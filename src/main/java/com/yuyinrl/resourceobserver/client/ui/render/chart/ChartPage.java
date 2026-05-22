package com.yuyinrl.resourceobserver.client.ui.render.chart;

/**
 * 图表页面类型枚举。
 * <ul>
 *   <li>{@link #THROUGHPUT}：吞吐量视图（生产/消耗/净流量）</li>
 *   <li>{@link #STOCK}：库存视图（库存量变化）</li>
 * </ul>
 */
public enum ChartPage {
    THROUGHPUT,
    STOCK;

    /** 切换到下一个页面（循环）。 */
    public ChartPage next() {
        return this == THROUGHPUT ? STOCK : THROUGHPUT;
    }
}
