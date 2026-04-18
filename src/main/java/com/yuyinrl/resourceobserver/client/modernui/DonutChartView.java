package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
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
import java.util.List;
import java.util.Locale;

/**
 * 甜甜圈图 View —— 显示发电量 vs 各负载段的占比关系。
 * <ul>
 *   <li>外环 = 发电容量（有效输入）100%</li>
 *   <li>填充段 = 各消耗类别占发电容量的比例</li>
 *   <li>空白段 = 容量余量（headroom）</li>
 *   <li>中心文字 = 余量百分比 + 标签</li>
 * </ul>
 * 支持鼠标悬停高亮 + tooltip。
 */
final class DonutChartView extends View {

    /** A single arc segment in the donut. */
    record Segment(String label, double percentage, long feTick, int color) {}

    // ── Data ──
    private List<Segment> segments = List.of();
    private double headroomPercent = 100.0;
    private String centerLine1 = "";
    private String centerLine2 = "";
    private int centerColor = UiThemeTokens.TEXT;

    // ── Hover state ──
    private float hoverX = -1, hoverY = -1;
    private boolean hovering = false;
    private int hoveredIndex = -1;

    // ── Paint ──
    private final TextPaint textPaint = new TextPaint();

    // ── Constants ──
    private static final float INNER_RATIO = 0.62f;
    private static final float HOVER_EXPAND = 3f;
    private static final float SEG_GAP_DEG = 1.2f;
    private static final int TOOLTIP_BG = 0xE0101828;
    private static final int TOOLTIP_BORDER = 0x40FFFFFF;
    private static final float TOOLTIP_RADIUS = 4f;
    private static final float TOOLTIP_PADDING = 6f;
    private static final float TOOLTIP_LINE_HEIGHT = 16f;
    private static final float TOOLTIP_SWATCH = 8f;
    private static final int BG_RING_COLOR = 0xFF0A1020;
    private static final int HOLE_COLOR = 0xFF0D1526;
    private static final int HEADROOM_RING_COLOR = 0xFF162236;

    DonutChartView(Context context) {
        super(context);
        textPaint.setColor(UiThemeTokens.TEXT);
    }

    /**
     * 设置甜甜圈数据并重绘。
     *
     * @param segments       负载分段列表（百分比相对于总输出）
     * @param headroomPercent 余量百分比（0~100+）
     * @param centerLine1    中心大字（如 "85.0%"）
     * @param centerLine2    中心小字（如 "Headroom"）
     * @param centerColor    中心大字颜色
     */
    void setData(List<Segment> segments, double headroomPercent,
                 String centerLine1, String centerLine2, int centerColor) {
        this.segments = segments != null ? segments : List.of();
        this.headroomPercent = headroomPercent;
        this.centerLine1 = centerLine1 != null ? centerLine1 : "";
        this.centerLine2 = centerLine2 != null ? centerLine2 : "";
        this.centerColor = centerColor;
        // Preserve hover state: re-run hit test with current cursor position
        // instead of blindly resetting to -1
        if (hovering && hoverX >= 0 && hoverY >= 0) {
            hoveredIndex = hitTest(hoverX, hoverY);
        } else {
            hoveredIndex = -1;
        }
        invalidate();
    }

    // ===================== Hover =====================

