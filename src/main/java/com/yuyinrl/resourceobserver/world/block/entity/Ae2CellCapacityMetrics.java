package com.yuyinrl.resourceobserver.world.block.entity;

/**
 * AE2 网络的存储单元容量度量 —— 由 {@link Ae2CellProber} 探测产出。
 *
 * <p>包含物品/流体两通道的字节、种类、单位（基础单位 = 1 物品 / 1mB），以及外部存储桥接器汇总。
 * {@link #reliable} / {@link #available} / {@link #externalReliable} / {@link #externalAvailable}
 * 旗标用于区分 "无数据" 与 "数据不可信"。
 */
public record Ae2CellCapacityMetrics(
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
        String scope,
        boolean reliable,
        boolean available,
        boolean externalReliable,
        boolean externalAvailable
) {
    public static Ae2CellCapacityMetrics unavailable() {
        return new Ae2CellCapacityMetrics(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                Ae2CellProber.AE2_CAPACITY_SCOPE_CELLS_ONLY,
                false, false, false, false
        );
    }
}
