package com.yuyinrl.resourceobserver.service.snapshot.crafting;

/**
 * StorageSummary 增量聚合器 —— 跨 AE2 网络逐项累加。
 *
 * <p>{@code reliable} 语义：只要任一网络 reliable=false，结果即 false。
 */
public final class CraftingSummaryAggregator {

    private int cpuCount = 0;
    private int busyCpuCount = 0;
    private long totalStorageBytes = 0L;
    private int totalCoProcessors = 0;
    private boolean reliable = true;

    public void accumulate(int cpuCount, int busyCpuCount, long totalStorageBytes,
                           int totalCoProcessors, boolean reliable) {
        this.cpuCount += cpuCount;
        this.busyCpuCount += busyCpuCount;
        this.totalStorageBytes += totalStorageBytes;
        this.totalCoProcessors += totalCoProcessors;
        if (!reliable) this.reliable = false;
    }

    public CraftingSnapshot.StorageSummarySnapshot build() {
        return new CraftingSnapshot.StorageSummarySnapshot(
                cpuCount, busyCpuCount, totalStorageBytes, totalCoProcessors, reliable);
    }
}
