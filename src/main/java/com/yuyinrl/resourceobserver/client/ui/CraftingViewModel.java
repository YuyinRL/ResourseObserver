package com.yuyinrl.resourceobserver.client.ui;

import java.util.List;

/**
 * 合成子页面 ViewModel —— 聚合所有 AE2_ITEMS 绑定的合成数据，供 Storage Network 页内
 * Crafting 子 Tab 直接渲染。
 *
 * @param hasData           是否至少存在一个 AE2 合成网络（无则显示空态）
 * @param storageSummary    所有 CPU 汇总指标
 * @param jobs              所有活跃任务（按繁忙状态 + CPU 序号排序）
 * @param craftables        可合成物品（已应用搜索过滤 + 稳定排序）
 * @param totalCraftables   过滤前的 craftables 总数
 * @param searchQuery       客户端当前的搜索词
 */
public record CraftingViewModel(
        boolean hasData,
        StorageSummary storageSummary,
        List<JobRow> jobs,
        List<CraftableRow> craftables,
        int totalCraftables,
        String searchQuery
) {
    public static CraftingViewModel empty(String searchQuery) {
        return new CraftingViewModel(false, StorageSummary.empty(), List.of(), List.of(), 0,
                searchQuery == null ? "" : searchQuery);
    }

    /**
     * 合成存储容量汇总（跨所有网络聚合）。
     */
    public record StorageSummary(
            int cpuCount,
            int busyCpuCount,
            long totalStorageBytes,
            int totalCoProcessors,
            boolean reliable
    ) {
        public static StorageSummary empty() {
            return new StorageSummary(0, 0, 0L, 0, true);
        }
    }

    /**
     * 活跃任务行。
     *
     * @param networkId         AE2 网络 ID
     * @param cpuName           CPU 显示名
     * @param outputItemId      输出物品 ID（空表示 CPU 空闲）
     * @param outputDisplayName 输出物品显示名
     * @param total             任务初始总数
     * @param remaining         剩余数量
     * @param progress          0..1 之间的进度（基于 remaining / total；total=0 返回 0）
     * @param busy              CPU 是否忙
     */
    public record JobRow(
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

    /** 可合成物品行。 */
    public record CraftableRow(
            String networkId,
            String itemId,
            String displayName
    ) {
    }
}
