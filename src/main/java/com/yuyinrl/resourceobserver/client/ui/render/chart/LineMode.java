package com.yuyinrl.resourceobserver.client.ui.render.chart;

/**
 * 线条显示模式枚举。
 * <ul>
 *   <li>{@link #ALL}：显示全部曲线</li>
 *   <li>{@link #PRODUCTION}：仅显示生产曲线</li>
 *   <li>{@link #CONSUMPTION}：仅显示消耗曲线</li>
 *   <li>{@link #NET}：仅显示净流量曲线</li>
 * </ul>
 */
public enum LineMode {
    ALL,
    PRODUCTION,
    CONSUMPTION,
    NET;

    /** 切换到下一个模式（循环）。 */
    public LineMode next() {
        return switch (this) {
            case ALL -> PRODUCTION;
            case PRODUCTION -> CONSUMPTION;
            case CONSUMPTION -> NET;
            case NET -> ALL;
        };
    }

    public boolean showProduction() {
        return this == ALL || this == PRODUCTION;
    }

    public boolean showConsumption() {
        return this == ALL || this == CONSUMPTION;
    }

    public boolean showNet() {
        return this == ALL || this == NET;
    }
}
