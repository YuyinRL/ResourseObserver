package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图表视图 —— 使用混合方式实现：
 * <ul>
 *   <li>标题/图例/控件 使用 TextView 组合（Modern UI 惯用方式）</li>
 *   <li>折线图使用自定义 Canvas 绘制（drawLine + Paint）</li>
 * </ul>
 * <p>
 * 布局：
 * <pre>
 * LinearLayout (VERTICAL, 区段背景)
 * ├── LinearLayout (HORIZONTAL, 标题行)
 * │   ├── TextView (标题)
 * │   ├── Spacer
 * │   ├── TextView (时间窗口按钮)
 * │   └── TextView (重置按钮)
 * ├── LinearLayout (HORIZONTAL, 图例行)
 * │   ├── Legend (Prod ●)
 * │   ├── Legend (Cons ●)
 * │   └── Legend (Net ●)
 * └── ChartPlotView (折线绘制区)
 * </pre>
 */
public class ChartView extends LinearLayout {

    /** 回调接口 */
    public interface Callback {
        void onWindowChanged(ChartWindow window);
        void onResetScope();
    }

    private ChartWindow chartWindow = ChartWindow.DAY_24H_5M;
    private final Callback callback;
    private final TextView windowBtn;
    private final ChartPlotView plotView;

    public ChartView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setOrientation(VERTICAL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(8), dp(6), dp(8), dp(6));

        // === 标题行 ===
        var titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        var titleTv = new TextView(context);
        titleTv.setText(Component.translatable("screen.resourceobserver.overview.section.chart").getString());
        titleTv.setTextSize(sp(11));
        titleTv.setTextColor(UiThemeTokens.TEXT);
        titleRow.addView(titleTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        windowBtn = new TextView(context);
        windowBtn.setTextSize(sp(9));
        windowBtn.setTextColor(UiThemeTokens.CYAN);
        windowBtn.setClickable(true);
        windowBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        updateWindowLabel();
        windowBtn.setOnClickListener(v -> {
            chartWindow = chartWindow.next();
            updateWindowLabel();
            if (this.callback != null) {
                this.callback.onWindowChanged(chartWindow);
            }
        });
        ShapeDrawable btnBg = new ShapeDrawable();
        btnBg.setCornerRadius(dp(3));
        btnBg.setColor(0x33224466);
        btnBg.setStroke(dp(1), UiThemeTokens.DIVIDER);
        windowBtn.setBackground(btnBg);
        titleRow.addView(windowBtn, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var resetBtn = new TextView(context);
        resetBtn.setText("Reset");
        resetBtn.setTextSize(sp(9));
        resetBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        resetBtn.setClickable(true);
        resetBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        resetBtn.setOnClickListener(v -> {
            if (this.callback != null) {
                this.callback.onResetScope();
            }
        });
        var resetParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resetParams.leftMargin = dp(4);
        titleRow.addView(resetBtn, resetParams);

        addView(titleRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // === 图例行 ===
        var legendRow = new LinearLayout(context);
        legendRow.setOrientation(HORIZONTAL);
        var legendParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        legendParams.topMargin = dp(2);

        addLegend(legendRow, "● Prod", UiThemeTokens.CYAN);
        addLegend(legendRow, "● Cons", UiThemeTokens.ROSE);
        addLegend(legendRow, "● Net", UiThemeTokens.EMERALD);

        addView(legendRow, legendParams);

        // === 折线绘制区 ===
        plotView = new ChartPlotView(context);
        var plotParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        plotParams.topMargin = dp(4);
        addView(plotView, plotParams);
    }

    /**
     * 更新图表数据。
     */
    public void update(List<OverviewViewModel.FlowPoint> series,
                       List<OverviewViewModel.FlowPoint> energySeries,
                       ChartWindow window,
                       String selectedItemLabel) {
        this.chartWindow = window;
        updateWindowLabel();
        plotView.setData(series);
    }

    private void updateWindowLabel() {
        windowBtn.setText(chartWindow.name());
    }

    private void addLegend(LinearLayout row, String text, int color) {
        var tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(sp(8));
        tv.setTextColor(color);
        var params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(12);
        row.addView(tv, params);
    }

    /**
     * 内部自定义 View —— 折线图绘制区域。
     * 仅使用 Canvas.drawLine 和 Canvas.drawRect，无需文本渲染。
     */
    static class ChartPlotView extends View {

        private List<OverviewViewModel.FlowPoint> series = List.of();

        ChartPlotView(Context context) {
            super(context);
            ShapeDrawable bg = new ShapeDrawable();
            bg.setColor(0x3310182C);
            bg.setStroke(dp(1), UiThemeTokens.DIVIDER);
            bg.setCornerRadius(dp(2));
            setBackground(bg);
        }

        void setData(List<OverviewViewModel.FlowPoint> series) {
            this.series = series != null ? series : List.of();
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float padL = dp(4);
            float padR = dp(4);
            float padT = dp(4);
            float padB = dp(4);
            float plotW = w - padL - padR;
            float plotH = h - padT - padB;

            if (series.isEmpty() || plotW <= 0 || plotH <= 0) {
                return;
            }

            // 收集各线条数据
            List<Double> prodValues = new ArrayList<>();
            List<Double> consValues = new ArrayList<>();
            List<Double> netValues = new ArrayList<>();
            for (OverviewViewModel.FlowPoint pt : series) {
                prodValues.add(pt.production());
                consValues.add(pt.consumption());
                netValues.add(pt.net());
            }

            // 计算全局范围
            double maxVal = 1.0;
            double minVal = 0.0;
            for (double v : prodValues) { maxVal = Math.max(maxVal, v); minVal = Math.min(minVal, v); }
            for (double v : consValues) { maxVal = Math.max(maxVal, v); minVal = Math.min(minVal, v); }
            for (double v : netValues) { maxVal = Math.max(maxVal, v); minVal = Math.min(minVal, v); }
            double range = Math.max(1.0, maxVal - minVal);

            // 零线
            if (minVal < 0 && maxVal > 0) {
                float zeroY = (float) (padT + plotH - (0 - minVal) / range * plotH);
                Paint zeroPaint = Paint.obtain();
                zeroPaint.setColor(UiThemeTokens.DIVIDER);
                canvas.drawLine(padL, zeroY, padL + plotW, zeroY, dp(1), zeroPaint);
                zeroPaint.recycle();
            }

            // 绘制三条折线
            drawPolyline(canvas, prodValues, padL, padT, plotW, plotH, minVal, range, UiThemeTokens.CYAN);
            drawPolyline(canvas, consValues, padL, padT, plotW, plotH, minVal, range, UiThemeTokens.ROSE);
            drawPolyline(canvas, netValues, padL, padT, plotW, plotH, minVal, range, UiThemeTokens.EMERALD);
        }

        private void drawPolyline(Canvas canvas, List<Double> values,
                                  float x0, float y0, float w, float h,
                                  double minVal, double range, int color) {
            if (values.size() < 2) return;
            Paint paint = Paint.obtain();
            paint.setColor(color);
            float thickness = dp(2);

            int count = values.size();
            for (int i = 1; i < count; i++) {
                float fx0 = x0 + ((i - 1) / (float) (count - 1)) * w;
                float fy0 = (float) (y0 + h - ((values.get(i - 1) - minVal) / range) * h);
                float fx1 = x0 + (i / (float) (count - 1)) * w;
                float fy1 = (float) (y0 + h - ((values.get(i) - minVal) / range) * h);
                canvas.drawLine(fx0, fy0, fx1, fy1, thickness, paint);
            }
            paint.recycle();
        }
    }
}