    @Override
    public boolean onHoverEvent(@Nonnull MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            hoverX = event.getX();
            hoverY = event.getY();
            hovering = true;
            int prev = hoveredIndex;
            hoveredIndex = hitTest(hoverX, hoverY);
            if (hoveredIndex != prev) invalidate();
            return true;
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            hovering = false;
            hoverX = -1;
            hoverY = -1;
            if (hoveredIndex != -1) {
                hoveredIndex = -1;
                invalidate();
            }
            return true;
        }
        return super.onHoverEvent(event);
    }

    /**
     * 根据鼠标位置判断悬停在哪个段上。返回段索引，-1 表示不在任何段上。
     */
    private int hitTest(float x, float y) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float outerR = Math.min(cx, cy) - HOVER_EXPAND - 1f;
        float innerR = outerR * INNER_RATIO;

        float dx = x - cx, dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < innerR || dist > outerR + HOVER_EXPAND + 2f) return -1;

        // Angle: 0° = top (12 o'clock), clockwise
        double angleDeg = Math.toDegrees(Math.atan2(dx, -dy));
        if (angleDeg < 0) angleDeg += 360.0;

        double scale = usageScale();
        int segCount = segments.size();
        double totalGap = segCount > 1 ? segCount * SEG_GAP_DEG : 0;
        double availableDeg = 360.0 * scale - totalGap;
        if (availableDeg <= 0) return -1;

        double rawTotal = rawSegTotal();
        double cumAngle = 0;
        for (int i = 0; i < segCount; i++) {
            double segDeg = rawTotal > 0
                    ? segments.get(i).percentage() / rawTotal * availableDeg
                    : 0;
            double gapBefore = (segCount > 1) ? SEG_GAP_DEG / 2.0 : 0;
            double start = cumAngle + gapBefore;
            double end = start + segDeg;
            if (angleDeg >= start && angleDeg < end) return i;
            cumAngle += segDeg + (segCount > 1 ? SEG_GAP_DEG : 0);
        }
        return -1;
    }

    // ===================== Drawing =====================

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f, cy = h / 2f;
        float outerR = Math.min(cx, cy) - HOVER_EXPAND - 1f;
        float innerR = outerR * INNER_RATIO;

        Paint paint = Paint.obtain();
        paint.setStyle(Paint.FILL);

        // 1. Background ring
        paint.setColor(BG_RING_COLOR);
        canvas.drawCircle(cx, cy, outerR, paint);

        // 2. Headroom ring (slightly brighter than background to distinguish)
        double scale = usageScale();
        if (scale < 1.0) {
            float headroomStart = -90f + (float) (scale * 360.0);
            float headroomSweep = (float) ((1.0 - scale) * 360.0);
            paint.setColor(HEADROOM_RING_COLOR);
            canvas.drawPie(cx, cy, outerR, headroomStart, headroomSweep, paint);
        }

        // 3. Load segments
        double rawTotal = rawSegTotal();
        int segCount = segments.size();
        double totalGap = segCount > 1 ? segCount * SEG_GAP_DEG : 0;
        double availableDeg = 360.0 * scale - totalGap;

        if (availableDeg > 0 && rawTotal > 0) {
            float startAngle = -90f;
            for (int i = 0; i < segCount; i++) {
                Segment seg = segments.get(i);
                float segSweep = (float) (seg.percentage() / rawTotal * availableDeg);
                if (segSweep < 0.1f) {
                    startAngle += segSweep + (segCount > 1 ? SEG_GAP_DEG : 0);
                    continue;
                }

                float gapHalf = segCount > 1 ? SEG_GAP_DEG / 2f : 0;
                float drawStart = startAngle + gapHalf;
                float drawSweep = segSweep;

                float r = outerR;
                int alpha = 200;
                if (hoveredIndex == i) {
                    r += HOVER_EXPAND;
                    alpha = 255;
                } else if (hoveredIndex >= 0) {
                    alpha = 140; // dim non-hovered segments
                }

                paint.setColor(seg.color());
                paint.setAlpha(alpha);
                canvas.drawPie(cx, cy, r, drawStart, drawSweep, paint);

                startAngle += segSweep + (segCount > 1 ? SEG_GAP_DEG : 0);
            }
        }

        // 4. Inner circle (donut hole)
        paint.setColor(HOLE_COLOR);
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, innerR, paint);

        // 5. Subtle inner ring border
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(0x20FFFFFF);
        canvas.drawCircle(cx, cy, innerR, paint);
        paint.setStyle(Paint.FILL);

        // 6. Center text
        drawCenterText(canvas, cx, cy, innerR);

        // 7. Tooltip
        if (hovering && hoveredIndex >= 0 && hoveredIndex < segments.size()) {
            drawTooltip(canvas, paint, w, h);
        }

        paint.recycle();
    }

    private void drawCenterText(Canvas canvas, float cx, float cy, float innerR) {
        if (centerLine1.isEmpty()) return;

        // Line 1: large value
        float size1 = Math.max(8f, innerR * 0.38f);
        textPaint.setTextSize(size1);
        textPaint.setColor(centerColor);
        ShapedText shaped1 = TextShaper.shapeText(
                centerLine1, 0, centerLine1.length(),
                TextDirectionHeuristics.LTR, textPaint);
        float x1 = cx - shaped1.getAdvance() / 2f;
        float y1 = cy - size1 * 0.1f;
        canvas.drawShapedText(shaped1, x1, y1, textPaint);

        // Line 2: label
        if (!centerLine2.isEmpty()) {
            float size2 = Math.max(6f, innerR * 0.22f);
            textPaint.setTextSize(size2);
            textPaint.setColor(UiThemeTokens.TEXT_MUTED);
            ShapedText shaped2 = TextShaper.shapeText(
                    centerLine2, 0, centerLine2.length(),
                    TextDirectionHeuristics.LTR, textPaint);
            float x2 = cx - shaped2.getAdvance() / 2f;
            float y2 = cy + innerR * 0.32f;
            canvas.drawShapedText(shaped2, x2, y2, textPaint);
        }
    }

    private void drawTooltip(Canvas canvas, Paint paint, int viewW, int viewH) {
        Segment seg = segments.get(hoveredIndex);

        // Build tooltip text lines
        String line1 = seg.label();
        double pctOfGen = seg.percentage() * usageScale();
        String line2 = String.format(Locale.ROOT, "%.1f%%", pctOfGen)
                + "  (" + ModernUiTheme.compact(seg.feTick()) + " FE/t)";

        float fontSize = 11f;
        textPaint.setTextSize(fontSize);

        ShapedText shaped1 = TextShaper.shapeText(
                line1, 0, line1.length(), TextDirectionHeuristics.LTR, textPaint);
        ShapedText shaped2 = TextShaper.shapeText(
                line2, 0, line2.length(), TextDirectionHeuristics.LTR, textPaint);

        float maxTextW = Math.max(shaped1.getAdvance(), shaped2.getAdvance());
        float swatchGap = TOOLTIP_SWATCH + 6f;
        float boxW = swatchGap + maxTextW + TOOLTIP_PADDING * 2;
        float boxH = 2 * TOOLTIP_LINE_HEIGHT + TOOLTIP_PADDING * 2;

        // Position: prefer right of cursor
        float boxX = hoverX + 14;
        float boxY = hoverY - boxH / 2;
        if (boxX + boxW > viewW - 4) boxX = hoverX - boxW - 14;
        if (boxY < 4) boxY = 4;
        if (boxY + boxH > viewH - 4) boxY = viewH - boxH - 4;

        // Background
        paint.setStyle(Paint.FILL);
        paint.setColor(TOOLTIP_BG);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, TOOLTIP_RADIUS, paint);

        // Border
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(TOOLTIP_BORDER);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, TOOLTIP_RADIUS, paint);
        paint.setStyle(Paint.FILL);

        float contentX = boxX + TOOLTIP_PADDING;
        float lineY = boxY + TOOLTIP_PADDING;

        // Line 1: swatch + label
        float cy1 = lineY + TOOLTIP_LINE_HEIGHT * 0.5f;
        float swatchHalf = TOOLTIP_SWATCH / 2;
        paint.setColor(seg.color());
        canvas.drawRoundRect(
                contentX, cy1 - swatchHalf,
                contentX + TOOLTIP_SWATCH, cy1 + swatchHalf,
                2f, paint);

        textPaint.setColor(UiThemeTokens.TEXT);
        canvas.drawShapedText(shaped1, contentX + swatchGap, cy1 + fontSize * 0.35f, textPaint);

        // Line 2: percentage + FE/t
        float cy2 = lineY + TOOLTIP_LINE_HEIGHT + TOOLTIP_LINE_HEIGHT * 0.5f;
        textPaint.setColor(UiThemeTokens.TEXT_MUTED);
        canvas.drawShapedText(shaped2, contentX + swatchGap, cy2 + fontSize * 0.35f, textPaint);
    }

    // ── Helpers ──

    /** Usage ratio clamped to [0, 1]: how much of generation is used by load. */
    private double usageScale() {
        return Math.min(1.0, Math.max(0.0, (100.0 - headroomPercent) / 100.0));
    }

    /** Sum of all segment percentages (they are % of total output, may not sum to exactly 100). */
    private double rawSegTotal() {
        double sum = 0;
        for (Segment seg : segments) sum += seg.percentage();
        return sum;
    }
}
