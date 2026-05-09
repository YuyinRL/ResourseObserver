package com.yuyinrl.resourceobserver.client.screen.widget;

/**
 * 矩形包围盒，用于 widget 布局与命中测试。所有坐标均为屏幕像素（Screen 坐标系）。
 *
 * <p>不可变值类型，便于在布局阶段共享与传递。</p>
 *
 * @param x 左上角 X
 * @param y 左上角 Y
 * @param width 宽
 * @param height 高
 */
public record Rect(int x, int y, int width, int height) {

    /** 空矩形常量。 */
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    /** 工厂：从左上 + 宽高构造。 */
    public static Rect of(int x, int y, int w, int h) {
        return new Rect(x, y, w, h);
    }

    public int right() { return x + width; }
    public int bottom() { return y + height; }

    /** 是否包含指定屏幕坐标。 */
    public boolean contains(double mx, double my) {
        return mx >= x && mx < right() && my >= y && my < bottom();
    }

    /** 内缩：四向收缩 padding 像素，得到内容区。 */
    public Rect shrink(int padding) {
        return shrink(padding, padding, padding, padding);
    }

    /** 按四向独立内缩。 */
    public Rect shrink(int top, int right, int bottom, int left) {
        return new Rect(x + left, y + top,
                Math.max(0, width - left - right),
                Math.max(0, height - top - bottom));
    }

    /** 偏移到新原点。 */
    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }
}
