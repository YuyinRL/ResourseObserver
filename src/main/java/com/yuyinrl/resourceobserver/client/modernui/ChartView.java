package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.ui.render.ChartMath;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartDataType;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartHoverPoint;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartPage;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartSeriesType;
import com.yuyinrl.resourceobserver.client.ui.render.chart.LineMode;
import com.yuyinrl.resourceobserver.client.ui.render.chart.RenderResult;
import com.yuyinrl.resourceobserver.client.ui.render.chart.SmoothingMode;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Modern UI 自定义 View —— 绘制资源流量/库存趋势折线图。
 * <p>
 * 使用 {@link ChartMath} 进行数据预计算（Hermite 插值、EMA 平滑等），
 * 通过 Modern UI 的 {@link Canvas}/{@link Paint} API 绘制网格、折线、零轴和数据点。
 * 支持鼠标悬停显示最近数据点的 tooltip。
 */
final class ChartView extends View {

    private static final float CURVE_SUBDIVISION = 1.0f;
    private static final int SAMPLE_SCALE = 1;
    private static final float LINE_WIDTH = 2.0f;
    private static final float ZERO_LINE_WIDTH = 1.0f;
    private static final float GRID_LINE_WIDTH = 1.0f;
    private static final float HOVER_DOT_RADIUS = 4.0f;
    private static final int ZERO_LINE_COLOR = 0xBBD5F3;
    private static final float ZERO_LINE_ALPHA = 0.50f;

    // Tooltip styling
    private static final int TOOLTIP_BG = 0xE6101C2E;
    private static final int TOOLTIP_BORDER = 0xFF3A5478;
    private static final float TOOLTIP_RADIUS = 6.0f;
    private static final float TOOLTIP_PADDING = 8.0f;
    private static final float TOOLTIP_FONT_SIZE = 11.0f;
    private static final float TOOLTIP_LINE_HEIGHT = 15.0f;
    private static final float TOOLTIP_SWATCH_SIZE = 8.0f;
    private static final float HOVER_SNAP_DISTANCE = 60.0f;

    private List<OverviewViewModel.FlowPoint> series;
    private ChartPage chartPage = ChartPage.THROUGHPUT;
    private LineMode lineMode = LineMode.ALL;
    private SmoothingMode smoothingMode = SmoothingMode.SMOOTH;

    private ChartMath.PreparedChart preparedChart;
    private long lastFingerprint;
    private int lastWidth;
    private int lastHeight;

    // Hover state
    private float hoverX = -1;
    private float hoverY = -1;
    private boolean hovering = false;
    /** All hover points sharing the nearest slotIndex (one per visible series) */
    private List<ChartMath.HoverPoint> activeHoverPoints = List.of();

    // Reusable text paint for tooltip
    private final TextPaint tooltipPaint = new TextPaint();

    ChartView(Context context) {
        super(context);
        tooltipPaint.setColor(UiThemeTokens.TEXT);
        tooltipPaint.setTextSize(TOOLTIP_FONT_SIZE);
    }

    /**
     * 更新图表数据和配置。仅在数据或配置变化时重新计算并重绘。
     */
    void setChartData(
            List<OverviewViewModel.FlowPoint> series,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode
    ) {
        this.series = series;
        this.chartPage = page;
        this.lineMode = lineMode;
        this.smoothingMode = smoothingMode;
        // Force recalculation
        this.lastFingerprint = 0;
        this.lastWidth = 0;
        this.lastHeight = 0;
        this.preparedChart = null;
        rebuildIfNeeded();
        // Preserve tooltip: recalculate hover point if mouse is still over the chart
        if (hovering && hoverX >= 0) {
            updateActiveHoverPoint();
        } else {
            this.activeHoverPoints = List.of();
        }
        invalidate();
    }

