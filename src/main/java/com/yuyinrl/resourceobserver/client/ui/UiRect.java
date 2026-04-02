package com.yuyinrl.resourceobserver.client.ui;

/**
 * 基础矩形工具类 —— 表示 UI 中的一个矩形区域。
 * 提供便捷方法计算右边界、下边界、包含判定和内缩。
 *
 * @param x      左上角 X 坐标
 * @param y      左上角 Y 坐标
 * @param width  宽度
 * @param height 高度
 */
public record UiRect(int x, int y, int width, int height) {
    /** 右边界 X 坐标 */
    public int right() {
        return x + width;
    }

    /** 下边界 Y 坐标 */
    public int bottom() {
        return y + height;
    }

    /** 判断点 (px, py) 是否在矩形内 */
    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    /** 返回向内收缩指定像素后的新矩形 */
    public UiRect inset(int padding) {
        return new UiRect(x + padding, y + padding, Math.max(0, width - 2 * padding), Math.max(0, height - 2 * padding));
    }
}
