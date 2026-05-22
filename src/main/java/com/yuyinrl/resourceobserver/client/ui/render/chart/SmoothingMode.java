package com.yuyinrl.resourceobserver.client.ui.render.chart;

/**
 * 平滑模式枚举。
 * <ul>
 *   <li>{@link #SMOOTH}：EMA 指数移动平均平滑</li>
 *   <li>{@link #RAW}：原始数据直接绘制</li>
 * </ul>
 */
public enum SmoothingMode {
    SMOOTH,
    RAW;

    /** 切换到下一个模式（循环）。 */
    public SmoothingMode next() {
        return this == SMOOTH ? RAW : SMOOTH;
    }
}