    private void rebuildIfNeeded() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || series == null || series.isEmpty()) {
            preparedChart = null;
            return;
        }
        long fp = ChartMath.seriesFingerprint(series);
        if (preparedChart != null && fp == lastFingerprint && w == lastWidth && h == lastHeight) {
            return;
        }
        lastFingerprint = fp;
        lastWidth = w;
        lastHeight = h;
        preparedChart = ChartMath.prepareChart(
                w, h, series, chartPage, lineMode, smoothingMode,
                CURVE_SUBDIVISION, SAMPLE_SCALE
        );
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        preparedChart = null;
        if (series != null && !series.isEmpty()) {
            rebuildIfNeeded();
        }
    }

    // ===================== Hover =====================

    @Override
    public boolean onHoverEvent(@Nonnull MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            hoverX = event.getX();
            hoverY = event.getY();
            hovering = true;
            updateActiveHoverPoint();
            invalidate();
            return true;
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            hovering = false;
            hoverX = -1;
            hoverY = -1;
            activeHoverPoints = List.of();
            invalidate();
            return true;
        }
        return super.onHoverEvent(event);
    }

    /**
     * 按最近 X 坐标查找同一 slotIndex 上所有系列的悬停点。
     * 只按水平距离匹配（垂直参考线语义），然后收集该时间槽上的全部系列。
     */
    private void updateActiveHoverPoint() {
        if (preparedChart == null || preparedChart.hoverPoints().isEmpty()) {
            activeHoverPoints = List.of();
            return;
        }
        // 1. 找到 X 距离最近的 hover point
        ChartMath.HoverPoint nearest = null;
        double bestXDist = Double.MAX_VALUE;
        for (ChartMath.HoverPoint hp : preparedChart.hoverPoints()) {
            double dx = Math.abs(hp.x() - hoverX);
            if (dx < bestXDist) {
                bestXDist = dx;
                nearest = hp;
            }
        }
        if (nearest == null || bestXDist > HOVER_SNAP_DISTANCE) {
            activeHoverPoints = List.of();
            return;
        }
        // 2. 收集同一 slotIndex 的所有系列悬停点
        int targetSlot = nearest.slotIndex();
        List<ChartMath.HoverPoint> collected = new java.util.ArrayList<>();
        for (ChartMath.HoverPoint hp : preparedChart.hoverPoints()) {
            if (hp.slotIndex() == targetSlot) {
                collected.add(hp);
            }
        }
        activeHoverPoints = collected;
    }

    // ===================== Drawing =====================

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        drawGrid(canvas, w, h);

        if (preparedChart == null) {
            rebuildIfNeeded();
        }
        if (preparedChart == null || !preparedChart.hasVisibleContent()) return;

        drawZeroAxis(canvas, preparedChart, w);
        drawSeries(canvas, preparedChart);

        // Hover crosshair + tooltip
        if (hovering && !activeHoverPoints.isEmpty()) {
            drawHoverIndicator(canvas, activeHoverPoints, w, h);
        }
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        Paint paint = Paint.obtain();
        paint.setStyle(Paint.FILL);

        // 水平网格线
        int hs = Math.max(8, h / 5);
        paint.setColor(0x223E5C84);
        for (int y = hs; y < h - 1; y += hs) {
            canvas.drawRect(1, y, w - 1, y + 1, paint);
        }

        // 垂直网格线
        paint.setColor(0x152F4668);
        for (int x = Math.max(12, w / 10); x < w - 1; x += Math.max(12, w / 10)) {
            canvas.drawRect(x, 1, x + 1, h - 1, paint);
        }

        paint.recycle();
    }

    private void drawZeroAxis(Canvas canvas, ChartMath.PreparedChart chart, int w) {
        if (chart.zeroAxisY() == null) return;
        float zy = chart.zeroAxisY().floatValue();

        Paint paint = Paint.obtain();
        paint.setColor(ZERO_LINE_COLOR);
        paint.setAlpha(Math.round(ZERO_LINE_ALPHA * 255));
        canvas.drawLine(0, zy, w, zy, ZERO_LINE_WIDTH, paint);
        paint.recycle();
    }

    private void drawSeries(Canvas canvas, ChartMath.PreparedChart chart) {
        Paint paint = Paint.obtain();
        paint.setStyle(Paint.FILL);

        for (ChartMath.PreparedSeries ps : chart.series()) {
            int color = ps.color();
            float lw = LINE_WIDTH;
            paint.setColor(color);
            paint.setAlpha(220);

            for (List<ChartMath.Point> segment : ps.segments()) {
                for (int i = 0; i + 1 < segment.size(); i++) {
                    ChartMath.Point a = segment.get(i);
                    ChartMath.Point b = segment.get(i + 1);
                    canvas.drawLine(
                            (float) a.x(), (float) a.y(),
                            (float) b.x(), (float) b.y(),
                            lw, paint
                    );
                }
            }
        }

        paint.recycle();
    }

    /**
     * 绘制多系列悬停指示器 —— 垂直参考线 + 每条曲线上的圆点 + 合并 tooltip。
     */
    private void drawHoverIndicator(Canvas canvas, List<ChartMath.HoverPoint> hps, int w, int h) {
        if (hps.isEmpty()) return;
        float refX = (float) hps.get(0).x();

        Paint paint = Paint.obtain();

        // ── 垂直参考线（全高、半透明白色虚线效果） ──
        paint.setColor(0x55FFFFFF);
        canvas.drawRect(refX - 0.5f, 0, refX + 0.5f, h, paint);

        // ── 每条曲线上绘制圆点 ──
        for (ChartMath.HoverPoint hp : hps) {
            float py = (float) hp.y();
            int color = seriesColor(hp.seriesType());
            // 实心圆点
            paint.setStyle(Paint.FILL);
            paint.setColor(color);
            canvas.drawCircle(refX, py, HOVER_DOT_RADIUS, paint);
            // 外圈
            paint.setColor(0x80FFFFFF);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(1.2f);
            canvas.drawCircle(refX, py, HOVER_DOT_RADIUS + 1.5f, paint);
        }
        paint.setStyle(Paint.FILL);

        // ── 构建多行 tooltip 文本 ──
        int lineCount = hps.size();
        float maxTextW = 0;
        ShapedText[] shapedLines = new ShapedText[lineCount];
        int[] lineColors = new int[lineCount];

        for (int i = 0; i < lineCount; i++) {
            ChartMath.HoverPoint hp = hps.get(i);
            lineColors[i] = seriesColor(hp.seriesType());
            String label = seriesLabel(hp.seriesType());
            String valueStr = ChartMath.formatMetric(hp.value());
            String line = label + ": " + valueStr;
            shapedLines[i] = TextShaper.shapeText(
                    line, 0, line.length(),
                    TextDirectionHeuristics.LTR, tooltipPaint
            );
            maxTextW = Math.max(maxTextW, shapedLines[i].getAdvance());
        }

        // Tooltip 尺寸：色块 + 间距 + 文本
        float swatchGap = TOOLTIP_SWATCH_SIZE + 6;
        float boxW = swatchGap + maxTextW + TOOLTIP_PADDING * 2;
        float boxH = lineCount * TOOLTIP_LINE_HEIGHT + TOOLTIP_PADDING * 2;

        // ── 定位：优先在参考线右侧，溢出时翻转 ──
        float boxX = refX + 12;
        float boxY = Math.max(4, hoverY - boxH / 2);
        if (boxX + boxW > w - 4) {
            boxX = refX - boxW - 12;
        }
        if (boxY + boxH > h - 4) {
            boxY = h - boxH - 4;
        }

        // ── 背景 ──
        paint.setColor(TOOLTIP_BG);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, TOOLTIP_RADIUS, paint);

        // ── 边框 ──
        paint.setColor(TOOLTIP_BORDER);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, TOOLTIP_RADIUS, paint);
        paint.setStyle(Paint.FILL);

        // ── 逐行绘制：色块 + 文本 ──
        float contentX = boxX + TOOLTIP_PADDING;
        float lineY = boxY + TOOLTIP_PADDING;
        for (int i = 0; i < lineCount; i++) {
            float cy = lineY + TOOLTIP_LINE_HEIGHT * 0.5f;
            // 色块（小方块）
            paint.setColor(lineColors[i]);
            float swatchHalf = TOOLTIP_SWATCH_SIZE / 2;
            canvas.drawRoundRect(
                    contentX, cy - swatchHalf,
                    contentX + TOOLTIP_SWATCH_SIZE, cy + swatchHalf,
                    2.0f, paint
            );
            // 文本
            tooltipPaint.setColor(lineColors[i]);
            canvas.drawShapedText(
                    shapedLines[i],
                    contentX + swatchGap,
                    lineY + TOOLTIP_LINE_HEIGHT * 0.75f,
                    tooltipPaint
            );
            lineY += TOOLTIP_LINE_HEIGHT;
        }
        tooltipPaint.setColor(UiThemeTokens.TEXT);

        paint.recycle();
    }

    private static int seriesColor(ChartSeriesType type) {
        return switch (type) {
            case PRODUCTION -> UiThemeTokens.CYAN;
            case CONSUMPTION -> UiThemeTokens.AMBER;
            case NET -> UiThemeTokens.EMERALD;
            case STOCK -> UiThemeTokens.BLUE;
        };
    }

    private static String seriesLabel(ChartSeriesType type) {
        return switch (type) {
            case PRODUCTION -> Component.translatable("screen.resourceobserver.overview.chart.legend.production").getString();
            case CONSUMPTION -> Component.translatable("screen.resourceobserver.overview.chart.legend.consumption").getString();
            case NET -> Component.translatable("screen.resourceobserver.overview.chart.legend.net").getString();
            case STOCK -> Component.translatable("screen.resourceobserver.overview.chart.legend.stock").getString();
        };
    }
}
