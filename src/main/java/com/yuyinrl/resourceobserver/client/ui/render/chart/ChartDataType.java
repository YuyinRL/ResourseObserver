package com.yuyinrl.resourceobserver.client.ui.render.chart;

/**
 * 图表数据类型枚举。
 * <ul>
 *   <li>{@link #ITEMS}：物品吞吐/库存数据（AE2 网络）</li>
 *   <li>{@link #ENERGY}：电量吞吐/储量数据（Flux 能量网络）</li>
 * </ul>
 */
public enum ChartDataType {
    ITEMS,
    ENERGY;

    /** 切换到下一个数据类型（循环）。 */
    public ChartDataType next() {
        return this == ITEMS ? ENERGY : ITEMS;
    }
}
