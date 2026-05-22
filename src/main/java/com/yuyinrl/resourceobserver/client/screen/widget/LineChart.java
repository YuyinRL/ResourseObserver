package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Vanilla V2 折线图控件。
 */
public class LineChart extends BaseWidget {

    /** 图表点，x 可传序号或时间桶，y 为待绘制值。 */
    public record DataPoint(double x, double y) {
    }

    /** 单条折线配置。 */
    public record Series(List<DataPoint> points, int lineColor, int fillColor, boolean filled) {
        public Series {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    private List<Series> series = List.of();
    private boolean showAxis = true;
    private int backgroundColor = 0xFF_1B_1B_1B;
    private int borderColor = VanillaTheme.COLOR_DIVIDER;
    private int axisColor = 0x55_FF_FF_FF;

    public LineChart setSeries(List<Series> series) {
        this.series = series == null ? List.of() : List.copyOf(series);
        return this;
    }

    public LineChart setShowAxis(boolean showAxis) {
        this.showAxis = showAxis;
        return this;
    }

    public LineChart setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public LineChart setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        if (b.width() <= 2 || b.height() <= 2) return;

        g.fill(b.x(), b.y(), b.right(), b.bottom(), borderColor);
        Rect plot = b.shrink(1);
        g.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), backgroundColor);

        Bounds dataBounds = computeBounds();
        if (dataBounds.empty()) {
            renderEmptyAxis(g, plot);
            return;
        }
        if (showAxis) renderAxis(g, plot, dataBounds);
        for (Series item : series) {
            renderSeries(g, plot, dataBounds, item);
        }
    }

    private void renderEmptyAxis(GuiGraphics g, Rect plot) {
        if (!showAxis) return;
        int y = plot.y() + plot.height() / 2;
        g.fill(plot.x(), y, plot.right(), y + 1, axisColor);
    }

    private void renderAxis(GuiGraphics g, Rect plot, Bounds dataBounds) {
        int zeroY = toY(plot, dataBounds, 0.0d);
        if (zeroY >= plot.y() && zeroY < plot.bottom()) {
            g.fill(plot.x(), zeroY, plot.right(), zeroY + 1, axisColor);
        }
        g.fill(plot.x(), plot.y(), plot.x() + 1, plot.bottom(), axisColor);
    }

    private void renderSeries(GuiGraphics g, Rect plot, Bounds dataBounds, Series item) {
        List<DataPoint> points = item.points();
        if (points.isEmpty()) return;

        int zeroY = toY(plot, dataBounds, 0.0d);
        int previousX = toX(plot, dataBounds, points.get(0).x());
        int previousY = toY(plot, dataBounds, points.get(0).y());
        drawPoint(g, previousX, previousY, item.lineColor());
        if (item.filled()) drawFill(g, previousX, previousY, zeroY, item.fillColor());

        for (int i = 1; i < points.size(); i++) {
            DataPoint point = points.get(i);
            int x = toX(plot, dataBounds, point.x());
            int y = toY(plot, dataBounds, point.y());
            if (item.filled()) fillBetween(g, previousX, x, previousY, y, zeroY, item.fillColor());
            drawSegment(g, previousX, previousY, x, y, item.lineColor());
            previousX = x;
            previousY = y;
        }
    }

    private static void fillBetween(GuiGraphics g, int x0, int x1, int y0, int y1, int zeroY, int color) {
        if (x1 < x0) {
            fillBetween(g, x1, x0, y1, y0, zeroY, color);
            return;
        }
        int width = Math.max(1, x1 - x0);
        for (int x = x0; x <= x1; x++) {
            double ratio = width == 0 ? 0.0d : (x - x0) / (double) width;
            int y = (int) Math.round(y0 + (y1 - y0) * ratio);
            drawFill(g, x, y, zeroY, color);
        }
    }

    private static void drawFill(GuiGraphics g, int x, int y, int zeroY, int color) {
        int top = Math.min(y, zeroY);
        int bottom = Math.max(y, zeroY);
        if (bottom <= top) bottom = top + 1;
        g.fill(x, top, x + 1, bottom, color);
    }

    private static void drawSegment(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            drawPoint(g, x0, y0, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double ratio = i / (double) steps;
            int x = (int) Math.round(x0 + dx * ratio);
            int y = (int) Math.round(y0 + dy * ratio);
            drawPoint(g, x, y, color);
        }
    }

    private static void drawPoint(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 2, y + 2, color);
    }

    private Bounds computeBounds() {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = 0.0d;
        double maxY = 0.0d;
        boolean any = false;
        for (Series item : series) {
            for (DataPoint point : item.points()) {
                if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) continue;
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
                any = true;
            }
        }
        if (!any) return Bounds.emptyBounds();
        if (Math.abs(maxX - minX) < 1.0E-6d) maxX = minX + 1.0d;
        if (Math.abs(maxY - minY) < 1.0E-6d) {
            maxY += 1.0d;
            minY -= 1.0d;
        }
        return new Bounds(minX, maxX, minY, maxY, false);
    }

    private static int toX(Rect plot, Bounds bounds, double x) {
        double ratio = (x - bounds.minX()) / (bounds.maxX() - bounds.minX());
        return plot.x() + (int) Math.round(clamp(ratio) * Math.max(0, plot.width() - 2));
    }

    private static int toY(Rect plot, Bounds bounds, double y) {
        double ratio = (y - bounds.minY()) / (bounds.maxY() - bounds.minY());
        return plot.bottom() - 2 - (int) Math.round(clamp(ratio) * Math.max(0, plot.height() - 2));
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0d;
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record Bounds(double minX, double maxX, double minY, double maxY, boolean empty) {
        private static Bounds emptyBounds() {
            return new Bounds(0.0d, 1.0d, 0.0d, 1.0d, true);
        }
    }
}
