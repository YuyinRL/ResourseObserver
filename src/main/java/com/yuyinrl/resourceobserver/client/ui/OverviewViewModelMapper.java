package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.client.util.PinyinMatcher;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 总览视图模型映射器 —— 将服务端 ObserverDataPayload 转换为客户端可直接渲染的 OverviewViewModel。
 * <p>
 * 转换流程：
 * 1. 遍历所有绑定的网络，汇总 KPI 总量，构建表格行数据
 * 2. 计算 KPI 指标（生产量、消耗量、库存比例、效率）
 * 3. 转换图表数据点为 FlowPoint 列表
 * 4. 构建分组选项列表
 * 5. 应用状态筛选和分组筛选
 * 6. 按指定排序模式排序
 * 7. 将行数据按分组归类
 * 8. 构建关注列表物品数据
 */
public final class OverviewViewModelMapper {
    private OverviewViewModelMapper() {
    }

    /**
     * 将 Payload 数据转换为 OverviewViewModel。
     * 这是 Mapper 的唯一公共入口，完成完整的数据转换流程。
     *
     * @param searchQuery 搜索关键词（按 displayName 过滤），为空时不过滤
     */
    public static OverviewViewModel fromPayload(ObserverDataPayload payload, String searchQuery) {
        LinkedHashSet<String> watchlistSet = new LinkedHashSet<>(payload.watchlistItemIds());
        // 汇总所有绑定网络的 KPI 总量
        WindowStatsAggregation windowStatsAggregation = WindowStatsAggregation.empty();
        CellCapacityAggregation cellCapacityAggregation = CellCapacityAggregation.empty();

        // 遍历所有绑定，构建表格行数据（按 itemId 合并跨网络重复物品）
        Map<String, OverviewViewModel.TableRow> mergedRows = new LinkedHashMap<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            // 仅聚合物品/流体类绑定的 KPI 数据，跳过能量绑定（FLUX_ENERGY）
            // FLUX 的 RF/tick 数据不应计入物品生产/消耗速率，否则会将百万级能量误算为物品
            if (!"FLUX_ENERGY".equals(binding.networkType())) {
                windowStatsAggregation = windowStatsAggregation.merge(binding.kpiWindowStats());
            }
            cellCapacityAggregation = cellCapacityAggregation.merge(binding);

            // 为每种物品创建或合并表格行
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                double production = item.productionRate();   // 服务端提供的每分钟生产速率（≥0）
                double consumption = item.consumptionRate(); // 服务端提供的每分钟消耗速率（≥0）
                String localizedName = localizeEntryName(item.entryType(), item.itemId(), item.displayName());

                OverviewViewModel.TableRow existing = mergedRows.get(item.itemId());
                if (existing != null) {
                    // 同一物品出现在多个网络中，累加数量和速率
                    long mergedProd = existing.production() + Math.round(production);
                    long mergedCons = existing.consumption() + Math.round(consumption);
                    long mergedNet = mergedProd - mergedCons;
                    long mergedStock = saturatingAdd(existing.stock(), item.amount());
                    mergedRows.put(item.itemId(), new OverviewViewModel.TableRow(
                            existing.itemId(),
                            existing.displayName(),
                            existing.groupKey(),
                            mergedProd,
                            mergedCons,
                            mergedNet,
                            mergedStock,
                            isCritical(mergedNet, mergedStock),
                            watchlistSet.contains(item.itemId()),
                            existing.iconSprite()
                    ));
                } else {
                    long net = Math.round(production - consumption);
                    mergedRows.put(item.itemId(), new OverviewViewModel.TableRow(
                            item.itemId(),
                            localizedName,
                            normalizeGroupKey(item.groupKey()),
                            Math.round(production),
                            Math.round(consumption),
                            net,
                            item.amount(),
                            isCritical(net, item.amount()),
                            watchlistSet.contains(item.itemId()),
                            item.iconSprite()
                    ));
                }
            }
        }
        List<OverviewViewModel.TableRow> allRows = new ArrayList<>(mergedRows.values());

        // 无物品级数据时使用绑定级数据构建回退行
        if (allRows.isEmpty()) {
            allRows = buildFallbackRows(payload, watchlistSet);
        }

        // 构建四个 KPI 指标卡
        StoragePresentation storagePresentation = buildStoragePresentation(cellCapacityAggregation);
        KpiPresentation kpiPresentation = buildKpiPresentation(
                windowStatsAggregation,
                storagePresentation.storageKpi()
        );
        // 转换图表数据点
        List<OverviewViewModel.FlowPoint> chartSeries = new ArrayList<>(payload.chartSeries().size());
        for (ObserverDataPayload.ChartPoint point : payload.chartSeries()) {
            chartSeries.add(new OverviewViewModel.FlowPoint(
                    point.slotIndex(), point.production(), point.consumption(),
                    point.net(), point.stock(), point.hasFlow(), point.hasStock()
            ));
        }
        List<OverviewViewModel.FlowPoint> energyChartSeries = new ArrayList<>(payload.energyChartSeries().size());
        for (ObserverDataPayload.ChartPoint point : payload.energyChartSeries()) {
            energyChartSeries.add(new OverviewViewModel.FlowPoint(
                    point.slotIndex(), point.production(), point.consumption(),
                    point.net(), point.stock(), point.hasFlow(), point.hasStock()
            ));
        }

        // 构建分组选项和 UI 状态
        List<OverviewViewModel.GroupOption> groups = buildGroups(payload.groups());
        OverviewViewModel.UiState uiState = new OverviewViewModel.UiState(
                normalizeGroupFilterKey(payload.tableGroupFilterKey()),
                groups,
                payload.tableSortMode(),
                payload.tableSortDesc(),
                payload.tableStatusFilter(),
                payload.watchlistLimit()
        );

        // 构建关注列表
        List<OverviewViewModel.WatchlistItem> watchlist = buildWatchlist(allRows, payload.watchlistItemIds());
        // 应用筛选和排序
        List<OverviewViewModel.TableRow> filteredRows = applyFiltersAndSort(
                allRows, uiState.statusFilter(), uiState.groupFilterKey(),
                uiState.sortMode(), uiState.sortDesc(), searchQuery
        );
        // 按分组归类行数据
        List<OverviewViewModel.TableGroup> groupedRows = groupRows(filteredRows, uiState.groupFilterKey(), uiState.groups());

        return new OverviewViewModel(
                Component.translatable("screen.resourceobserver.overview.header.title").getString(),
                Component.translatable("screen.resourceobserver.overview.header.subtitle").getString(),
                Component.translatable("screen.resourceobserver.overview.link_status").getString(),
                kpiPresentation.kpis(),
                payload.chartWindow(),
                payload.chartScopeItemId(),
                chartSeries,
                energyChartSeries,
                uiState,
                watchlist,
                groupedRows,
                kpiPresentation.details(),
                storagePresentation.storageDetail()
        );
    }

    /**
     * 构建四个 KPI 指标卡。
     * - 生产量：总生产量/分钟
     * - 消耗量：总消耗量/分钟
     * - 库存：填充率百分比
     * - 效率：消耗/生产比率
     */
    private static KpiPresentation buildKpiPresentation(
            WindowStatsAggregation stats,
            OverviewViewModel.KpiMetric storageKpi
    ) {
        boolean recentAvailable = stats.recentAvailable();
        boolean previousAvailable = stats.previousAvailable();
        boolean baseTrendAvailable = stats.trendAvailable() && recentAvailable && previousAvailable;
        // 直接使用聚合后的速率（已在 merge 阶段按各绑定独立计算并累加）
        double itemProducedRecentRate = stats.itemProducedRecentRate();
        double itemProducedPreviousRate = stats.itemProducedPreviousRate();
        double itemConsumedRecentRate = stats.itemConsumedRecentRate();
        double itemConsumedPreviousRate = stats.itemConsumedPreviousRate();
        double fluidProducedRecentRate = stats.fluidProducedRecentRate();
        double fluidProducedPreviousRate = stats.fluidProducedPreviousRate();
        double fluidConsumedRecentRate = stats.fluidConsumedRecentRate();
        double fluidConsumedPreviousRate = stats.fluidConsumedPreviousRate();

        boolean itemProductionTrendAvailable = baseTrendAvailable && itemProducedPreviousRate > 0.0d;
        boolean itemConsumptionTrendAvailable = baseTrendAvailable && itemConsumedPreviousRate > 0.0d;
        boolean fluidProductionTrendAvailable = baseTrendAvailable && fluidProducedPreviousRate > 0.0d;
        boolean fluidConsumptionTrendAvailable = baseTrendAvailable && fluidConsumedPreviousRate > 0.0d;

        double itemProductionTrendPercent = itemProductionTrendAvailable
                ? percentChange(itemProducedRecentRate, itemProducedPreviousRate)
                : 0.0d;
        double itemConsumptionTrendPercent = itemConsumptionTrendAvailable
                ? percentChange(itemConsumedRecentRate, itemConsumedPreviousRate)
                : 0.0d;
        double fluidProductionTrendPercent = fluidProductionTrendAvailable
                ? percentChange(fluidProducedRecentRate, fluidProducedPreviousRate)
                : 0.0d;
        double fluidConsumptionTrendPercent = fluidConsumptionTrendAvailable
                ? percentChange(fluidConsumedRecentRate, fluidConsumedPreviousRate)
                : 0.0d;

        double itemBalanceRecent = recentAvailable ? balanceScore(itemProducedRecentRate, itemConsumedRecentRate) : 0.0d;
        double itemBalancePrevious = previousAvailable ? balanceScore(itemProducedPreviousRate, itemConsumedPreviousRate) : 0.0d;
        boolean itemBalanceTrendAvailable = baseTrendAvailable;
        double itemBalanceDelta = itemBalanceTrendAvailable ? (itemBalanceRecent - itemBalancePrevious) : 0.0d;

        double fluidBalanceRecent = recentAvailable ? balanceScore(fluidProducedRecentRate, fluidConsumedRecentRate) : 0.0d;
        double fluidBalancePrevious = previousAvailable ? balanceScore(fluidProducedPreviousRate, fluidConsumedPreviousRate) : 0.0d;
        boolean fluidBalanceTrendAvailable = baseTrendAvailable;
        double fluidBalanceDelta = fluidBalanceTrendAvailable ? (fluidBalanceRecent - fluidBalancePrevious) : 0.0d;

        OverviewViewModel.Status productionStatus = statusForProduction(itemProductionTrendAvailable, itemProductionTrendPercent);
        OverviewViewModel.Status consumptionStatus = statusForConsumption(itemConsumptionTrendAvailable, itemConsumptionTrendPercent);
        OverviewViewModel.Status balanceStatus = statusForBalance(itemBalanceRecent, recentAvailable);

        String productionTrendText = itemProductionTrendAvailable
                ? Component.translatable(
                "screen.resourceobserver.overview.kpi.trend.vs_previous",
                formatSignedPercent(itemProductionTrendPercent)
        ).getString()
                : trendUnavailableText();
        String consumptionTrendText = itemConsumptionTrendAvailable
                ? Component.translatable(
                "screen.resourceobserver.overview.kpi.trend.vs_previous",
                formatSignedPercent(itemConsumptionTrendPercent)
        ).getString()
                : trendUnavailableText();
        String balanceTrendText = itemBalanceTrendAvailable
                ? Component.translatable(
                "screen.resourceobserver.overview.kpi.trend.delta_points",
                formatSignedPoints(itemBalanceDelta)
        ).getString()
                : trendUnavailableText();

        List<OverviewViewModel.KpiMetric> kpis = new ArrayList<>(4);
        kpis.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.PRODUCTION,
                Component.translatable("screen.resourceobserver.overview.section.kpi_production").getString(),
                recentAvailable ? formatRateCompact(itemProducedRecentRate, "/min") : loadingText(),
                productionTrendText,
                productionStatus,
                "resourceobserver:terminal/kpi_production"
        ));
        kpis.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.CONSUMPTION,
                Component.translatable("screen.resourceobserver.overview.section.kpi_consumption").getString(),
                recentAvailable ? formatRateCompact(itemConsumedRecentRate, "/min") : loadingText(),
                consumptionTrendText,
                consumptionStatus,
                "resourceobserver:terminal/kpi_consumption"
        ));
        kpis.add(storageKpi);
        kpis.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.BALANCE,
                Component.translatable("screen.resourceobserver.overview.section.kpi_balance").getString(),
                recentAvailable ? formatSignedPoints(itemBalanceRecent) : loadingText(),
                balanceTrendText,
                balanceStatus,
                "resourceobserver:terminal/kpi_efficiency"
        ));

        List<OverviewViewModel.KpiDetail> details = new ArrayList<>(3);
        details.add(new OverviewViewModel.KpiDetail(
                OverviewViewModel.KpiType.PRODUCTION,
                Component.translatable("screen.resourceobserver.overview.section.kpi_production").getString(),
                productionTrendText,
                productionStatus,
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.item").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatRateExact(itemProducedRecentRate, "/min") : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatRateExact(itemProducedPreviousRate, "/min") : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                itemProductionTrendAvailable ? formatSignedPercent(itemProductionTrendPercent) : trendUnavailableText()),
                        recentAvailable
                ),
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.fluid").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatRateExact(fluidProducedRecentRate, "B/min") : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatRateExact(fluidProducedPreviousRate, "B/min") : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                fluidProductionTrendAvailable ? formatSignedPercent(fluidProductionTrendPercent) : trendUnavailableText()),
                        recentAvailable
                )
        ));
        details.add(new OverviewViewModel.KpiDetail(
                OverviewViewModel.KpiType.CONSUMPTION,
                Component.translatable("screen.resourceobserver.overview.section.kpi_consumption").getString(),
                consumptionTrendText,
                consumptionStatus,
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.item").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatRateExact(itemConsumedRecentRate, "/min") : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatRateExact(itemConsumedPreviousRate, "/min") : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                itemConsumptionTrendAvailable ? formatSignedPercent(itemConsumptionTrendPercent) : trendUnavailableText()),
                        recentAvailable
                ),
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.fluid").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatRateExact(fluidConsumedRecentRate, "B/min") : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatRateExact(fluidConsumedPreviousRate, "B/min") : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                fluidConsumptionTrendAvailable ? formatSignedPercent(fluidConsumptionTrendPercent) : trendUnavailableText()),
                        recentAvailable
                )
        ));
        details.add(new OverviewViewModel.KpiDetail(
                OverviewViewModel.KpiType.BALANCE,
                Component.translatable("screen.resourceobserver.overview.section.kpi_balance").getString(),
                balanceHint(balanceStatus, recentAvailable),
                balanceStatus,
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.item").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatSignedPoints(itemBalanceRecent) : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatSignedPoints(itemBalancePrevious) : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                itemBalanceTrendAvailable ? formatSignedPoints(itemBalanceDelta) : trendUnavailableText()),
                        recentAvailable
                ),
                new OverviewViewModel.KpiDetailChannel(
                        Component.translatable("screen.resourceobserver.overview.kpi.detail.channel.fluid").getString(),
                        detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                                recentAvailable ? formatSignedPoints(fluidBalanceRecent) : loadingText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                                previousAvailable ? formatSignedPoints(fluidBalancePrevious) : naText()),
                        detailLine("screen.resourceobserver.overview.kpi.detail.change",
                                fluidBalanceTrendAvailable ? formatSignedPoints(fluidBalanceDelta) : trendUnavailableText()),
                        recentAvailable
                )
        ));
        return new KpiPresentation(kpis, details);
    }

    private static String detailLine(String key, String value) {
        return Component.translatable(key, value).getString();
    }

    private static OverviewViewModel.Status statusForProduction(boolean trendAvailable, double trendPercent) {
        if (!trendAvailable) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        if (trendPercent >= 5.0d) {
            return OverviewViewModel.Status.POSITIVE;
        }
        if (trendPercent <= -5.0d) {
            return OverviewViewModel.Status.WARNING;
        }
        return OverviewViewModel.Status.NEUTRAL;
    }

    private static OverviewViewModel.Status statusForConsumption(boolean trendAvailable, double trendPercent) {
        if (!trendAvailable) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        if (trendPercent >= 5.0d) {
            return OverviewViewModel.Status.WARNING;
        }
        if (trendPercent <= -5.0d) {
            return OverviewViewModel.Status.POSITIVE;
        }
        return OverviewViewModel.Status.NEUTRAL;
    }

    private static OverviewViewModel.Status statusForBalance(double score, boolean available) {
        if (!available) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        if (score >= 8.0d) {
            return OverviewViewModel.Status.POSITIVE;
        }
        if (score > -8.0d) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        if (score >= -25.0d) {
            return OverviewViewModel.Status.WARNING;
        }
        return OverviewViewModel.Status.NEGATIVE;
    }

    private static String balanceHint(OverviewViewModel.Status status, boolean available) {
        if (!available) {
            return Component.translatable("screen.resourceobserver.overview.kpi.balance.hint.unavailable").getString();
        }
        return switch (status) {
            case POSITIVE -> Component.translatable("screen.resourceobserver.overview.kpi.balance.hint.positive").getString();
            case WARNING -> Component.translatable("screen.resourceobserver.overview.kpi.balance.hint.warning").getString();
            case NEGATIVE -> Component.translatable("screen.resourceobserver.overview.kpi.balance.hint.negative").getString();
            case NEUTRAL -> Component.translatable("screen.resourceobserver.overview.kpi.balance.hint.neutral").getString();
        };
    }

    private static double percentChange(double recent, double previous) {
        if (!Double.isFinite(recent) || !Double.isFinite(previous) || previous <= 0.0d) {
            return 0.0d;
        }
        return ((recent - previous) / previous) * 100.0d;
    }

    private static double balanceScore(double productionRate, double consumptionRate) {
        if (!Double.isFinite(productionRate) || !Double.isFinite(consumptionRate)) {
            return 0.0d;
        }
        double sum = productionRate + consumptionRate;
        if (sum <= 0.0d) {
            return 0.0d;
        }
        double score = ((productionRate - consumptionRate) / sum) * 100.0d;
        if (score > 100.0d) {
            return 100.0d;
        }
        if (score < -100.0d) {
            return -100.0d;
        }
        return score;
    }

    private static String trendUnavailableText() {
        return Component.translatable("screen.resourceobserver.overview.kpi.trend.unavailable").getString();
    }

    private static String naText() {
        return Component.translatable("screen.resourceobserver.overview.kpi.detail.na").getString();
    }

    /** 预热期占位文字（"Loading..." / "加载中…"） */
    private static String loadingText() {
        return Component.translatable("screen.resourceobserver.overview.kpi.loading").getString();
    }

    private static String formatRateCompact(double value, String unitSuffix) {
        if (!Double.isFinite(value)) {
            return naText();
        }
        return formatCompactDouble(value) + " " + unitSuffix;
    }

    private static String formatRateExact(double value, String unitSuffix) {
        if (!Double.isFinite(value)) {
            return naText();
        }
        return formatExactDouble(value) + " " + unitSuffix;
    }

    private static String formatSignedPercent(double value) {
        if (!Double.isFinite(value)) {
            return naText();
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%+.1f%%", value));
    }

    private static String formatSignedPoints(double value) {
        if (!Double.isFinite(value)) {
            return naText();
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%+.1f pts", value));
    }

    /**
     * 构建存储 KPI 指标卡。
     * <p>
     * 多层条件链处理各种边界情况（优先级从高到低）：
     * 1. 无 AE2 绑定 → 显示 N/A + "非 AE2 网络"
     * 2. 容量数据不可用 → 显示 N/A + "单元格不可用"
     * 3. 不可靠且无可读数据 → 显示 N/A + "部分读取"
     * 4. 正常情况 → 显示实际填充率，并根据填充率/类型占满等设置警告状态
     */
    private static StoragePresentation buildStoragePresentation(CellCapacityAggregation aggregation) {
        if (!aggregation.hasAe2Binding()) {
            String hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.not_ae2").getString();
            OverviewViewModel.KpiMetric kpi = new OverviewViewModel.KpiMetric(
                    OverviewViewModel.KpiType.STORAGE,
                    Component.translatable("screen.resourceobserver.overview.section.kpi_storage").getString(),
                    "N/A",
                    hint,
                    OverviewViewModel.Status.NEUTRAL,
                    "resourceobserver:terminal/kpi_storage"
            );
            return new StoragePresentation(kpi, OverviewViewModel.StorageDetail.unavailable(hint));
        }

        long itemUsedBytes = aggregation.itemUsedBytes();
        long itemTotalBytes = Math.max(aggregation.itemTotalBytes(), itemUsedBytes);
        long itemUsedTypes = aggregation.itemUsedTypes();
        long itemTotalTypes = Math.max(aggregation.itemTotalTypes(), itemUsedTypes);
        long fluidUsedBytes = aggregation.fluidUsedBytes();
        long fluidTotalBytes = Math.max(aggregation.fluidTotalBytes(), fluidUsedBytes);
        long fluidUsedTypes = aggregation.fluidUsedTypes();
        long fluidTotalTypes = Math.max(aggregation.fluidTotalTypes(), fluidUsedTypes);
        long externalItemUsed = aggregation.externalItemUsedUnits();
        long externalItemTotal = Math.max(aggregation.externalItemTotalUnits(), externalItemUsed);
        long externalFluidUsed = aggregation.externalFluidUsedUnits();
        long externalFluidTotal = Math.max(aggregation.externalFluidTotalUnits(), externalFluidUsed);

        OverviewViewModel.Status status = OverviewViewModel.Status.POSITIVE;
        String hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.normal").getString();
        if (!aggregation.diskAvailable() && !aggregation.externalAvailable()) {
            status = OverviewViewModel.Status.WARNING;
            hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.cells_unavailable").getString();
        } else if (!aggregation.diskReliable() || !aggregation.externalReliable()) {
            status = OverviewViewModel.Status.WARNING;
            hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.unreliable").getString();
        } else if ((itemTotalTypes > 0L && itemUsedTypes >= itemTotalTypes)
                || (fluidTotalTypes > 0L && fluidUsedTypes >= fluidTotalTypes)) {
            status = OverviewViewModel.Status.WARNING;
            hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.types_full").getString();
        } else {
            double itemFill = itemTotalBytes > 0 ? (double) itemUsedBytes / (double) itemTotalBytes : 0.0d;
            double fluidFill = fluidTotalBytes > 0 ? (double) fluidUsedBytes / (double) fluidTotalBytes : 0.0d;
            if (Math.max(itemFill, fluidFill) >= 0.9d) {
                status = OverviewViewModel.Status.WARNING;
                hint = Component.translatable("screen.resourceobserver.overview.kpi.storage.bytes_high").getString();
            }
        }

        OverviewViewModel.StorageDetail detail = new OverviewViewModel.StorageDetail(
                true,
                aggregation.diskReliable(),
                aggregation.externalReliable(),
                hint,
                new OverviewViewModel.StorageChannel(
                        Component.translatable("screen.resourceobserver.overview.storage.detail.disk_item").getString(),
                        formatBytesUsage(itemUsedBytes, itemTotalBytes, aggregation.diskAvailable()),
                        formatTypesUsage(itemUsedTypes, itemTotalTypes, aggregation.diskAvailable()),
                        formatBytesUsageExact(itemUsedBytes, itemTotalBytes, aggregation.diskAvailable()),
                        formatTypesUsageExact(itemUsedTypes, itemTotalTypes, aggregation.diskAvailable()),
                        aggregation.diskAvailable()
                ),
                new OverviewViewModel.StorageChannel(
                        Component.translatable("screen.resourceobserver.overview.storage.detail.disk_fluid").getString(),
                        formatBytesUsage(fluidUsedBytes, fluidTotalBytes, aggregation.diskAvailable()),
                        formatTypesUsage(fluidUsedTypes, fluidTotalTypes, aggregation.diskAvailable()),
                        formatBytesUsageExact(fluidUsedBytes, fluidTotalBytes, aggregation.diskAvailable()),
                        formatTypesUsageExact(fluidUsedTypes, fluidTotalTypes, aggregation.diskAvailable()),
                        aggregation.diskAvailable()
                ),
                new OverviewViewModel.StorageChannel(
                        Component.translatable("screen.resourceobserver.overview.storage.detail.external_item").getString(),
                        formatExternalItemUsage(externalItemUsed, externalItemTotal, aggregation.externalAvailable()),
                        Component.translatable("screen.resourceobserver.overview.storage.detail.types_na").getString(),
                        formatExternalItemUsageExact(externalItemUsed, externalItemTotal, aggregation.externalAvailable()),
                        Component.translatable("screen.resourceobserver.overview.storage.detail.types_na").getString(),
                        aggregation.externalAvailable()
                ),
                new OverviewViewModel.StorageChannel(
                        Component.translatable("screen.resourceobserver.overview.storage.detail.external_fluid").getString(),
                        formatExternalFluidUsage(externalFluidUsed, externalFluidTotal, aggregation.externalAvailable()),
                        Component.translatable("screen.resourceobserver.overview.storage.detail.types_na").getString(),
                        formatExternalFluidUsageExact(externalFluidUsed, externalFluidTotal, aggregation.externalAvailable()),
                        Component.translatable("screen.resourceobserver.overview.storage.detail.types_na").getString(),
                        aggregation.externalAvailable()
                )
        );

        OverviewViewModel.KpiMetric kpi = new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.STORAGE,
                Component.translatable("screen.resourceobserver.overview.section.kpi_storage").getString(),
                Component.translatable("screen.resourceobserver.overview.kpi.storage.open_detail").getString(),
                hint,
                status,
                "resourceobserver:terminal/kpi_storage"
        );
        return new StoragePresentation(kpi, detail);
    }

    private static String formatBytesUsage(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.bytes_usage",
                formatByteLike(used),
                formatByteLike(total)
        ).getString();
    }

    private static String formatTypesUsage(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.types_usage",
                formatCompact(used),
                formatCompact(total)
        ).getString();
    }

    private static String formatBytesUsageExact(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.bytes_usage",
                formatExactByteLike(used),
                formatExactByteLike(total)
        ).getString();
    }

    private static String formatTypesUsageExact(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.types_usage",
                formatExactCount(used),
                formatExactCount(total)
        ).getString();
    }

    private static String formatExternalItemUsage(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.external_item_usage",
                formatCompact(used),
                formatCompact(total)
        ).getString();
    }

    private static String formatExternalItemUsageExact(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.external_item_usage",
                formatExactCount(used),
                formatExactCount(total)
        ).getString();
    }

    private static String formatExternalFluidUsage(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.external_fluid_usage",
                formatCompact(used),
                formatCompact(total)
        ).getString();
    }

    private static String formatExternalFluidUsageExact(long used, long total, boolean available) {
        if (!available) {
            return "N/A";
        }
        return Component.translatable(
                "screen.resourceobserver.overview.storage.detail.external_fluid_usage",
                formatExactCount(used),
                formatExactCount(total)
        ).getString();
    }

    /** 构建分组选项列表，确保"未分组"始终在首位 */
    private static List<OverviewViewModel.GroupOption> buildGroups(List<ObserverDataPayload.GroupEntry> source) {
        String ungroupedLabel = Component.translatable("screen.resourceobserver.overview.group.ungrouped").getString();
        LinkedHashMap<String, OverviewViewModel.GroupOption> groups = new LinkedHashMap<>();
        groups.put(
                PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                new OverviewViewModel.GroupOption(
                        PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                        ungroupedLabel,
                        true
                )
        );
        for (ObserverDataPayload.GroupEntry group : source) {
            String key = normalizeGroupKey(group.key());
            if (key.isBlank() || PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(key)) {
                continue;
            }
            if (PlayerUiPrefsSavedData.GROUP_UNGROUPED.equals(key)) {
                groups.put(key, new OverviewViewModel.GroupOption(key, ungroupedLabel, true));
            } else {
                String displayName = group.displayName() == null || group.displayName().isBlank()
                        ? key
                        : group.displayName();
                groups.put(key, new OverviewViewModel.GroupOption(key, displayName, group.systemGroup()));
            }
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * 应用状态筛选、分组筛选、搜索过滤和排序。
     * 先过滤不符合条件的行，再按指定模式排序。
     */
    private static List<OverviewViewModel.TableRow> applyFiltersAndSort(
            List<OverviewViewModel.TableRow> rows,
            TableStatusFilter statusFilter,
            String groupFilterKey,
            TableSortMode sortMode,
            boolean sortDesc,
            String searchQuery
    ) {
        String queryTrimmed = (searchQuery == null || searchQuery.isBlank())
                ? "" : searchQuery.trim();
        List<OverviewViewModel.TableRow> filtered = new ArrayList<>();
        for (OverviewViewModel.TableRow row : rows) {
            if (!matchesStatus(row, statusFilter)) {
                continue;
            }
            if (!matchesGroup(row, groupFilterKey)) {
                continue;
            }
            if (!queryTrimmed.isEmpty()
                    && !PinyinMatcher.matches(row.displayName(), queryTrimmed)) {
                continue;
            }
            filtered.add(row);
        }
        filtered.sort(comparatorFor(sortMode, sortDesc));
        return filtered;
    }

    /**
     * 从表格行数据中提取关注列表物品。
     * 按 watchlistItemIds 的顺序排列，仅包含表格中存在的物品。
     */
    private static List<OverviewViewModel.WatchlistItem> buildWatchlist(
            List<OverviewViewModel.TableRow> rows,
            List<String> watchlistItemIds
    ) {
        if (watchlistItemIds.isEmpty() || rows.isEmpty()) {
            return List.of();
        }
        Map<String, OverviewViewModel.TableRow> rowById = new HashMap<>();
        for (OverviewViewModel.TableRow row : rows) {
            rowById.putIfAbsent(row.itemId(), row);
        }

        List<OverviewViewModel.WatchlistItem> watch = new ArrayList<>();
        for (String itemId : watchlistItemIds) {
            OverviewViewModel.TableRow row = rowById.get(itemId);
            if (row == null) {
                continue;
            }
            watch.add(new OverviewViewModel.WatchlistItem(
                    row.itemId(),
                    row.displayName(),
                    row.net(),
                    row.stock(),
                    true,
                    row.iconSprite()
            ));
        }
        return watch;
    }

    /**
     * 将过滤后的行按分组归类。
     * 筛选 ALL 时显示所有分组，否则仅显示选中分组。
     */
    private static List<OverviewViewModel.TableGroup> groupRows(
            List<OverviewViewModel.TableRow> rows,
            String groupFilterKey,
            List<OverviewViewModel.GroupOption> groupOptions
    ) {
        LinkedHashMap<String, List<OverviewViewModel.TableRow>> grouped = new LinkedHashMap<>();
        for (OverviewViewModel.GroupOption group : groupOptions) {
            grouped.put(group.key(), new ArrayList<>());
        }
        for (OverviewViewModel.TableRow row : rows) {
            grouped.computeIfAbsent(normalizeGroupKey(row.groupKey()), ignored -> new ArrayList<>()).add(row);
        }

        List<OverviewViewModel.TableGroup> result = new ArrayList<>();
        if (PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            for (OverviewViewModel.GroupOption group : groupOptions) {
                List<OverviewViewModel.TableRow> groupRows = grouped.getOrDefault(group.key(), List.of());
                if (groupRows.isEmpty()) {
                    continue;
                }
                result.add(new OverviewViewModel.TableGroup(group.key(), group.displayName(), groupRows));
            }
            if (result.isEmpty()) {
                result.add(new OverviewViewModel.TableGroup(
                        PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                        Component.translatable("screen.resourceobserver.overview.group.ungrouped").getString(),
                        List.of()
                ));
            }
            return result;
        }

        String key = normalizeGroupKey(groupFilterKey);
        String title = key;
        for (OverviewViewModel.GroupOption group : groupOptions) {
            if (group.key().equals(key)) {
                title = group.displayName();
                break;
            }
        }
        result.add(new OverviewViewModel.TableGroup(key, title, grouped.getOrDefault(key, List.of())));
        return result;
    }

    /**
     * 无物品级数据时的回退方案。
     * 为每个绑定创建一行概要数据（如 Flux 能量网络无逐物品数据）。
     */
    private static List<OverviewViewModel.TableRow> buildFallbackRows(
            ObserverDataPayload payload,
            Collection<String> starredItems
    ) {
        List<OverviewViewModel.TableRow> rows = new ArrayList<>();
        int idx = 0;
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            String fallbackId = binding.networkType().toLowerCase(Locale.ROOT) + "_" + idx++;
            long stock = binding.currentValue();
            long net = binding.totalProduced() - binding.totalConsumed();
            rows.add(new OverviewViewModel.TableRow(
                    fallbackId,
                    binding.displayName(),
                    PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                    binding.totalProduced(),
                    binding.totalConsumed(),
                    net,
                    stock,
                    isCritical(net, stock),
                    starredItems.contains(fallbackId),
                    binding.iconSprite()
            ));
        }
        return rows;
    }

    /**
     * 尝试使用客户端注册表本地化物品名称。
     * 优先使用 Minecraft 的物品翻译系统，失败时回退到服务端提供的显示名。
     */
    private static String localizeEntryName(
            ObserverDataPayload.EntryType entryType,
            String itemId,
            String fallbackDisplayName
    ) {
        if (itemId == null || itemId.isBlank()) {
            return fallbackDisplayName;
        }
        if (entryType == ObserverDataPayload.EntryType.FLUID) {
            return fallbackDisplayName == null || fallbackDisplayName.isBlank() ? itemId : fallbackDisplayName;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item != Items.AIR) {
                String translated = new ItemStack(item).getHoverName().getString();
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
            }
        } catch (Exception ignored) {
            // 当 id 无效或在客户端无法解析时，保留备用名称
        }
        return fallbackDisplayName == null || fallbackDisplayName.isBlank() ? itemId : fallbackDisplayName;
    }

    /** 规范化分组键，空值映射到"未分组" */
    private static String normalizeGroupKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlayerUiPrefsSavedData.GROUP_UNGROUPED;
        }
        return raw;
    }

    /** 规范化分组筛选键，空值映射到"全部" */
    private static String normalizeGroupFilterKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlayerUiPrefsSavedData.GROUP_FILTER_ALL;
        }
        return raw;
    }

    /** 判断行是否匹配状态筛选条件 */
    private static boolean matchesStatus(OverviewViewModel.TableRow row, TableStatusFilter statusFilter) {
        return switch (statusFilter) {
            case ALL -> true;
            case SURPLUS -> row.net() > 0;
            case DEFICIT -> row.net() < 0;
            case CRITICAL -> row.critical();
        };
    }

    /** 判断行是否匹配分组筛选条件 */
    private static boolean matchesGroup(OverviewViewModel.TableRow row, String groupFilterKey) {
        if (groupFilterKey == null || groupFilterKey.isBlank() || PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            return true;
        }
        return groupFilterKey.equals(row.groupKey());
    }

    /**
     * 根据排序模式生成比较器。
     * 主排序按指定字段，副排序按显示名称字母序。
     */
    private static Comparator<OverviewViewModel.TableRow> comparatorFor(TableSortMode sortMode, boolean sortDesc) {
        Comparator<OverviewViewModel.TableRow> comparator = switch (sortMode) {
            case NET_ABS, NET -> Comparator.comparingLong(OverviewViewModel.TableRow::net);
            case PRODUCTION -> Comparator.comparingLong(OverviewViewModel.TableRow::production);
            case CONSUMPTION -> Comparator.comparingLong(OverviewViewModel.TableRow::consumption);
            case STOCK -> Comparator.comparingLong(OverviewViewModel.TableRow::stock);
        };
        if (sortDesc) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(OverviewViewModel.TableRow::displayName);
    }

    /** 判断物品是否处于严重亏损状态（净消耗且库存极低） */
    private static boolean isCritical(long net, long stock) {
        if (net >= 0) {
            return false;
        }
        return stock <= 64L;
    }

    private record StoragePresentation(
            OverviewViewModel.KpiMetric storageKpi,
            OverviewViewModel.StorageDetail storageDetail
    ) {
    }

    private record KpiPresentation(
            List<OverviewViewModel.KpiMetric> kpis,
            List<OverviewViewModel.KpiDetail> details
    ) {
    }

    /**
     * 聚合多个绑定的 KPI 速率数据。
     * 服务端已预计算每分钟速率，客户端仅需累加各绑定的速率值。
     */
    private record WindowStatsAggregation(
            double itemProducedRecentRate,
            double itemConsumedRecentRate,
            double itemProducedPreviousRate,
            double itemConsumedPreviousRate,
            double fluidProducedRecentRate,
            double fluidConsumedRecentRate,
            double fluidProducedPreviousRate,
            double fluidConsumedPreviousRate,
            boolean recentAvailable,
            boolean previousAvailable,
            boolean trendAvailable
    ) {
        private static WindowStatsAggregation empty() {
            return new WindowStatsAggregation(
                    0.0d, 0.0d, 0.0d, 0.0d,
                    0.0d, 0.0d, 0.0d, 0.0d,
                    false, false, false
            );
        }

        private WindowStatsAggregation merge(ObserverDataPayload.KpiWindowStats stats) {
            if (stats == null) {
                return this;
            }
            return new WindowStatsAggregation(
                    itemProducedRecentRate + stats.itemProducedRecentRate(),
                    itemConsumedRecentRate + stats.itemConsumedRecentRate(),
                    itemProducedPreviousRate + stats.itemProducedPreviousRate(),
                    itemConsumedPreviousRate + stats.itemConsumedPreviousRate(),
                    fluidProducedRecentRate + stats.fluidProducedRecentRate(),
                    fluidConsumedRecentRate + stats.fluidConsumedRecentRate(),
                    fluidProducedPreviousRate + stats.fluidProducedPreviousRate(),
                    fluidConsumedPreviousRate + stats.fluidConsumedPreviousRate(),
                    recentAvailable || stats.recentAvailable(),
                    previousAvailable || stats.previousAvailable(),
                    trendAvailable || stats.trendAvailable()
            );
        }
    }

    /**
     * 将大数值格式化为紧凑表示（K/M/B 后缀）。
     * 例如：1234 → "1.2K"，1234567 → "1.2M"
     */
    private static String formatByteLike(long value) {
        return formatCompact(value) + " B";
    }

    private static String formatExactByteLike(long value) {
        return formatExactCount(value) + " B";
    }

    private static String formatExactCount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatCompactDouble(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0d));
        }
        if (abs >= 1_000_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0d));
        }
        if (abs >= 1_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fK", value / 1_000.0d));
        }
        if (Math.abs(value - Math.rint(value)) < 1.0E-6d) {
            return String.format(Locale.ROOT, "%,d", (long) Math.rint(value));
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%,.1f", value));
    }

    private static String formatExactDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6d) {
            return String.format(Locale.ROOT, "%,d", (long) Math.rint(value));
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%,.2f", value));
    }

    private static String trimTrailingZeros(String value) {
        if (value == null || value.isEmpty() || !value.contains(".")) {
            return value;
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return end <= 0 ? "0" : value.substring(0, end);
    }

    private record CellCapacityAggregation(
            long itemUsedBytes,
            long itemTotalBytes,
            long itemUsedTypes,
            long itemTotalTypes,
            long itemUsedUnits,
            long itemMaxUnits,
            long fluidUsedBytes,
            long fluidTotalBytes,
            long fluidUsedTypes,
            long fluidTotalTypes,
            long fluidUsedUnits,
            long fluidMaxUnits,
            long externalItemUsedUnits,
            long externalItemTotalUnits,
            long externalFluidUsedUnits,
            long externalFluidTotalUnits,
            boolean hasAe2Binding,
            boolean diskAvailable,
            boolean diskReliable,
            boolean externalAvailable,
            boolean externalReliable
    ) {
        private static CellCapacityAggregation empty() {
            return new CellCapacityAggregation(
                    0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L,
                    false, false, true, false, true
            );
        }

        private CellCapacityAggregation merge(ObserverDataPayload.BindingEntry binding) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                return this;
            }
            ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
            if (metrics == null) {
                return new CellCapacityAggregation(
                        itemUsedBytes,
                        itemTotalBytes,
                        itemUsedTypes,
                        itemTotalTypes,
                        itemUsedUnits,
                        itemMaxUnits,
                        fluidUsedBytes,
                        fluidTotalBytes,
                        fluidUsedTypes,
                        fluidTotalTypes,
                        fluidUsedUnits,
                        fluidMaxUnits,
                        externalItemUsedUnits,
                        externalItemTotalUnits,
                        externalFluidUsedUnits,
                        externalFluidTotalUnits,
                        true,
                        diskAvailable,
                        false,
                        externalAvailable,
                        false
                );
            }
            return new CellCapacityAggregation(
                    saturatingAdd(itemUsedBytes, Math.max(0L, metrics.itemUsedBytes())),
                    saturatingAdd(itemTotalBytes, Math.max(0L, metrics.itemTotalBytes())),
                    saturatingAdd(itemUsedTypes, Math.max(0L, metrics.itemUsedTypes())),
                    saturatingAdd(itemTotalTypes, Math.max(0L, metrics.itemTotalTypes())),
                    saturatingAdd(itemUsedUnits, Math.max(0L, metrics.itemUsedUnits())),
                    saturatingAdd(itemMaxUnits, Math.max(0L, metrics.itemMaxUnits())),
                    saturatingAdd(fluidUsedBytes, Math.max(0L, metrics.fluidUsedBytes())),
                    saturatingAdd(fluidTotalBytes, Math.max(0L, metrics.fluidTotalBytes())),
                    saturatingAdd(fluidUsedTypes, Math.max(0L, metrics.fluidUsedTypes())),
                    saturatingAdd(fluidTotalTypes, Math.max(0L, metrics.fluidTotalTypes())),
                    saturatingAdd(fluidUsedUnits, Math.max(0L, metrics.fluidUsedUnits())),
                    saturatingAdd(fluidMaxUnits, Math.max(0L, metrics.fluidMaxUnits())),
                    saturatingAdd(externalItemUsedUnits, Math.max(0L, metrics.externalItemUsedUnits())),
                    saturatingAdd(externalItemTotalUnits, Math.max(0L, metrics.externalItemTotalUnits())),
                    saturatingAdd(externalFluidUsedUnits, Math.max(0L, metrics.externalFluidUsedUnits())),
                    saturatingAdd(externalFluidTotalUnits, Math.max(0L, metrics.externalFluidTotalUnits())),
                    true,
                    diskAvailable || metrics.available(),
                    diskReliable && metrics.reliable(),
                    externalAvailable || metrics.externalAvailable(),
                    externalReliable && metrics.externalReliable()
            );
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
