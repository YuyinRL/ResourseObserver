package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.world.block.entity.NetworkRef;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Storage 域 Snapshot 装配点 —— 把 {@link ObserverDataPayload} 折叠为不可变 {@link StorageSnapshot}。
 *
 * <p>F2.A 复刻 {@code StorageNetworkViewModelMapper.fromPayload} 的全部行为；后续消费者侧
 * （ModernUI / 新 Vanilla / Web）改吃此 Snapshot 后即可下线 mapper。
 *
 * <p>纯逻辑下沉到同包工具：{@link StorageAlertEvaluator} / {@link StorageBufferSmoother} /
 * {@link StorageUsageSegmenter} / {@link StorageKpiBuilder}；本类只做装配与本地化注入。
 */
public final class StorageSnapshotBuilder {

    /** FLUX_ENERGY 不属于存储域，由 Power 页面单独处理 */
    private static final String FLUX_ENERGY_TYPE = "FLUX_ENERGY";

    private StorageSnapshotBuilder() {
    }

    /**
     * 搜索匹配器抽象 —— 服务端 / 测试用 contains；客户端可注入 PinyinMatcher::matches。
     */
    @FunctionalInterface
    public interface SearchMatcher extends BiPredicate<String, String> {
        /** 默认匹配：忽略大小写包含 */
        SearchMatcher DEFAULT = (name, query) -> {
            if (name == null || query == null || query.isBlank()) return true;
            return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        };
    }

    /**
     * 主装配入口（默认 SearchMatcher）。
     */
    public static StorageSnapshot fromPayload(
            ObserverDataPayload payload,
            String selectedNodeId,
            boolean alertFilterActive,
            Map<String, double[]> bufferEma,
            String searchQuery,
            StorageLocalizer localizer
    ) {
        return fromPayload(payload, selectedNodeId, alertFilterActive, bufferEma, searchQuery,
                localizer, SearchMatcher.DEFAULT);
    }

    /** 主装配入口（注入 SearchMatcher 版本）。 */
    public static StorageSnapshot fromPayload(
            ObserverDataPayload payload,
            String selectedNodeId,
            boolean alertFilterActive,
            Map<String, double[]> bufferEma,
            String searchQuery,
            StorageLocalizer localizer,
            SearchMatcher matcher
    ) {
        StorageLocalizer loc = localizer == null ? StorageLocalizer.IDENTITY : localizer;
        SearchMatcher search = matcher == null ? SearchMatcher.DEFAULT : matcher;

        // 1. 仅保留存储类绑定
        List<ObserverDataPayload.BindingEntry> bindings = payload.bindings().stream()
                .filter(StorageSnapshotBuilder::isStorageBinding)
                .toList();
        boolean hasSelection = selectedNodeId != null && !selectedNodeId.isBlank();

        // 2. 节点列表
        List<StorageSnapshot.NodeSnapshot> nodes = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            double capacityRatio = computeCapacityRatio(binding);
            int itemCount = binding.itemDeltas() == null ? 0 : binding.itemDeltas().size();
            boolean selected = hasSelection && binding.networkId().equals(selectedNodeId);
            boolean statusAlert = capacityRatio >= StorageAlertEvaluator.CAPACITY_CRITICAL_RATIO;
            String statusKey = statusAlert
                    ? "screen.resourceobserver.storage.node.status.alert"
                    : "screen.resourceobserver.storage.node.status.healthy";
            long usedValue = computeUsedValue(binding);
            long totalValue = computeTotalValue(binding);
            nodes.add(new StorageSnapshot.NodeSnapshot(
                    binding.networkId(),
                    binding.displayName(),
                    binding.networkType(),
                    binding.iconSprite(),
                    itemCount,
                    capacityRatio,
                    selected,
                    SnapshotFormatters.formatCompact(usedValue),
                    SnapshotFormatters.formatCompact(totalValue),
                    statusKey,
                    statusAlert,
                    parseCoordinatesText(binding.networkId())
            ));
        }

