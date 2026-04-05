package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 存储网络视图模型映射器 —— 将 ObserverDataPayload 转换为 StorageNetworkViewModel。
 * <p>
 * 映射逻辑：
 * 1. 遍历所有绑定，构建节点列表和全局物品汇总
 * 2. 根据选中节点筛选物品列表
 * 3. 计算每个物品的警报级别
 * 4. 构建使用率摘要分段
 * 5. 构建 KPI 卡片
 */
public final class StorageNetworkViewModelMapper {
    private StorageNetworkViewModelMapper() {
    }

    /**
     * 消耗率临界阈值：缓冲时间 < 30 秒 → 黄色警告。
     * delta 现在是每分钟速率，对应的秒数阈值仍为 30 秒。
     */
    private static final long ALERT_YELLOW_SECONDS = 30L;

    /**
     * 将 Payload 数据转换为 StorageNetworkViewModel。
     *
     * @param payload           服务端数据载荷
     * @param selectedNodeId    当前选中的节点 ID（null/空 = 全部节点）
     * @param alertFilterActive 是否仅显示异常物品
     */
    /**
     * 存储网络支持的网络类型前缀 —— 凡以这些字符串开头的绑定才纳入存储视图。
     * FLUX_ENERGY 等纯能量绑定应由 Power Network 页面单独处理。
     */
    private static boolean isStorageBinding(ObserverDataPayload.BindingEntry binding) {
        String type = binding.networkType();
        if (type == null || type.isBlank()) {
            return true; // 未知类型默认当作存储绑定，保持向后兼容
        }
        // 明确排除能量类型绑定
        return !"FLUX_ENERGY".equalsIgnoreCase(type);
    }

    public static StorageNetworkViewModel fromPayload(
            ObserverDataPayload payload,
            String selectedNodeId,
            boolean alertFilterActive
    ) {
        // 仅保留存储类绑定（过滤掉 FLUX_ENERGY 等纯能量绑定）
        List<ObserverDataPayload.BindingEntry> bindings = payload.bindings().stream()
                .filter(StorageNetworkViewModelMapper::isStorageBinding)
                .toList();
        boolean hasSelection = selectedNodeId != null && !selectedNodeId.isBlank();

        // ========== 构建节点列表 ==========
        List<StorageNetworkViewModel.NodeEntry> nodes = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            double capacityRatio = computeCapacityRatio(binding);
            int itemCount = binding.itemDeltas() == null ? 0 : binding.itemDeltas().size();
            boolean selected = hasSelection && binding.networkId().equals(selectedNodeId);
            boolean statusAlert = capacityRatio >= 0.95;
            String statusLabel = statusAlert
                    ? Component.translatable("screen.resourceobserver.storage.node.status.alert").getString()
                    : Component.translatable("screen.resourceobserver.storage.node.status.healthy").getString();
            long usedValue = computeUsedValue(binding);
            long totalValue = computeTotalValue(binding);
            nodes.add(new StorageNetworkViewModel.NodeEntry(
                    binding.networkId(),
                    binding.displayName(),
                    binding.networkType(),
                    binding.iconSprite(),
                    itemCount,
                    capacityRatio,
                    selected,
                    formatCompactDecimal(usedValue),
                    formatCompactDecimal(totalValue),
                    statusLabel,
                    statusAlert
            ));
        }

