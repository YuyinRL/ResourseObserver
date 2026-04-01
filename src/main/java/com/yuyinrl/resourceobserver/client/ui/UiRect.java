package com.yuyinrl.resourceobserver.client.ui;

public record UiRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public UiRect inset(int padding) {
        return new UiRect(x + padding, y + padding, Math.max(0, width - 2 * padding), Math.max(0, height - 2 * padding));
    }
}
