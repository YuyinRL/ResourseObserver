package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;

import java.util.List;

/**
 * 迷你甜甜圈：单一负载比例展示。
 *
 * <p>相比 {@link DonutChart}：
 * <ul>
 *   <li>内径比例更大（0.58→默认空 hole 视觉更轻）</li>
 *   <li>关闭 tooltip</li>
 *   <li>中心仅显示 1 行百分比</li>
 * </ul>
 */
public class MiniDonut extends DonutChart {

    public MiniDonut() {
        super();
        setInnerRatio(0.58f);
        setShowTooltip(false);
    }

    /**
     * 一键设置：单段 + headroom + 中心文本。
     *
     * @param percent   负载占比（0..100）
     * @param color     段颜色
     * @param centerText 中心文字（通常等于 percent% 串）
     */
    public MiniDonut setSinglePercent(double percent, int color, String centerText) {
        double clamped = Math.max(0, Math.min(100, percent));
        setData(List.of(new Segment("Load", clamped, 0, color)),
                100.0 - clamped, centerText, "",
                VanillaTheme.COLOR_TEXT_PRIMARY);
        return this;
    }
}