        // ========== 构建全局物品汇总 ==========
        Map<String, GlobalItemInfo> globalMap = new LinkedHashMap<>();
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            if (binding.itemDeltas() == null) {
                continue;
            }
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                GlobalItemInfo info = globalMap.computeIfAbsent(item.itemId(),
                        id -> new GlobalItemInfo(item.displayName(), item.iconSprite(), item.groupKey(), 0L, 0L, 0L, 0L));
                globalMap.put(item.itemId(), new GlobalItemInfo(
                        info.displayName(),
                        info.iconSprite(),
                        info.groupKey(),
                        saturatingAdd(info.globalAmount(), item.amount()),
                        saturatingAdd(info.globalDelta(), item.delta()),
                        saturatingAdd(info.globalProductionRate(), item.productionRate()),
                        saturatingAdd(info.globalConsumptionRate(), item.consumptionRate())
                ));
            }
        }

        // ========== 筛选物品列表 ==========
        List<StorageNetworkViewModel.ItemRow> items = new ArrayList<>();
        if (hasSelection) {
            // 仅显示选中节点的物品
            ObserverDataPayload.BindingEntry selectedBinding = findBinding(bindings, selectedNodeId);
            if (selectedBinding != null && selectedBinding.itemDeltas() != null) {
                for (ObserverDataPayload.ItemDeltaEntry item : selectedBinding.itemDeltas()) {
                    GlobalItemInfo global = globalMap.getOrDefault(item.itemId(),
                            new GlobalItemInfo(item.displayName(), item.iconSprite(), item.groupKey(), item.amount(), item.delta(), item.productionRate(), item.consumptionRate()));
                    StorageNetworkViewModel.AlertLevel alert = computeAlertLevel(item.amount(), item.consumptionRate(), selectedBinding);
                    String localizedName = localizeItemName(item.entryType(), item.itemId(), item.displayName());
                    long burnRate = item.consumptionRate();
                    String bufferText = computeBufferText(item.amount(), item.consumptionRate());
                    double bufferRatio = computeBufferRatio(item.amount(), item.consumptionRate());
                    items.add(new StorageNetworkViewModel.ItemRow(
                            item.itemId(),
                            localizedName,
                            item.iconSprite(),
                            item.amount(),
                            global.globalAmount(),
                            item.delta(),
                            alert,
                            item.groupKey(),
                            burnRate,
                            bufferText,
                            bufferRatio
                    ));
                }
            }
        } else {
            // 显示所有节点的汇总物品
            for (Map.Entry<String, GlobalItemInfo> entry : globalMap.entrySet()) {
                GlobalItemInfo info = entry.getValue();
                StorageNetworkViewModel.AlertLevel alert = computeAlertLevelGlobal(info.globalAmount(), info.globalConsumptionRate(), bindings);
                String localizedName = localizeItemName(ObserverDataPayload.EntryType.ITEM, entry.getKey(), info.displayName());
                long burnRate = info.globalConsumptionRate();
                String bufferText = computeBufferText(info.globalAmount(), info.globalConsumptionRate());
                double bufferRatio = computeBufferRatio(info.globalAmount(), info.globalConsumptionRate());
                items.add(new StorageNetworkViewModel.ItemRow(
                        entry.getKey(),
                        localizedName,
                        info.iconSprite(),
                        info.globalAmount(),
                        info.globalAmount(),
                        info.globalDelta(),
                        alert,
                        info.groupKey(),
                        burnRate,
                        bufferText,
                        bufferRatio
                ));
            }
        }

        // 应用警报筛选
        if (alertFilterActive) {
            items = items.stream()
                    .filter(row -> row.alertLevel() != StorageNetworkViewModel.AlertLevel.GREEN)
                    .toList();
        }

        // 按显示名排序
        items = items.stream()
                .sorted(Comparator.comparing(StorageNetworkViewModel.ItemRow::displayName))
                .toList();

        // ========== 构建使用率摘要 ==========
        List<StorageNetworkViewModel.UsageSegment> usageSegments = buildUsageSegments(items);

        // ========== 构建 KPI 卡片 ==========
        List<StorageNetworkViewModel.StorageKpi> kpiCards = buildKpiCards(items, bindings, hasSelection ? selectedNodeId : null);

        // ========== 计算关键指标 ==========
        int criticalItemCount = 0;
        for (StorageNetworkViewModel.ItemRow item : items) {
            if (item.alertLevel() != StorageNetworkViewModel.AlertLevel.GREEN) {
                criticalItemCount++;
            }
        }

        double totalUsedRatio = computeAverageFillRate(bindings, hasSelection ? selectedNodeId : null);

        return new StorageNetworkViewModel(
                nodes,
                selectedNodeId,
                alertFilterActive,
                kpiCards,
                items,
                usageSegments,
                items.size(),
                criticalItemCount,
                totalUsedRatio
        );
    }

    // ========== 私有辅助方法 ==========

    private static ObserverDataPayload.BindingEntry findBinding(
            List<ObserverDataPayload.BindingEntry> bindings,
            String networkId
    ) {
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            if (binding.networkId().equals(networkId)) {
                return binding;
            }
        }
        return null;
    }

    /** 计算绑定的容量填充比例 */
    private static double computeCapacityRatio(ObserverDataPayload.BindingEntry binding) {
        ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
        if (metrics != null && metrics.available()) {
            long totalBytes = Math.max(metrics.itemTotalBytes(), 1L);
            return Math.min(1.0, (double) metrics.itemUsedBytes() / totalBytes);
        }
        if (binding.capacity() > 0) {
            return Math.min(1.0, (double) binding.currentValue() / binding.capacity());
        }
        return 0.0;
    }

    /** 计算单节点内物品的警报级别 */
    private static StorageNetworkViewModel.AlertLevel computeAlertLevel(
            long amount,
            long consumptionRate,
            ObserverDataPayload.BindingEntry binding
    ) {
        // 红色：库存 ≤ 0 或容量 ≥ 95%
        if (amount <= 0) {
            return StorageNetworkViewModel.AlertLevel.RED;
        }
        double capacityRatio = computeCapacityRatio(binding);
        if (capacityRatio >= 0.95) {
            return StorageNetworkViewModel.AlertLevel.RED;
        }
        // 黄色：消耗速率下库存不足 30 秒
        // consumptionRate 是每分钟消耗量（正值），缓冲时间 = amount * 60 / consumptionRate 秒
        // 缓冲 < 30s  →  amount * 60 < 30 * consumptionRate
        if (consumptionRate > 0) {
            if (amount * 60L < ALERT_YELLOW_SECONDS * consumptionRate) {
                return StorageNetworkViewModel.AlertLevel.YELLOW;
            }
        }
        return StorageNetworkViewModel.AlertLevel.GREEN;
    }

    /** 计算全局汇总物品的警报级别 */
    private static StorageNetworkViewModel.AlertLevel computeAlertLevelGlobal(
            long amount,
            long consumptionRate,
            List<ObserverDataPayload.BindingEntry> bindings
    ) {
        if (amount <= 0) {
            return StorageNetworkViewModel.AlertLevel.RED;
        }
        // 检查是否有任何节点容量 ≥ 95%
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            double ratio = computeCapacityRatio(binding);
            if (ratio >= 0.95) {
                return StorageNetworkViewModel.AlertLevel.RED;
            }
        }
        // 黄色：消耗速率下库存不足 30 秒（consumptionRate 是每分钟消耗量，正值）
        if (consumptionRate > 0) {
            if (amount * 60L < ALERT_YELLOW_SECONDS * consumptionRate) {
                return StorageNetworkViewModel.AlertLevel.YELLOW;
            }
        }
        return StorageNetworkViewModel.AlertLevel.GREEN;
    }

    /** 构建使用率摘要分段 */
    private static List<StorageNetworkViewModel.UsageSegment> buildUsageSegments(
            List<StorageNetworkViewModel.ItemRow> items
    ) {
        if (items.isEmpty()) {
            return List.of();
        }
        // 按分组汇总库存量
        Map<String, Long> groupTotals = new LinkedHashMap<>();
        long grandTotal = 0L;
        for (StorageNetworkViewModel.ItemRow item : items) {
            String groupKey = normalizeGroupKey(item.groupKey());
            groupTotals.merge(groupKey, Math.max(0, item.localAmount()), Long::sum);
            grandTotal += Math.max(0, item.localAmount());
        }
        if (grandTotal <= 0L) {
            return List.of();
        }

        // 分组颜色映射
        List<StorageNetworkViewModel.UsageSegment> segments = new ArrayList<>();
        for (Map.Entry<String, Long> entry : groupTotals.entrySet()) {
            double pct = (entry.getValue() * 100.0) / grandTotal;
            if (pct < 0.5) {
                continue;
            }
            String displayName = groupDisplayName(entry.getKey());
            int color = groupColor(entry.getKey());
            segments.add(new StorageNetworkViewModel.UsageSegment(entry.getKey(), displayName, pct, color));
        }
        // 按百分比降序排列
        segments.sort(Comparator.comparingDouble(StorageNetworkViewModel.UsageSegment::percentage).reversed());
        return segments;
    }

    /** 构建三个 KPI 卡片 */
    private static List<StorageNetworkViewModel.StorageKpi> buildKpiCards(
            List<StorageNetworkViewModel.ItemRow> items,
            List<ObserverDataPayload.BindingEntry> bindings,
            String selectedNodeId
    ) {
        // 总物品数量
        long totalAmount = 0L;
        for (StorageNetworkViewModel.ItemRow item : items) {
            totalAmount = saturatingAdd(totalAmount, Math.max(0, item.localAmount()));
        }
        // 总类型数
        int totalTypes = items.size();
        // 填充率
        double fillRate = computeAverageFillRate(bindings, selectedNodeId);

        OverviewViewModel.Status amountStatus = totalAmount > 0
                ? OverviewViewModel.Status.POSITIVE
                : OverviewViewModel.Status.NEUTRAL;
        OverviewViewModel.Status typesStatus = totalTypes > 0
                ? OverviewViewModel.Status.POSITIVE
                : OverviewViewModel.Status.NEUTRAL;
        OverviewViewModel.Status fillStatus = fillRate >= 0.9
                ? OverviewViewModel.Status.WARNING
                : (fillRate >= 0.0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.NEUTRAL);

        return List.of(
                new StorageNetworkViewModel.StorageKpi(
                        "screen.resourceobserver.storage.kpi.total_items",
                        formatCompact(totalAmount),
                        amountStatus
                ),
                new StorageNetworkViewModel.StorageKpi(
                        "screen.resourceobserver.storage.kpi.total_types",
                        String.valueOf(totalTypes),
                        typesStatus
                ),
                new StorageNetworkViewModel.StorageKpi(
                        "screen.resourceobserver.storage.kpi.fill_rate",
                        formatPercent(fillRate),
                        fillStatus
                )
        );
    }

    private static double computeAverageFillRate(
            List<ObserverDataPayload.BindingEntry> bindings,
            String selectedNodeId
    ) {
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            ObserverDataPayload.BindingEntry binding = findBinding(bindings, selectedNodeId);
            return binding != null ? computeCapacityRatio(binding) : 0.0;
        }
        if (bindings.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            double ratio = computeCapacityRatio(binding);
            if (ratio >= 0.0) {
                sum += ratio;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    /** 尝试使用客户端注册表本地化物品名称 */
    private static String localizeItemName(
            ObserverDataPayload.EntryType entryType,
            String itemId,
            String fallback
    ) {
        if (itemId == null || itemId.isBlank()) {
            return fallback;
        }
        if (entryType == ObserverDataPayload.EntryType.FLUID) {
            return fallback == null || fallback.isBlank() ? itemId : fallback;
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
        }
        return fallback == null || fallback.isBlank() ? itemId : fallback;
    }

    private static String normalizeGroupKey(String raw) {
        return raw == null || raw.isBlank() ? "ungrouped" : raw;
    }

    private static String groupDisplayName(String groupKey) {
        return switch (groupKey.toLowerCase(Locale.ROOT)) {
            case "raw" -> Component.translatable("screen.resourceobserver.storage.usage.raw").getString();
            case "intermediate" -> Component.translatable("screen.resourceobserver.storage.usage.intermediate").getString();
            case "finished" -> Component.translatable("screen.resourceobserver.storage.usage.finished").getString();
            default -> Component.translatable("screen.resourceobserver.storage.usage.other").getString();
        };
    }

    private static int groupColor(String groupKey) {
        return switch (groupKey.toLowerCase(Locale.ROOT)) {
            case "raw" -> UiThemeTokens.CYAN;
            case "intermediate" -> UiThemeTokens.AMBER;
            case "finished" -> UiThemeTokens.EMERALD;
            default -> UiThemeTokens.BLUE;
        };
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

    /** 格式化带小数的紧凑数值（如 852.0k） */
    private static String formatCompactDecimal(long value) {
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

    private static String formatPercent(double ratio) {
        if (ratio < 0.0) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /**
     * 返回每分钟消耗速率。
     * consumptionRate 是服务端提供的每分钟消耗速率（≥0），直接返回即可。
     */
    private static long computeBurnRatePerMin(long consumptionRate) {
        return Math.max(0L, consumptionRate);
    }

    /**
     * 计算预估缓冲时间文本。
     * consumptionRate 是每分钟消耗速率（正值），缓冲秒数 = amount × 60 / consumptionRate。
     */
    private static String computeBufferText(long amount, long consumptionRate) {
        if (consumptionRate <= 0 || amount <= 0) {
            return "∞";
        }
        long totalSeconds = amount * 60L / consumptionRate;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * 计算缓冲比例（用于进度条，最大 1.0）。
     * <p>
     * 采用三段非线性刻度，将有限的进度条宽度合理分配到三个语义区间：
     * <pre>
     *  时间区间       进度条区间   语义
     *  0  ~ 30s      0% ~ 30%   危险区（放大显示，让细微差异也可见）
     *  30s ~ 5min   30% ~ 70%   警告区（线性映射）
     *  5min ~ 60min 70% ~ 100%  安全区（压缩显示）
     *  ≥ 60min / ∞  100%        稳定
     * </pre>
     * 对应的颜色阈值也以进度条比例（而非秒数）表达：< 30% 为红，30%~70% 为琥珀，> 70% 为绿。
     */
    private static double computeBufferRatio(long amount, long consumptionRate) {
        if (consumptionRate <= 0 || amount <= 0) {
            return 1.0;
        }
        double t = (double) amount * 60.0 / consumptionRate; // 剩余秒数（浮点）
        if (t <= 0)   return 0.0;
        if (t <= 30)  return t / 30.0 * 0.30;                         // 危险区 0% → 30%
        if (t <= 300) return 0.30 + (t - 30.0) / 270.0 * 0.40;       // 警告区 30% → 70%
        return Math.min(1.0, 0.70 + (t - 300.0) / 3300.0 * 0.30);    // 安全区 70% → 100%
    }

    /** 计算节点已用值 */
    private static long computeUsedValue(ObserverDataPayload.BindingEntry binding) {
        ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
        if (metrics != null && metrics.available()) {
            return metrics.itemUsedBytes();
        }
        return binding.currentValue();
    }

    /** 计算节点总容量值 */
    private static long computeTotalValue(ObserverDataPayload.BindingEntry binding) {
        ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
        if (metrics != null && metrics.available()) {
            return metrics.itemTotalBytes();
        }
        return Math.max(1L, binding.capacity());
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

    private record GlobalItemInfo(
            String displayName,
            String iconSprite,
            String groupKey,
            long globalAmount,
            long globalDelta,
            long globalProductionRate,
            long globalConsumptionRate
    ) {
    }
}



