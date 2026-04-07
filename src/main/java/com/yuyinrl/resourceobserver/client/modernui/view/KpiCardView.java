package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/**
 * 单张 KPI 卡片视图 —— 使用 LinearLayout 组合 TextView 实现。
 * <p>
 * 布局：
 * <pre>
 * FrameLayout (卡片背景)
 * ├── LinearLayout (VERTICAL, 左侧文本)
 * │   ├── TextView (标签)
 * │   ├── TextView (数值)
 * │   └── TextView (趋势)
 * └── View (右上角图标占位)
 * </pre>
 */
public class KpiCardView extends FrameLayout {

    public interface OnKpiClickListener {
        void onKpiClicked(OverviewViewModel.KpiType type);
    }

    private OverviewViewModel.KpiMetric metric;
    private final TextView labelTv;
    private final TextView valueTv;
    private final TextView trendTv;
    private final View accentBar;
    private OnKpiClickListener clickListener;

    public KpiCardView(Context context) {
        super(context);
        setClickable(true);
        updateBackground(false);

        setPadding(dp(8), dp(6), dp(8), dp(6));

        // 文本列
        var textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        labelTv = new TextView(context);
        labelTv.setTextSize(sp(10));
        labelTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        textColumn.addView(labelTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        valueTv = new TextView(context);
        valueTv.setTextSize(sp(11));
        valueTv.setTextColor(UiThemeTokens.TITLE);
        var valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.topMargin = dp(2);
        textColumn.addView(valueTv, valueParams);

        trendTv = new TextView(context);
        trendTv.setTextSize(sp(10));
        var trendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        trendParams.topMargin = dp(1);
        textColumn.addView(trendTv, trendParams);

        addView(textColumn, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP
        ));

        // 图标区占位
        var iconPlaceholder = new View(context);
        ShapeDrawable iconBg = new ShapeDrawable();
        iconBg.setColor(0x4016253C);
        iconBg.setCornerRadius(dp(2));
        iconPlaceholder.setBackground(iconBg);
        addView(iconPlaceholder, new LayoutParams(dp(16), dp(16), Gravity.END | Gravity.TOP));

        // 底部高亮条（初始隐藏）
        accentBar = new View(context);
        accentBar.setVisibility(INVISIBLE);
        ShapeDrawable accentBg = new ShapeDrawable();
        accentBg.setColor(0xAA76D2FF);
        accentBar.setBackground(accentBg);
        addView(accentBar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM
        ));

        setOnClickListener(v -> {
            if (metric != null && clickListener != null) {
                clickListener.onKpiClicked(metric.type());
            }
        });
    }

    public void setOnKpiClickListener(OnKpiClickListener listener) {
        this.clickListener = listener;
    }

    public void update(OverviewViewModel.KpiMetric metric) {
        this.metric = metric;
        labelTv.setText(Component.translatable(metric.label()).getString());
        valueTv.setText(metric.value() != null ? metric.value() : "");
        trendTv.setText(metric.trend() != null ? metric.trend() : "");
        trendTv.setTextColor(statusColor(metric.status()));
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        updateBackground(hovered);
        accentBar.setVisibility(hovered ? VISIBLE : INVISIBLE);
    }

    private void updateBackground(boolean hover) {
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(hover ? 0xEE1C2F4A : UiThemeTokens.CARD_BG);
        bg.setStroke(dp(1), hover ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER);
        setBackground(bg);
    }

    private static int statusColor(OverviewViewModel.Status status) {
        return switch (status) {
            case POSITIVE -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case NEGATIVE -> UiThemeTokens.ROSE;
            case NEUTRAL -> UiThemeTokens.TEXT;
        };
    }
}
