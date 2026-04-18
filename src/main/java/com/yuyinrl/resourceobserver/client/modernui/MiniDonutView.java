package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * 迷你甜甜圈指示器 —— 用于表格单元格内展示负载占比。
 * 显示为一个小型环形图 + 中心百分比文字，颜色与饼图对应设备色一致。
 */
final class MiniDonutView extends View {

    private double ratio;      // 0.0 ~ 1.0
    private int arcColor;      // 填充弧颜色（设备色）
    private String label;      // 中心文字

    private static final float INNER_RATIO = 0.58f;
    private static final int BG_RING = 0xFF0D1628;   // 环形底色
    private static final int HOLE_COLOR = 0xFF0D1526; // 中心圆底色

    private final TextPaint textPaint = new TextPaint();

    MiniDonutView(Context context) {
        super(context);
        textPaint.setColor(UiThemeTokens.TEXT);
    }

    /**
     * 设置数据并重绘。
     *
     * @param ratio    占比 0.0 ~ 1.0
     * @param color    弧段颜色（设备色）
     */
    void setData(double ratio, int color) {
        this.ratio = Math.max(0.0, Math.min(1.0, ratio));
        this.arcColor = color;
        this.label = String.format(Locale.ROOT, "%.0f%%", this.ratio * 100);
        invalidate();
    }

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f, cy = h / 2f;
        float outerR = Math.min(cx, cy) - 1f;
        float innerR = outerR * INNER_RATIO;

        Paint paint = Paint.obtain();
        paint.setStyle(Paint.FILL);

        // 1. 底色圆环
        paint.setColor(BG_RING);
        canvas.drawCircle(cx, cy, outerR, paint);

        // 2. 填充弧段（从12点钟方向顺时针）
        if (ratio > 0.001) {
            float sweep = (float) (ratio * 360.0);
            paint.setColor(arcColor);
            canvas.drawPie(cx, cy, outerR, -90f, sweep, paint);
        }

        // 3. 中心圆孔（形成环形效果）
        paint.setColor(HOLE_COLOR);
        canvas.drawCircle(cx, cy, innerR, paint);

        // 4. 内圈细描边
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(0.5f);
        paint.setColor(0x18FFFFFF);
        canvas.drawCircle(cx, cy, innerR, paint);
        paint.setStyle(Paint.FILL);

        // 5. 中心百分比文字
        if (label != null && outerR > 6f) {
            float fontSize = Math.max(5f, innerR * 0.65f);
            textPaint.setTextSize(fontSize);
            textPaint.setColor(arcColor);
            ShapedText shaped = TextShaper.shapeText(
                    label, 0, label.length(),
                    TextDirectionHeuristics.LTR, textPaint);
            float tx = cx - shaped.getAdvance() / 2f;
            float ty = cy + fontSize * 0.35f;
            canvas.drawShapedText(shaped, tx, ty, textPaint);
        }

        paint.recycle();
    }
}
