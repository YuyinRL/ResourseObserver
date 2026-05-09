package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import java.util.List;

/**
 * Crafting 域服务端权威快照 —— 由 {@link CraftingSnapshotBuilder#fromPayload} 装配。
 *
 * <p>F4.A 设计目标：把 {@code CraftingViewModelMapper} 中跨 AE2 网络的聚合逻辑
 * 抽离成三方共用的 record 契约，与现有 ViewModel 字段一一对应以便 F4.B 平滑迁移。
 */
public record CraftingSnapshot(
        boolean hasData,
        StorageSummarySnapshot storageSummary,
        List<JobSnapshot> jobs,
        List<CraftableSnapshot> craftables,
        int totalCraftables,
        String searchQuery,
        long timestampMs
) {

    public static CraftingSnapshot empty(String searchQuery) {
        return new CraftingSnapshot(false, StorageSummarySnapshot.empty(), List.of(), List.of(), 0,
                searchQuery == null ? "" : searchQuery, 0L);
    }

    /** 跨 AE2 网络聚合的 CPU 容量摘要 */
    public record StorageSummarySnapshot(
            int cpuCount,
            int busyCpuCount,
            long totalStorageBytes,
            int totalCoProcessors,
            boolean reliable
    ) {
        public static StorageSummarySnapshot empty() {
            return new StorageSummarySnapshot(0, 0, 0L, 0, true);
        }
    }

    /** 活跃 / 空闲 CPU 任务行 */
    public record JobSnapshot(
            String networkId,
            String cpuName,
            String outputItemId,
            String outputDisplayName,
            long total,
            long remaining,
            double progress,
            boolean busy,
            long storageBytes,
            int coProcessors,
            long elapsedMillis,
            String treeId
    ) {
    }

    /** 可合成物品行 */
    public record CraftableSnapshot(
            String networkId,
            String itemId,
            String displayName
    ) {
    }
}
