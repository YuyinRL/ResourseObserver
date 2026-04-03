package com.yuyinrl.resourceobserver.client.ui;

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
     */
    public static OverviewViewModel fromPayload(ObserverDataPayload payload) {
        LinkedHashSet<String> watchlistSet = new LinkedHashSet<>(payload.watchlistItemIds());
        // 汇总所有绑定网络的 KPI 总量
        long totalProduced = 0L;
        long totalConsumed = 0L;
        CellCapacityAggregation cellCapacityAggregation = CellCapacityAggregation.empty();

        // 遍历所有绑定，构建表格行数据
        List<OverviewViewModel.TableRow> allRows = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            totalProduced += binding.totalProduced();
            totalConsumed += binding.totalConsumed();
            cellCapacityAggregation = cellCapacityAggregation.merge(binding);

            // 为每种物品创建表格行
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                long production = Math.max(0L, item.delta());     // 正增量 → 生产
                long consumption = Math.max(0L, -item.delta());   // 负增量 → 消耗
                long net = item.delta();
                String localizedName = localizeEntryName(item.entryType(), item.itemId(), item.displayName());
                allRows.add(new OverviewViewModel.TableRow(
                        item.itemId(),
                        localizedName,
                        normalizeGroupKey(item.groupKey()),
                        production,
                        consumption,
                        net,
                        item.amount(),
                        isCritical(net, item.amount()),
                        watchlistSet.contains(item.itemId()),
                        item.iconSprite()
                ));
            }
        }

        // 无物品级数据时使用绑定级数据构建回退行
        if (allRows.isEmpty()) {
            allRows = buildFallbackRows(payload, watchlistSet);
        }

        // 构建四个 KPI 指标卡
        StoragePresentation storagePresentation = buildStoragePresentation(cellCapacityAggregation);
        List<OverviewViewModel.KpiMetric> kpis = buildKpis(
                totalProduced,
                totalConsumed,
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
                uiState.sortMode(), uiState.sortDesc()
        );
        // 按分组归类行数据
        List<OverviewViewModel.TableGroup> groupedRows = groupRows(filteredRows, uiState.groupFilterKey(), uiState.groups());

        return new OverviewViewModel(
                "OPERATIONS DASHBOARD",
                "Global monitoring of linked storage networks and resource flow.",
                "LINK ESTABLISHED",
                kpis, payload.chartWindow(), payload.chartScopeItemId(),
                chartSeries, uiState, watchlist, groupedRows, storagePresentation.storageDetail()
        );
    }

    /**
     * 构建四个 KPI 指标卡。
     * - 生产量：总生产量/分钟
     * - 消耗量：总消耗量/分钟
     * - 库存：填充率百分比
     * - 效率：消耗/生产比率
     */
    private static List<OverviewViewModel.KpiMetric> buildKpis(
            long totalProduced,
            long totalConsumed,
            OverviewViewModel.KpiMetric storageKpi
    ) {
        long net = totalProduced - totalConsumed;
        // 效率公式：消耗/生产比。efficiency=1.0 表示生产与消耗完全平衡，
        // 显示值 = (1 - max(0, ratio-1)) * 100：
        //   ratio=0.8 → 消耗低于生产 → 显示 100%（因为 max(0, -0.2)=0）
        //   ratio=1.5 → 消耗超出生产 50% → 显示 50%
        double efficiency = totalProduced > 0 ? (double) totalConsumed / (double) totalProduced : 0.0;

        List<OverviewViewModel.KpiMetric> result = new ArrayList<>(4);
        result.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.PRODUCTION,
                "screen.resourceobserver.overview.section.kpi_production",
                formatCompact(totalProduced) + " /min",
                net >= 0 ? "+5.2% vs cycle" : "-2.4% vs cycle",
                net >= 0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.WARNING,
                "resourceobserver:terminal/kpi_production"
        ));
        result.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.CONSUMPTION,
                "screen.resourceobserver.overview.section.kpi_consumption",
                formatCompact(totalConsumed) + " /min",
                totalConsumed > totalProduced ? "+2.1% vs cycle" : "-1.3% vs cycle",
                totalConsumed > totalProduced ? OverviewViewModel.Status.WARNING : OverviewViewModel.Status.NEUTRAL,
                "resourceobserver:terminal/kpi_consumption"
        ));
        result.add(storageKpi);
        result.add(new OverviewViewModel.KpiMetric(
                OverviewViewModel.KpiType.EFFICIENCY,
                "screen.resourceobserver.overview.section.kpi_efficiency",
                String.format(Locale.ROOT, "%.1f%%", Math.max(0.0, (1.0 - Math.max(0.0, efficiency - 1.0)) * 100.0)),
                "Nominal",
                OverviewViewModel.Status.POSITIVE,
                "resourceobserver:terminal/kpi_efficiency"
        ));
        return result;
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
                    "screen.resourceobserver.overview.section.kpi_storage",
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
                "screen.resourceobserver.overview.section.kpi_storage",
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
     * 应用状态筛选、分组筛选和排序。
     * 先过滤不符合条件的行，再按指定模式排序。
     */
    private static List<OverviewViewModel.TableRow> applyFiltersAndSort(
            List<OverviewViewModel.TableRow> rows,
            TableStatusFilter statusFilter,
            String groupFilterKey,
            TableSortMode sortMode,
            boolean sortDesc
    ) {
        List<OverviewViewModel.TableRow> filtered = new ArrayList<>();
        for (OverviewViewModel.TableRow row : rows) {
            if (!matchesStatus(row, statusFilter)) {
                continue;
            }
            if (!matchesGroup(row, groupFilterKey)) {
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