        // 3. 全局物品汇总
        Map<String, GlobalItemInfo> globalMap = new LinkedHashMap<>();
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            if (binding.itemDeltas() == null) continue;
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                GlobalItemInfo info = globalMap.computeIfAbsent(item.itemId(),
                        id -> new GlobalItemInfo(item.displayName(), item.iconSprite(), item.groupKey(),
                                0L, 0.0, 0.0, 0.0));
                globalMap.put(item.itemId(), new GlobalItemInfo(
                        info.displayName(),
                        info.iconSprite(),
                        SnapshotFormatters.normalizeGroupKey(info.groupKey(), PlayerUiPrefsSavedData.GROUP_UNGROUPED),
                        saturatingAdd(info.globalAmount(), item.amount()),
                        info.globalDelta() + item.delta(),
                        info.globalProductionRate() + item.productionRate(),
                        info.globalConsumptionRate() + item.consumptionRate()
                ));
            }
        }

        // 4. 物品行（按选中节点 / 全局两条路径）
        double maxCapacityRatio = 0.0;
        for (ObserverDataPayload.BindingEntry b : bindings) {
            maxCapacityRatio = Math.max(maxCapacityRatio, computeCapacityRatio(b));
        }

        List<StorageSnapshot.ItemRowSnapshot> items = new ArrayList<>();
        Set<String> activeItemIds = new HashSet<>();
        if (hasSelection) {
            ObserverDataPayload.BindingEntry selectedBinding = findBinding(bindings, selectedNodeId);
            if (selectedBinding != null && selectedBinding.itemDeltas() != null) {
                double selectedRatio = computeCapacityRatio(selectedBinding);
                for (ObserverDataPayload.ItemDeltaEntry item : selectedBinding.itemDeltas()) {
                    GlobalItemInfo global = globalMap.getOrDefault(item.itemId(),
                            new GlobalItemInfo(item.displayName(), item.iconSprite(), item.groupKey(),
                                    item.amount(), item.delta(), item.productionRate(), item.consumptionRate()));
                    StorageSnapshot.AlertLevel alert = StorageAlertEvaluator.evaluate(
                            item.amount(), item.consumptionRate(), selectedRatio);
                    String localized = loc.localizeEntryName(item.entryType(), item.itemId(), item.displayName());
                    BufferDisplay buf = computeBufferDisplay(item.itemId(), item.consumptionRate(), item.amount(), bufferEma);
                    activeItemIds.add(item.itemId());
                    items.add(new StorageSnapshot.ItemRowSnapshot(
                            item.itemId(),
                            localized,
                            item.iconSprite(),
                            item.amount(),
                            global.globalAmount(),
                            item.delta(),
                            alert,
                            SnapshotFormatters.normalizeGroupKey(item.groupKey(), PlayerUiPrefsSavedData.GROUP_UNGROUPED),
                            item.consumptionRate(),
                            buf.text(),
                            buf.ratio()
                    ));
                }
            }
        } else {
            for (Map.Entry<String, GlobalItemInfo> entry : globalMap.entrySet()) {
                GlobalItemInfo info = entry.getValue();
                StorageSnapshot.AlertLevel alert = StorageAlertEvaluator.evaluateGlobal(
                        info.globalAmount(), info.globalConsumptionRate(), maxCapacityRatio);
                String localized = loc.localizeEntryName(ObserverDataPayload.EntryType.ITEM, entry.getKey(), info.displayName());
                BufferDisplay buf = computeBufferDisplay(entry.getKey(), info.globalConsumptionRate(),
                        info.globalAmount(), bufferEma);
                activeItemIds.add(entry.getKey());
                items.add(new StorageSnapshot.ItemRowSnapshot(
                        entry.getKey(),
                        localized,
                        info.iconSprite(),
                        info.globalAmount(),
                        info.globalAmount(),
                        info.globalDelta(),
                        alert,
                        SnapshotFormatters.normalizeGroupKey(info.groupKey(), PlayerUiPrefsSavedData.GROUP_UNGROUPED),
                        info.globalConsumptionRate(),
                        buf.text(),
                        buf.ratio()
                ));
            }
        }

        // EMA 清理
        bufferEma.keySet().retainAll(activeItemIds);

        // 5. 过滤 + 搜索 + 排序
        if (alertFilterActive) {
            items = items.stream()
                    .filter(row -> row.alertLevel() != StorageSnapshot.AlertLevel.GREEN)
                    .toList();
        }
        if (searchQuery != null && !searchQuery.isBlank()) {
            String q = searchQuery.trim();
            items = items.stream()
                    .filter(row -> search.test(row.displayName(), q))
                    .toList();
        }
        items = items.stream()
                .sorted(Comparator.comparing(StorageSnapshot.ItemRowSnapshot::displayName))
                .toList();

        // 6. 分段 + KPI
        List<StorageSnapshot.UsageSegmentSnapshot> usageSegments = StorageUsageSegmenter.build(items);

        long totalAmount = 0L;
        for (StorageSnapshot.ItemRowSnapshot row : items) {
            totalAmount = saturatingAdd(totalAmount, Math.max(0L, row.localAmount()));
        }
        double fillRate = computeAverageFillRate(bindings, hasSelection ? selectedNodeId : null);
        List<StorageSnapshot.KpiSnapshot> kpis = StorageKpiBuilder.build(totalAmount, items.size(), fillRate);

        // 7. 关键指标
        int criticalItemCount = 0;
        for (StorageSnapshot.ItemRowSnapshot item : items) {
            if (item.alertLevel() != StorageSnapshot.AlertLevel.GREEN) {
                criticalItemCount++;
            }
        }

        return new StorageSnapshot(
                nodes,
                selectedNodeId,
                alertFilterActive,
                searchQuery == null ? "" : searchQuery,
                kpis,
                items,
                usageSegments,
                items.size(),
                criticalItemCount,
                fillRate
        );
    }

    // ========== 私有辅助 ==========

    private static boolean isStorageBinding(ObserverDataPayload.BindingEntry binding) {
        String type = binding.networkType();
        if (type == null || type.isBlank()) return true;
        return !FLUX_ENERGY_TYPE.equalsIgnoreCase(type);
    }

    private static ObserverDataPayload.BindingEntry findBinding(
            List<ObserverDataPayload.BindingEntry> bindings, String networkId) {
        for (ObserverDataPayload.BindingEntry b : bindings) {
            if (b.networkId().equals(networkId)) return b;
        }
        return null;
    }

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

    private static long computeUsedValue(ObserverDataPayload.BindingEntry binding) {
        ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
        if (metrics != null && metrics.available()) {
            return metrics.itemUsedBytes();
        }
        return binding.currentValue();
    }

    private static long computeTotalValue(ObserverDataPayload.BindingEntry binding) {
        ObserverDataPayload.CellCapacityMetrics metrics = binding.cellCapacityMetrics();
        if (metrics != null && metrics.available()) {
            return metrics.itemTotalBytes();
        }
        return Math.max(1L, binding.capacity());
    }

    private static double computeAverageFillRate(
            List<ObserverDataPayload.BindingEntry> bindings, String selectedNodeId) {
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            ObserverDataPayload.BindingEntry binding = findBinding(bindings, selectedNodeId);
            return binding != null ? computeCapacityRatio(binding) : 0.0;
        }
        if (bindings.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (ObserverDataPayload.BindingEntry b : bindings) {
            sum += computeCapacityRatio(b);
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static String parseCoordinatesText(String networkId) {
        BlockPos pos = NetworkRef.extractPos(networkId);
        if (pos == null) return null;
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static BufferDisplay computeBufferDisplay(
            String itemId, double burnRate, long amount, Map<String, double[]> bufferEma) {
        if (burnRate <= 0 || amount <= 0) {
            bufferEma.remove(itemId);
            return new BufferDisplay("∞", 1.0);
        }
        double anchorSec = StorageBufferSmoother.smoothRateAndUpdateAnchor(itemId, burnRate, amount, bufferEma);
        return new BufferDisplay(
                SnapshotFormatters.formatBufferSeconds(Math.round(anchorSec)),
                SnapshotFormatters.bufferRatioFromSeconds(anchorSec)
        );
    }

    private record GlobalItemInfo(
            String displayName,
            String iconSprite,
            String groupKey,
            long globalAmount,
            double globalDelta,
            double globalProductionRate,
            double globalConsumptionRate
    ) {
    }

    private record BufferDisplay(String text, double ratio) {
    }
}
