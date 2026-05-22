package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 水平进度条，支持单值进度或多段堆叠模式。
 */
public class ProgressBar extends BaseWidget {

    /** 分段进度单元，ratio 为 0..1 的相对宽度。 */
    public record Segment(double ratio, int color) {
    }

    private double progress;
    private int fillColor = VanillaTheme.COLOR_STATUS_INFO;
    private int backgroundColor = 0xFF_1B_1B_1B;
    private int borderColor = VanillaTheme.COLOR_DIVIDER;
    private List<Segment> segments = List.of();

    public ProgressBar setProgress(double progress) {
        this.progress = clamp(progress);
        this.segments = List.of();
        return this;
    }

    public ProgressBar setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public ProgressBar setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public ProgressBar setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    public ProgressBar setSegments(List<Segment> segments) {
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        g.fill(b.x(), b.y(), b.right(), b.bottom(), borderColor);
        Rect inner = b.shrink(1);
        g.fill(inner.x(), inner.y(), inner.right(), inner.bottom(), backgroundColor);
        if (!segments.isEmpty()) {
            renderSegments(g, inner);
            return;
        }
        int fillW = (int) Math.round(inner.width() * progress);
        if (fillW > 0) {
            g.fill(inner.x(), inner.y(), inner.x() + fillW, inner.bottom(), fillColor);
        }
    }

    private void renderSegments(GuiGraphics g, Rect inner) {
        int x = inner.x();
        int remaining = inner.width();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            int w = i == segments.size() - 1
                    ? remaining
                    : Math.min(remaining, (int) Math.round(inner.width() * clamp(segment.ratio())));
            if (w > 0) {
                g.fill(x, inner.y(), x + w, inner.bottom(), segment.color());
            }
            x += w;
            remaining -= w;
            if (remaining <= 0) break;
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
