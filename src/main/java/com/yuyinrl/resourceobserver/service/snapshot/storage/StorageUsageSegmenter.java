package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 使用率分段构建器 —— 把物品行按 groupKey 聚合成 {@link StorageSnapshot.UsageSegmentSnapshot} 列表。
 *
 * <p>F2.A 从 {@code StorageNetworkViewModelMapper.buildUsageSegments} 抽出来的纯逻辑：
 * <ul>
 *     <li>按 groupKey 求和 localAmount（负数视为 0）</li>
 *     <li>过滤百分比 &lt; 0.5% 的细碎分段</li>
 *     <li>按百分比降序排列</li>
 *     <li>分组到颜色槽位（{@link StorageSnapshot.GroupColorSlot}）的映射在此</li>
 * </ul>
 */
public final class StorageUsageSegmenter {

    /** 默认分组键（与 PlayerUiPrefsSavedData.GROUP_UNGROUPED 对齐） */
    public static final String GROUP_UNGROUPED = "ungrouped";

    /** 显示阈值：百分比 &lt; 此值的分段不参与展示 */
    public static final double MIN_PERCENT_THRESHOLD = 0.5;

    private StorageUsageSegmenter() {
    }

    /**
     * 由物品行构建分段列表。
     *
     * @param items 物品行（snapshot 形态）
     * @return 分段列表（按百分比降序）；items 为空或总量 ≤ 0 时返回 {@link List#of()}
     */
    public static List<StorageSnapshot.UsageSegmentSnapshot> build(List<StorageSnapshot.ItemRowSnapshot> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<String, Long> groupTotals = new LinkedHashMap<>();
        long grandTotal = 0L;
        for (StorageSnapshot.ItemRowSnapshot item : items) {
            String groupKey = SnapshotFormatters.normalizeGroupKey(item.groupKey(), GROUP_UNGROUPED);
            long add = Math.max(0L, item.localAmount());
            groupTotals.merge(groupKey, add, Long::sum);
            grandTotal += add;
        }
        if (grandTotal <= 0L) {
            return List.of();
        }

        List<StorageSnapshot.UsageSegmentSnapshot> segments = new ArrayList<>();
        for (Map.Entry<String, Long> entry : groupTotals.entrySet()) {
            double pct = (entry.getValue() * 100.0) / grandTotal;
            if (pct < MIN_PERCENT_THRESHOLD) {
                continue;
            }
            String groupKey = entry.getKey();
            segments.add(new StorageSnapshot.UsageSegmentSnapshot(
                    groupKey,
                    groupTranslationKey(groupKey),
                    groupKey,
                    pct,
                    groupColorSlot(groupKey)
            ));
        }
        segments.sort(Comparator.comparingDouble(StorageSnapshot.UsageSegmentSnapshot::percentage).reversed());
        return segments;
    }

    /** 分组键 → 翻译键映射（与原 mapper 对齐） */
    public static String groupTranslationKey(String groupKey) {
        return switch (lower(groupKey)) {
            case "raw" -> "screen.resourceobserver.storage.usage.raw";
            case "intermediate" -> "screen.resourceobserver.storage.usage.intermediate";
            case "finished" -> "screen.resourceobserver.storage.usage.finished";
            default -> "screen.resourceobserver.storage.usage.other";
        };
    }

    /** 分组键 → 颜色槽位映射 */
    public static StorageSnapshot.GroupColorSlot groupColorSlot(String groupKey) {
        return switch (lower(groupKey)) {
            case "raw" -> StorageSnapshot.GroupColorSlot.RAW;
            case "intermediate" -> StorageSnapshot.GroupColorSlot.INTERMEDIATE;
            case "finished" -> StorageSnapshot.GroupColorSlot.FINISHED;
            default -> StorageSnapshot.GroupColorSlot.OTHER;
        };
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
