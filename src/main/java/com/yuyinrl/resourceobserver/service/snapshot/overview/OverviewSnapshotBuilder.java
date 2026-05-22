package com.yuyinrl.resourceobserver.service.snapshot.overview;

import appeng.api.networking.IGrid;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.world.block.entity.Ae2CellCapacityMetrics;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Overview 主装配器 —— 把 {@link ObserverDataPayload} 折叠成一个 {@link OverviewSnapshot}。
 *
 * <p>F1.A.6 引入。所有"派生数据"（KPI 状态、存储健康、表格分组、关注列表）
 * 在此处一次性计算完毕；三类消费者（ModernUI / 新 Vanilla V2 / Web）皆从该 snapshot 出发。
 *
 * <p>本类目前依赖 {@code ObserverDataPayload}（其类签名引入了 MC 类型 BlockPos /
 * FriendlyByteBuf），因此无法纳入纯单测；但内部所有派生逻辑已在 F1.A.2 ~ A.5 单独抽出
 * （{@link SnapshotFormatters} / {@link KpiCalculator} / {@link StorageStatusEvaluator} /
 * {@link TableOps}）并 100% 单测覆盖。本类只负责按 mapper 现有顺序串联它们。
 *
 * <p>本地化策略由 {@link OverviewLocalizer} 注入：
 * 客户端走 {@code BuiltInRegistries + Component.translatable} 完整本地化；
 * 服务端 / 测试可使用 {@link OverviewLocalizer#IDENTITY} 退回到原始文本。
 *
 * @see OverviewSnapshot
 * @see OverviewLocalizer
 */
public final class OverviewSnapshotBuilder {

    /** 表格筛选/排序时不参与文字搜索（由消费方在 ViewModel 层独立处理）。 */
    private OverviewSnapshotBuilder() {
    }

    /** 入口：把 payload 折叠成 OverviewSnapshot。 */
    public static OverviewSnapshot fromPayload(ObserverDataPayload payload, OverviewLocalizer localizer) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (localizer == null) {
            localizer = OverviewLocalizer.IDENTITY;
        }

        LinkedHashSet<String> watchlistSet = new LinkedHashSet<>(payload.watchlistItemIds());

        // ===== 1. 聚合 KPI 速率 + 存储容量 =====
        WindowStatsAccum windowStats = WindowStatsAccum.empty();
        CellCapacityAccum cellCapacity = CellCapacityAccum.empty();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if (!"FLUX_ENERGY".equals(binding.networkType())) {
                windowStats = windowStats.merge(binding.kpiWindowStats());
            }
            cellCapacity = cellCapacity.merge(binding);
        }

        // ===== 2. 行数据：按 itemId 跨绑定合并 =====
        List<OverviewSnapshot.TableRowSnapshot> allRows = mergeTableRows(payload, watchlistSet, localizer);
        if (allRows.isEmpty()) {
            allRows = buildFallbackRows(payload, watchlistSet);
        }

        // ===== 3. KPI 卡片 + 详细分解 =====
        StorageOutcome storage = buildStorage(cellCapacity, localizer);
        KpiOutcome kpiOutcome = buildKpiOutcome(windowStats, storage.kpi());

        // ===== 4. 图表 =====
        List<OverviewSnapshot.ChartPointSnapshot> chartSeries = mapChartPoints(payload.chartSeries());
        List<OverviewSnapshot.ChartPointSnapshot> energyChartSeries = mapChartPoints(payload.energyChartSeries());

        // ===== 5. 分组 + UI 状态 =====
        List<OverviewSnapshot.GroupOptionSnapshot> groupOptions = buildGroupOptions(payload.groups(), localizer);
        String normalizedFilter = TableOps.normalizeGroupKey(
                payload.tableGroupFilterKey(), TableOps.GROUP_FILTER_ALL);
        OverviewSnapshot.UiStateSnapshot uiState = new OverviewSnapshot.UiStateSnapshot(
                normalizedFilter,
                groupOptions,
                payload.tableSortMode(),
                payload.tableSortDesc(),
                payload.tableStatusFilter(),
                payload.watchlistLimit()
        );

        // ===== 6. 关注列表（基于全部行，不受过滤影响） =====
        List<OverviewSnapshot.WatchlistItemSnapshot> watchlist =
                TableOps.buildWatchlist(allRows, payload.watchlistItemIds());

        // ===== 7. 过滤 + 排序 + 分组（不应用文字搜索；UI 层按需注入） =====
        List<OverviewSnapshot.TableRowSnapshot> filtered = TableOps.applyFiltersAndSort(
                allRows,
                uiState.statusFilter(),
                uiState.groupFilterKey(),
                uiState.sortMode(),
                uiState.sortDesc(),
                null
        );
        List<OverviewSnapshot.TableGroupSnapshot> tableGroups = TableOps.groupRows(
                filtered, uiState.groupFilterKey(), groupOptions);

        // ===== 8. 头部信息 =====
        boolean hasAe2 = false;
        boolean hasFlux = false;
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if ("AE2_ITEMS".equals(binding.networkType())) {
                hasAe2 = true;
            } else if ("FLUX_ENERGY".equals(binding.networkType())) {
                hasFlux = true;
            }
        }
        OverviewSnapshot.HeaderInfo header = new OverviewSnapshot.HeaderInfo(
                "screen.resourceobserver.overview.header.title",
                "screen.resourceobserver.overview.header.subtitle",
                "screen.resourceobserver.overview.link_status",
                payload.bindings().size(),
                hasAe2,
                hasFlux
        );

        return new OverviewSnapshot(
                header,
                kpiOutcome.kpis(),
                kpiOutcome.details(),
                storage.detail(),
                payload.chartWindow(),
                payload.chartScope(),
                payload.chartScopeItemId(),
                chartSeries,
                energyChartSeries,
                uiState,
                watchlist,
                tableGroups
        );
    }

    /** Web / 服务端入口：直接从 Observer 当前状态构造 OverviewSnapshot。 */
    public static OverviewSnapshot fromObserver(ObserverBlockEntity observer, OverviewLocalizer localizer) {
        return fromObserver(observer, localizer, PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot.defaults());
    }

    /** Web / 服务端入口（可注入 UI 偏好）。 */
    public static OverviewSnapshot fromObserver(
            ObserverBlockEntity observer,
            OverviewLocalizer localizer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot prefs = uiPrefs == null
                ? PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot.defaults()
                : uiPrefs;
        return fromPayload(buildPayloadFromObserver(observer, prefs), localizer);
    }

    // ===== 表格行聚合 =====

    private static List<OverviewSnapshot.TableRowSnapshot> mergeTableRows(
            ObserverDataPayload payload,
            LinkedHashSet<String> watchlistSet,
            OverviewLocalizer localizer
    ) {
        Map<String, OverviewSnapshot.TableRowSnapshot> merged = new LinkedHashMap<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                double production = item.productionRate();
                double consumption = item.consumptionRate();
                String localizedName = localizer.localizeEntryName(
                        item.entryType(), item.itemId(), item.displayName());
                String groupKey = TableOps.normalizeGroupKey(item.groupKey(), TableOps.GROUP_UNGROUPED);
                boolean starred = watchlistSet.contains(item.itemId());

                OverviewSnapshot.TableRowSnapshot existing = merged.get(item.itemId());
                if (existing != null) {
                    long mergedProd = existing.production() + Math.round(production);
                    long mergedCons = existing.consumption() + Math.round(consumption);
                    long mergedNet = mergedProd - mergedCons;
                    long mergedStock = saturatingAdd(existing.stock(), item.amount());
                    merged.put(item.itemId(), new OverviewSnapshot.TableRowSnapshot(
                            existing.itemId(),
                            existing.displayName(),
                            existing.groupKey(),
                            mergedProd,
                            mergedCons,
                            mergedNet,
                            mergedStock,
                            TableOps.isCritical(mergedNet, mergedStock),
                            starred,
                            existing.iconSprite()
                    ));
                } else {
                    long net = Math.round(production - consumption);
                    merged.put(item.itemId(), new OverviewSnapshot.TableRowSnapshot(
                            item.itemId(),
                            localizedName,
                            groupKey,
                            Math.round(production),
                            Math.round(consumption),
                            net,
                            item.amount(),
                            TableOps.isCritical(net, item.amount()),
                            starred,
                            item.iconSprite()
                    ));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static List<OverviewSnapshot.TableRowSnapshot> buildFallbackRows(
            ObserverDataPayload payload,
            LinkedHashSet<String> watchlistSet
    ) {
        List<OverviewSnapshot.TableRowSnapshot> rows = new ArrayList<>();
        int idx = 0;
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            String fallbackId = binding.networkType().toLowerCase(Locale.ROOT) + "_" + idx++;
            long stock = binding.currentValue();
            long net = binding.totalProduced() - binding.totalConsumed();
            rows.add(new OverviewSnapshot.TableRowSnapshot(
                    fallbackId,
                    binding.displayName(),
                    TableOps.GROUP_UNGROUPED,
                    binding.totalProduced(),
                    binding.totalConsumed(),
                    net,
                    stock,
                    TableOps.isCritical(net, stock),
                    watchlistSet.contains(fallbackId),
                    binding.iconSprite()
            ));
        }
        return rows;
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0L) {
            return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return sum;
    }

    // ===== 图表 =====

    private static List<OverviewSnapshot.ChartPointSnapshot> mapChartPoints(
            List<ObserverDataPayload.ChartPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<OverviewSnapshot.ChartPointSnapshot> result = new ArrayList<>(points.size());
        for (ObserverDataPayload.ChartPoint p : points) {
            result.add(new OverviewSnapshot.ChartPointSnapshot(
                    p.slotIndex(), p.bucket(), p.production(), p.consumption(),
                    p.net(), p.stock(), p.hasFlow(), p.hasStock(), p.sampleCount()
            ));
        }
        return result;
    }

    // ===== 分组 =====

    private static List<OverviewSnapshot.GroupOptionSnapshot> buildGroupOptions(
            List<ObserverDataPayload.GroupEntry> source,
            OverviewLocalizer localizer
    ) {
        String ungroupedLabel = localizer.localizeGroupLabel(
                TableOps.GROUP_UNGROUPED, TableOps.GROUP_UNGROUPED);
        LinkedHashMap<String, OverviewSnapshot.GroupOptionSnapshot> groups = new LinkedHashMap<>();
        groups.put(TableOps.GROUP_UNGROUPED,
                new OverviewSnapshot.GroupOptionSnapshot(TableOps.GROUP_UNGROUPED, ungroupedLabel, true));
        for (ObserverDataPayload.GroupEntry group : source) {
            String key = TableOps.normalizeGroupKey(group.key(), TableOps.GROUP_UNGROUPED);
            if (key.isBlank() || TableOps.GROUP_FILTER_ALL.equals(key)) {
                continue;
            }
            if (TableOps.GROUP_UNGROUPED.equals(key)) {
                groups.put(key, new OverviewSnapshot.GroupOptionSnapshot(key, ungroupedLabel, true));
            } else {
                String displayName = group.displayName() == null || group.displayName().isBlank()
                        ? key : group.displayName();
                groups.put(key, new OverviewSnapshot.GroupOptionSnapshot(key, displayName, group.systemGroup()));
            }
        }
        return new ArrayList<>(groups.values());
    }

    // ===== KPI 卡 + 详细 =====

    private static KpiOutcome buildKpiOutcome(WindowStatsAccum stats, OverviewSnapshot.KpiSnapshot storageKpi) {
        boolean recent = stats.recentAvailable;
        boolean previous = stats.previousAvailable;
        boolean baseTrend = stats.trendAvailable && recent && previous;

        boolean itemProdTrend = baseTrend && stats.itemProducedPreviousRate > 0.0d;
        boolean itemConsTrend = baseTrend && stats.itemConsumedPreviousRate > 0.0d;
        boolean fluidProdTrend = baseTrend && stats.fluidProducedPreviousRate > 0.0d;
        boolean fluidConsTrend = baseTrend && stats.fluidConsumedPreviousRate > 0.0d;

        double itemProdPct = itemProdTrend
                ? KpiCalculator.percentChange(stats.itemProducedRecentRate, stats.itemProducedPreviousRate) : 0.0d;
        double itemConsPct = itemConsTrend
                ? KpiCalculator.percentChange(stats.itemConsumedRecentRate, stats.itemConsumedPreviousRate) : 0.0d;
        double fluidProdPct = fluidProdTrend
                ? KpiCalculator.percentChange(stats.fluidProducedRecentRate, stats.fluidProducedPreviousRate) : 0.0d;
        double fluidConsPct = fluidConsTrend
                ? KpiCalculator.percentChange(stats.fluidConsumedRecentRate, stats.fluidConsumedPreviousRate) : 0.0d;

        double itemBalRecent = recent
                ? KpiCalculator.balanceScore(stats.itemProducedRecentRate, stats.itemConsumedRecentRate) : 0.0d;
        double itemBalPrev = previous
                ? KpiCalculator.balanceScore(stats.itemProducedPreviousRate, stats.itemConsumedPreviousRate) : 0.0d;
        double itemBalDelta = baseTrend ? (itemBalRecent - itemBalPrev) : 0.0d;

        double fluidBalRecent = recent
                ? KpiCalculator.balanceScore(stats.fluidProducedRecentRate, stats.fluidConsumedRecentRate) : 0.0d;
        double fluidBalPrev = previous
                ? KpiCalculator.balanceScore(stats.fluidProducedPreviousRate, stats.fluidConsumedPreviousRate) : 0.0d;
        double fluidBalDelta = baseTrend ? (fluidBalRecent - fluidBalPrev) : 0.0d;

        OverviewSnapshot.KpiStatus prodStatus = KpiCalculator.classifyProduction(itemProdTrend, itemProdPct);
        OverviewSnapshot.KpiStatus consStatus = KpiCalculator.classifyConsumption(itemConsTrend, itemConsPct);
        OverviewSnapshot.KpiStatus balStatus = KpiCalculator.classifyBalance(itemBalRecent, recent);

        // KPI 卡片
        List<OverviewSnapshot.KpiSnapshot> kpis = new ArrayList<>(4);
        kpis.add(new OverviewSnapshot.KpiSnapshot(
                OverviewSnapshot.KpiType.PRODUCTION,
                "screen.resourceobserver.overview.section.kpi_production",
                stats.itemProducedRecentRate,
                OverviewSnapshot.ValueKind.FLOW_PER_MINUTE,
                itemProdTrend ? "screen.resourceobserver.overview.kpi.trend.vs_previous"
                              : "screen.resourceobserver.overview.kpi.trend.unavailable",
                itemProdTrend ? SnapshotFormatters.formatSignedPercent(itemProdPct) : null,
                prodStatus,
                "resourceobserver:terminal/kpi_production"
        ));
        kpis.add(new OverviewSnapshot.KpiSnapshot(
                OverviewSnapshot.KpiType.CONSUMPTION,
                "screen.resourceobserver.overview.section.kpi_consumption",
                stats.itemConsumedRecentRate,
                OverviewSnapshot.ValueKind.FLOW_PER_MINUTE,
                itemConsTrend ? "screen.resourceobserver.overview.kpi.trend.vs_previous"
                              : "screen.resourceobserver.overview.kpi.trend.unavailable",
                itemConsTrend ? SnapshotFormatters.formatSignedPercent(itemConsPct) : null,
                consStatus,
                "resourceobserver:terminal/kpi_consumption"
        ));
        kpis.add(storageKpi);
        kpis.add(new OverviewSnapshot.KpiSnapshot(
                OverviewSnapshot.KpiType.BALANCE,
                "screen.resourceobserver.overview.section.kpi_balance",
                itemBalRecent,
                OverviewSnapshot.ValueKind.BALANCE_SCORE,
                baseTrend ? "screen.resourceobserver.overview.kpi.trend.delta_points"
                          : "screen.resourceobserver.overview.kpi.trend.unavailable",
                baseTrend ? SnapshotFormatters.formatSignedPoints(itemBalDelta) : null,
                balStatus,
                "resourceobserver:terminal/kpi_efficiency"
        ));

        // 详细分解
        List<OverviewSnapshot.KpiDetailSnapshot> details = new ArrayList<>(3);
        details.add(new OverviewSnapshot.KpiDetailSnapshot(
                OverviewSnapshot.KpiType.PRODUCTION,
                "screen.resourceobserver.overview.section.kpi_production",
                itemProdTrend ? "screen.resourceobserver.overview.kpi.trend.vs_previous"
                              : "screen.resourceobserver.overview.kpi.trend.unavailable",
                prodStatus,
                channel("screen.resourceobserver.overview.kpi.detail.channel.item",
                        stats.itemProducedRecentRate, stats.itemProducedPreviousRate, itemProdPct, itemProdTrend, recent),
                channel("screen.resourceobserver.overview.kpi.detail.channel.fluid",
                        stats.fluidProducedRecentRate, stats.fluidProducedPreviousRate, fluidProdPct, fluidProdTrend, recent)
        ));
        details.add(new OverviewSnapshot.KpiDetailSnapshot(
                OverviewSnapshot.KpiType.CONSUMPTION,
                "screen.resourceobserver.overview.section.kpi_consumption",
                itemConsTrend ? "screen.resourceobserver.overview.kpi.trend.vs_previous"
                              : "screen.resourceobserver.overview.kpi.trend.unavailable",
                consStatus,
                channel("screen.resourceobserver.overview.kpi.detail.channel.item",
                        stats.itemConsumedRecentRate, stats.itemConsumedPreviousRate, itemConsPct, itemConsTrend, recent),
                channel("screen.resourceobserver.overview.kpi.detail.channel.fluid",
                        stats.fluidConsumedRecentRate, stats.fluidConsumedPreviousRate, fluidConsPct, fluidConsTrend, recent)
        ));
        details.add(new OverviewSnapshot.KpiDetailSnapshot(
                OverviewSnapshot.KpiType.BALANCE,
                "screen.resourceobserver.overview.section.kpi_balance",
                balanceHintKey(balStatus, recent),
                balStatus,
                channel("screen.resourceobserver.overview.kpi.detail.channel.item",
                        itemBalRecent, itemBalPrev, itemBalDelta, baseTrend, recent),
                channel("screen.resourceobserver.overview.kpi.detail.channel.fluid",
                        fluidBalRecent, fluidBalPrev, fluidBalDelta, baseTrend, recent)
        ));
        return new KpiOutcome(kpis, details);
    }

    private static OverviewSnapshot.ChannelDetail channel(
            String labelKey, double recent, double previous, double trendValue,
            boolean trendAvailable, boolean recentAvailable) {
        return new OverviewSnapshot.ChannelDetail(
                labelKey, recent, previous, trendValue, trendAvailable, recentAvailable);
    }

    private static String balanceHintKey(OverviewSnapshot.KpiStatus status, boolean available) {
        if (!available) {
            return "screen.resourceobserver.overview.kpi.balance.hint.unavailable";
        }
        return switch (status) {
            case POSITIVE -> "screen.resourceobserver.overview.kpi.balance.hint.positive";
            case WARNING -> "screen.resourceobserver.overview.kpi.balance.hint.warning";
            case NEGATIVE -> "screen.resourceobserver.overview.kpi.balance.hint.negative";
            case NEUTRAL -> "screen.resourceobserver.overview.kpi.balance.hint.neutral";
        };
    }

    // ===== 存储 KPI + 详细 =====

    private static StorageOutcome buildStorage(CellCapacityAccum cap, OverviewLocalizer localizer) {
        if (!cap.hasAe2Binding) {
            String hintKey = "screen.resourceobserver.overview.kpi.storage.not_ae2";
            OverviewSnapshot.KpiSnapshot kpi = new OverviewSnapshot.KpiSnapshot(
                    OverviewSnapshot.KpiType.STORAGE,
                    "screen.resourceobserver.overview.section.kpi_storage",
                    Double.NaN,
                    OverviewSnapshot.ValueKind.PERCENT,
                    hintKey,
                    null,
                    OverviewSnapshot.KpiStatus.NEUTRAL,
                    "resourceobserver:terminal/kpi_storage"
            );
            return new StorageOutcome(kpi, OverviewSnapshot.StorageDetailSnapshot.unavailable(hintKey));
        }

        long itemUsedBytes = cap.itemUsedBytes;
        long itemTotalBytes = StorageStatusEvaluator.clampTotal(itemUsedBytes, cap.itemTotalBytes);
        long itemUsedTypes = cap.itemUsedTypes;
        long itemTotalTypes = StorageStatusEvaluator.clampTotal(itemUsedTypes, cap.itemTotalTypes);
        long fluidUsedBytes = cap.fluidUsedBytes;
        long fluidTotalBytes = StorageStatusEvaluator.clampTotal(fluidUsedBytes, cap.fluidTotalBytes);
        long fluidUsedTypes = cap.fluidUsedTypes;
        long fluidTotalTypes = StorageStatusEvaluator.clampTotal(fluidUsedTypes, cap.fluidTotalTypes);

        StorageStatusEvaluator.Result eval = StorageStatusEvaluator.evaluate(
                new StorageStatusEvaluator.Inputs(
                        cap.hasAe2Binding,
                        cap.diskAvailable, cap.diskReliable,
                        cap.externalAvailable, cap.externalReliable,
                        itemUsedBytes, itemTotalBytes, itemUsedTypes, itemTotalTypes,
                        fluidUsedBytes, fluidTotalBytes, fluidUsedTypes, fluidTotalTypes
                ));
        String hintKey = mapReasonToHintKey(eval.reason());

        long externalItemUsed = cap.externalItemUsedUnits;
        long externalItemTotal = StorageStatusEvaluator.clampTotal(externalItemUsed, cap.externalItemTotalUnits);
        long externalFluidUsed = cap.externalFluidUsedUnits;
        long externalFluidTotal = StorageStatusEvaluator.clampTotal(externalFluidUsed, cap.externalFluidTotalUnits);

        OverviewSnapshot.StorageDetailSnapshot detail = new OverviewSnapshot.StorageDetailSnapshot(
                true,
                cap.diskReliable,
                cap.externalReliable,
                hintKey,
                new OverviewSnapshot.StorageChannelSnapshot(
                        "screen.resourceobserver.overview.storage.detail.disk_item",
                        itemUsedBytes, itemTotalBytes, itemUsedTypes, itemTotalTypes,
                        cap.diskAvailable),
                new OverviewSnapshot.StorageChannelSnapshot(
                        "screen.resourceobserver.overview.storage.detail.disk_fluid",
                        fluidUsedBytes, fluidTotalBytes, fluidUsedTypes, fluidTotalTypes,
                        cap.diskAvailable),
                new OverviewSnapshot.StorageChannelSnapshot(
                        "screen.resourceobserver.overview.storage.detail.external_item",
                        externalItemUsed, externalItemTotal, 0L, 0L,
                        cap.externalAvailable),
                new OverviewSnapshot.StorageChannelSnapshot(
                        "screen.resourceobserver.overview.storage.detail.external_fluid",
                        externalFluidUsed, externalFluidTotal, 0L, 0L,
                        cap.externalAvailable)
        );

        // KPI 数值 = 较高侧填充率（百分比）
        double itemFill = StorageStatusEvaluator.bytesFillRatio(itemUsedBytes, itemTotalBytes);
        double fluidFill = StorageStatusEvaluator.bytesFillRatio(fluidUsedBytes, fluidTotalBytes);
        double fillPercent = Math.max(itemFill, fluidFill) * 100.0d;

        OverviewSnapshot.KpiSnapshot kpi = new OverviewSnapshot.KpiSnapshot(
                OverviewSnapshot.KpiType.STORAGE,
                "screen.resourceobserver.overview.section.kpi_storage",
                fillPercent,
                OverviewSnapshot.ValueKind.PERCENT,
                hintKey,
                null,
                eval.status(),
                "resourceobserver:terminal/kpi_storage"
        );
        return new StorageOutcome(kpi, detail);
    }

    private static String mapReasonToHintKey(StorageStatusEvaluator.Reason reason) {
        return switch (reason) {
            case NO_AE2 -> "screen.resourceobserver.overview.kpi.storage.not_ae2";
            case CELLS_UNAVAILABLE -> "screen.resourceobserver.overview.kpi.storage.cells_unavailable";
            case UNRELIABLE -> "screen.resourceobserver.overview.kpi.storage.unreliable";
            case TYPES_FULL -> "screen.resourceobserver.overview.kpi.storage.types_full";
            case BYTES_HIGH -> "screen.resourceobserver.overview.kpi.storage.bytes_high";
            case NORMAL -> "screen.resourceobserver.overview.kpi.storage.normal";
        };
    }

    // ===== Observer → Payload 适配 =====

    private static ObserverDataPayload buildPayloadFromObserver(
            ObserverBlockEntity observer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        List<BoundEntry> effectiveBindings = deduplicateBindings(observer);
        List<ObserverDataPayload.BindingEntry> entries = new ArrayList<>();
        for (BoundEntry binding : effectiveBindings) {
            BindingStats stats = observer.getStatsFor(binding.networkId());
            entries.add(new ObserverDataPayload.BindingEntry(
                    binding.networkType(),
                    binding.networkId(),
                    binding.targetBlockId(),
                    resolveBindingDisplayName(binding, uiPrefs),
                    bindingIcon(binding),
                    stats.currentValue(),
                    stats.capacity(),
                    toCellCapacityMetrics(observer, binding),
                    stats.totalProduced(),
                    stats.totalConsumed(),
                    observer.getKpiStats(binding.networkId()),
                    buildItemDeltas(binding, observer, uiPrefs),
                    observer.getDebugInfoFor(binding.networkId())
            ));
        }

        ChartSeriesResult chartResult = buildChartSeries(observer, effectiveBindings);
        return new ObserverDataPayload(
                observer.getBlockPos(),
                observer.isBound(),
                false,
                ChartWindow.WEB_DETAIL_BUFFER_5M_1S,
                ChartScope.GLOBAL,
                "",
                chartResult.itemSeries(),
                chartResult.energySeries(),
                uiPrefs.groupFilterKey(),
                uiPrefs.sortMode(),
                uiPrefs.sortDesc(),
                uiPrefs.statusFilter(),
                PlayerUiPrefsSavedData.WATCHLIST_LIMIT,
                uiPrefs.watchlistItemIds(),
                buildGroupEntries(uiPrefs),
                entries,
                List.of()
        );
    }

    private static ChartSeriesResult buildChartSeries(
            ObserverBlockEntity observer,
            List<BoundEntry> effectiveBindings
    ) {
        if (!(observer.getLevel() instanceof ServerLevel level)) {
            return new ChartSeriesResult(List.of(), List.of());
        }
        BlockPos observerPos = observer.getBlockPos();
        List<BoundEntry> itemBindings = new ArrayList<>();
        List<BoundEntry> energyBindings = new ArrayList<>();
        for (BoundEntry binding : effectiveBindings) {
            if ("FLUX_ENERGY".equals(binding.networkType())) {
                energyBindings.add(binding);
            } else {
                itemBindings.add(binding);
            }
        }
        return new ChartSeriesResult(
                HistoryRecorder.querySeries(
                        level,
                        observerPos,
                        itemBindings,
                        ChartWindow.WEB_DETAIL_BUFFER_5M_1S,
                        ChartScope.GLOBAL,
                        null),
                HistoryRecorder.querySeries(
                        level,
                        observerPos,
                        energyBindings,
                        ChartWindow.WEB_DETAIL_BUFFER_5M_1S,
                        ChartScope.GLOBAL,
                        null)
        );
    }

    private static List<ObserverDataPayload.GroupEntry> buildGroupEntries(
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        List<ObserverDataPayload.GroupEntry> groups = new ArrayList<>();
        for (PlayerUiPrefsSavedData.GroupDefinition group : uiPrefs.groups()) {
            groups.add(new ObserverDataPayload.GroupEntry(group.key(), group.displayName(), group.systemGroup()));
        }
        return groups;
    }

    private static List<ObserverDataPayload.ItemDeltaEntry> buildItemDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return buildAe2ItemDeltas(binding, observer, uiPrefs);
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return buildFluxEnergyDeltas(binding, observer);
        }
        return List.of();
    }

    private static List<ObserverDataPayload.ItemDeltaEntry> buildAe2ItemDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = new ArrayList<>();
        Map<String, Long> amounts = observer.getAe2ItemAmountsFor(binding.networkId());
        Map<String, Double> rates = observer.getAe2ItemRatesPerMinFor(binding.networkId());
        Map<String, Double> prodRates = observer.getAe2ItemProdRatesPerMinFor(binding.networkId());
        Map<String, Double> consRates = observer.getAe2ItemConsRatesPerMinFor(binding.networkId());
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            String itemId = entry.getKey();
            ObserverDataPayload.EntryType entryType = entryTypeForId(itemId);
            itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                    entryType,
                    itemId,
                    toDisplayName(itemId, entryType),
                    uiPrefs.groupKeyForItem(itemId),
                    iconSpriteForEntryType(entryType),
                    entry.getValue(),
                    rates.getOrDefault(itemId, 0.0d),
                    prodRates.getOrDefault(itemId, 0.0d),
                    consRates.getOrDefault(itemId, 0.0d)
            ));
        }
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String itemId = entry.getKey();
            if (amounts.containsKey(itemId)) {
                continue;
            }
            ObserverDataPayload.EntryType entryType = entryTypeForId(itemId);
            itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                    entryType,
                    itemId,
                    toDisplayName(itemId, entryType),
                    uiPrefs.groupKeyForItem(itemId),
                    iconSpriteForEntryType(entryType),
                    0L,
                    entry.getValue(),
                    prodRates.getOrDefault(itemId, 0.0d),
                    consRates.getOrDefault(itemId, 0.0d)
            ));
        }
        itemDeltas.sort(Comparator.comparing(ObserverDataPayload.ItemDeltaEntry::entryType)
                .thenComparing(ObserverDataPayload.ItemDeltaEntry::itemId));
        return itemDeltas;
    }

    private static List<ObserverDataPayload.ItemDeltaEntry> buildFluxEnergyDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer
    ) {
        FluxNetworksIntegration.FluxSampleResult result = observer.getFluxSampleResultFor(binding.networkId());
        if (result == null) {
            return List.of();
        }
        return List.of(
                new ObserverDataPayload.ItemDeltaEntry(
                        ObserverDataPayload.EntryType.ITEM,
                        "flux.input_per_tick",
                        "Input/t",
                        "flux_metrics",
                        "resourceobserver:terminal/kpi_production",
                        result.energyInput(),
                        0.0d,
                        0.0d,
                        0.0d
                ),
                new ObserverDataPayload.ItemDeltaEntry(
                        ObserverDataPayload.EntryType.ITEM,
                        "flux.output_per_tick",
                        "Output/t",
                        "flux_metrics",
                        "resourceobserver:terminal/kpi_consumption",
                        result.energyOutput(),
                        0.0d,
                        0.0d,
                        0.0d
                ),
                new ObserverDataPayload.ItemDeltaEntry(
                        ObserverDataPayload.EntryType.ITEM,
                        "flux.energy_stored",
                        "Energy Stored",
                        "flux_metrics",
                        "resourceobserver:terminal/kpi_storage",
                        result.totalEnergy(),
                        0.0d,
                        0.0d,
                        0.0d
                )
        );
    }

    private static List<BoundEntry> deduplicateBindings(ObserverBlockEntity observer) {
        List<BoundEntry> source = observer.getBindings();
        if (source.size() <= 1) return source;
        Set<IGrid> seenAe2Grids = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BoundEntry> deduped = new ArrayList<>(source.size());
        for (BoundEntry binding : source) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                deduped.add(binding);
                continue;
            }
            IGrid grid = observer.resolveAe2GridForNetwork(binding.networkId());
            if (grid == null || seenAe2Grids.add(grid)) {
                deduped.add(binding);
            }
        }
        return deduped;
    }

    private static ObserverDataPayload.CellCapacityMetrics toCellCapacityMetrics(
            ObserverBlockEntity observer,
            BoundEntry binding
    ) {
        if (!"AE2_ITEMS".equals(binding.networkType())) {
            return ObserverDataPayload.CellCapacityMetrics.unavailable();
        }
        Ae2CellCapacityMetrics metrics = observer.getAe2CellCapacityMetricsFor(binding.networkId());
        return new ObserverDataPayload.CellCapacityMetrics(
                metrics.itemUsedBytes(),
                metrics.itemTotalBytes(),
                metrics.itemUsedTypes(),
                metrics.itemTotalTypes(),
                metrics.itemUsedUnits(),
                metrics.itemMaxUnits(),
                metrics.fluidUsedBytes(),
                metrics.fluidTotalBytes(),
                metrics.fluidUsedTypes(),
                metrics.fluidTotalTypes(),
                metrics.fluidUsedUnits(),
                metrics.fluidMaxUnits(),
                metrics.externalItemUsedUnits(),
                metrics.externalItemTotalUnits(),
                metrics.externalFluidUsedUnits(),
                metrics.externalFluidTotalUnits(),
                metrics.scope(),
                metrics.reliable(),
                metrics.available(),
                metrics.externalReliable(),
                metrics.externalAvailable()
        );
    }

    private static String resolveBindingDisplayName(
            BoundEntry binding,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        String custom = uiPrefs.customNameForNetwork(binding.networkId());
        if (custom != null && !custom.isBlank()) return custom;
        if ("AE2_ITEMS".equals(binding.networkType())) return "Storage Network";
        if ("FLUX_ENERGY".equals(binding.networkType())) return "Power Network";
        return "Linked Network";
    }

    private static String bindingIcon(BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) return "resourceobserver:terminal/kpi_storage";
        if ("FLUX_ENERGY".equals(binding.networkType())) return "resourceobserver:terminal/kpi_consumption";
        return "resourceobserver:terminal/kpi_efficiency";
    }

    private static ObserverDataPayload.EntryType entryTypeForId(String itemId) {
        if (itemId != null && itemId.startsWith("fluid:")) {
            return ObserverDataPayload.EntryType.FLUID;
        }
        return ObserverDataPayload.EntryType.ITEM;
    }

    private static String iconSpriteForEntryType(ObserverDataPayload.EntryType entryType) {
        if (entryType == ObserverDataPayload.EntryType.FLUID) {
            return "resourceobserver:terminal/watch_item";
        }
        return "resourceobserver:terminal/table_item";
    }

    private static String toDisplayName(String itemId, ObserverDataPayload.EntryType entryType) {
        String id = itemId == null ? "" : itemId;
        if (entryType == ObserverDataPayload.EntryType.FLUID && id.startsWith("fluid:")) {
            id = id.substring("fluid:".length());
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        if (path.isBlank()) return itemId == null ? "" : itemId;
        String[] parts = path.split("[_\\-/]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    // ===== 内部聚合工具 =====

    private record ChartSeriesResult(
            List<ObserverDataPayload.ChartPoint> itemSeries,
            List<ObserverDataPayload.ChartPoint> energySeries
    ) {
    }


    private record StorageOutcome(
            OverviewSnapshot.KpiSnapshot kpi,
            OverviewSnapshot.StorageDetailSnapshot detail
    ) {
    }

    private record KpiOutcome(
            List<OverviewSnapshot.KpiSnapshot> kpis,
            List<OverviewSnapshot.KpiDetailSnapshot> details
    ) {
    }

    /** KPI 速率累加器（与原 mapper 中的 WindowStatsAggregation 逻辑一致）。 */
    private static final class WindowStatsAccum {
        double itemProducedRecentRate;
        double itemConsumedRecentRate;
        double itemProducedPreviousRate;
        double itemConsumedPreviousRate;
        double fluidProducedRecentRate;
        double fluidConsumedRecentRate;
        double fluidProducedPreviousRate;
        double fluidConsumedPreviousRate;
        boolean recentAvailable;
        boolean previousAvailable;
        boolean trendAvailable;

        static WindowStatsAccum empty() {
            return new WindowStatsAccum();
        }

        WindowStatsAccum merge(ObserverDataPayload.KpiWindowStats stats) {
            if (stats == null) {
                return this;
            }
            this.itemProducedRecentRate += stats.itemProducedRecentRate();
            this.itemConsumedRecentRate += stats.itemConsumedRecentRate();
            this.itemProducedPreviousRate += stats.itemProducedPreviousRate();
            this.itemConsumedPreviousRate += stats.itemConsumedPreviousRate();
            this.fluidProducedRecentRate += stats.fluidProducedRecentRate();
            this.fluidConsumedRecentRate += stats.fluidConsumedRecentRate();
            this.fluidProducedPreviousRate += stats.fluidProducedPreviousRate();
            this.fluidConsumedPreviousRate += stats.fluidConsumedPreviousRate();
            this.recentAvailable = this.recentAvailable || stats.recentAvailable();
            this.previousAvailable = this.previousAvailable || stats.previousAvailable();
            this.trendAvailable = this.trendAvailable || stats.trendAvailable();
            return this;
        }
    }

    /** AE2 单元 + 外部存储容量累加器（对应原 mapper 中的 CellCapacityAggregation）。 */
    private static final class CellCapacityAccum {
        long itemUsedBytes;
        long itemTotalBytes;
        long itemUsedTypes;
        long itemTotalTypes;
        long fluidUsedBytes;
        long fluidTotalBytes;
        long fluidUsedTypes;
        long fluidTotalTypes;
        long externalItemUsedUnits;
        long externalItemTotalUnits;
        long externalFluidUsedUnits;
        long externalFluidTotalUnits;
        boolean hasAe2Binding;
        boolean diskAvailable;
        boolean diskReliable = true;
        boolean externalAvailable;
        boolean externalReliable = true;

        static CellCapacityAccum empty() {
            return new CellCapacityAccum();
        }

        CellCapacityAccum merge(ObserverDataPayload.BindingEntry binding) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                return this;
            }
            this.hasAe2Binding = true;
            ObserverDataPayload.CellCapacityMetrics m = binding.cellCapacityMetrics();
            if (m == null) {
                return this;
            }
            if (m.available()) {
                this.diskAvailable = true;
                this.itemUsedBytes += m.itemUsedBytes();
                this.itemTotalBytes += m.itemTotalBytes();
                this.itemUsedTypes += m.itemUsedTypes();
                this.itemTotalTypes += m.itemTotalTypes();
                this.fluidUsedBytes += m.fluidUsedBytes();
                this.fluidTotalBytes += m.fluidTotalBytes();
                this.fluidUsedTypes += m.fluidUsedTypes();
                this.fluidTotalTypes += m.fluidTotalTypes();
                this.diskReliable = this.diskReliable && m.reliable();
            }
            if (m.externalAvailable()) {
                this.externalAvailable = true;
                this.externalItemUsedUnits += m.externalItemUsedUnits();
                this.externalItemTotalUnits += m.externalItemTotalUnits();
                this.externalFluidUsedUnits += m.externalFluidUsedUnits();
                this.externalFluidTotalUnits += m.externalFluidTotalUnits();
                this.externalReliable = this.externalReliable && m.externalReliable();
            }
            return this;
        }
    }
}
