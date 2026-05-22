package com.yuyinrl.resourceobserver.client.ui.render.chart;

import com.yuyinrl.resourceobserver.client.ui.UiRect;

import java.util.List;

/**
 * 图表渲染结果 —— 包含各控制按钮的热区及 hover 点列表。
 *
 * @param windowToggle    时间窗口切换按钮热区
 * @param dataTypeToggle  数据类型切换按钮热区
 * @param pageToggle      页面切换按钮热区
 * @param lineModeButton  线条模式按钮热区
 * @param smoothingButton 平滑模式按钮热区
 * @param resetButton     重置选中按钮热区（无选中时为 null）
 * @param lineModeEnabled 线条模式按钮是否可用（库存页面下禁用）
 * @param plotRect        绘图区矩形
 * @param hoverPoints     可交互的数据点列表
 */
public record RenderResult(
        UiRect windowToggle,
        UiRect dataTypeToggle,
        UiRect pageToggle,
        UiRect lineModeButton,
        UiRect smoothingButton,
        UiRect resetButton,
        boolean lineModeEnabled,
        UiRect plotRect,
        List<ChartHoverPoint> hoverPoints
) {
}
