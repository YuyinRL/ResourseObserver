package com.yuyinrl.resourceobserver.client.ui.render.chart;

/**
 * 图表 hover 点 —— 记录数据点在屏幕上的位置以便鼠标交互检测。
 *
 * @param seriesType 所属数据系列
 * @param x          屏幕 X 坐标
 * @param y          屏幕 Y 坐标
 * @param slotIndex  数据序列中的索引
 * @param value      数据值（用于 tooltip 显示）
 */
public record ChartHoverPoint(
        ChartSeriesType seriesType,
        double x,
        double y,
        int slotIndex,
        double value
) {
}
