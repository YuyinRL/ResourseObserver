package com.yuyinrl.resourceobserver.client.modernui;

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

/**
 * KPI 详情弹窗 —— 显示单个 KPI 指标的详细信息。
 * <p>
 * 包含：
 * - 标题（指标类型名称）
 * - 状态提示文本
 * - 物品通道（Recent / Previous / Trend）
 * - 流体通道（如有）
 * <p>
 * 此类为纯 View 构建器（Modern UI 没有 DialogFragment），
 * 通过 {@link #createOverlayView} 创建叠加层 View，由 {@link OverviewFragment} 管理生命周期。
 */
public final class KpiDetailDialogFragment {

    private KpiDetailDialogFragment() {
    }

    /**
     * 创建 KPI 详情弹窗的叠加层 View。
     *
     * @param context   UI 上下文
     * @param detail    KPI 详情数据
     * @param onDismiss 关闭回调
     * @return 可直接添加到 FrameLayout 的叠加层 View
     */
    public static View createOverlayView(Context context,
                                         OverviewViewModel.KpiDetail detail,
                                         Runnable onDismiss) {
        // === 半透明遮罩层 ===
        var overlay = new FrameLayout(context);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        ShapeDrawable scrim = new ShapeDrawable();
        scrim.setColor(0x88000000);
        overlay.setBackground(scrim);
        overlay.setClickable(true); // 拦截点击，防止穿透

        // === 对话框内容面板 ===
        var root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(root.dp(16), root.dp(12), root.dp(16), root.dp(12));

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(root.dp(8));
        bg.setColor(UiThemeTokens.PANEL_BG);
        bg.setStroke(root.dp(1), UiThemeTokens.PANEL_BORDER);
        root.setBackground(bg);

        var rootParams = new FrameLayout.LayoutParams(
                root.dp(360),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.setLayoutParams(rootParams);

        // 标题
        var title = new TextView(context);
        title.setText(detail.title());
        title.setTextSize(root.sp(14));
        title.setTextColor(UiThemeTokens.TITLE);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 状态提示
        if (detail.hintText() != null && !detail.hintText().isBlank()) {
            var hint = new TextView(context);
            hint.setText(detail.hintText());
            hint.setTextSize(root.sp(10));
            hint.setTextColor(statusColor(detail.status()));
            var hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            hintParams.topMargin = root.dp(4);
            root.addView(hint, hintParams);
        }

        // 分割线
        addDivider(context, root);

        // 物品通道
        if (detail.itemChannel() != null && detail.itemChannel().available()) {
            addChannel(context, root, detail.itemChannel());
        }

        // 流体通道
        if (detail.fluidChannel() != null && detail.fluidChannel().available()) {
            addDivider(context, root);
            addChannel(context, root, detail.fluidChannel());
        }

        // 关闭按钮
        var closeBtn = new TextView(context);
        closeBtn.setText("Close");
        closeBtn.setTextSize(root.sp(11));
        closeBtn.setTextColor(UiThemeTokens.CYAN);
        closeBtn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        closeBtn.setClickable(true);
        closeBtn.setOnClickListener(v -> onDismiss.run());
        var closeBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeBtnParams.topMargin = root.dp(8);
        root.addView(closeBtn, closeBtnParams);

        overlay.addView(root);
        return overlay;
    }

    private static void addDivider(Context context, LinearLayout parent) {
        View divider = new View(context);
        ShapeDrawable divBg = new ShapeDrawable();
        divBg.setColor(UiThemeTokens.DIVIDER);
        divider.setBackground(divBg);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                divider.dp(1)
        );
        params.topMargin = divider.dp(6);
        params.bottomMargin = divider.dp(6);
        parent.addView(divider, params);
    }

    private static void addChannel(Context context, LinearLayout parent, OverviewViewModel.KpiDetailChannel channel) {
        var label = new TextView(context);
        label.setText(channel.label());
        label.setTextSize(parent.sp(11));
        label.setTextColor(UiThemeTokens.TEXT);
        parent.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addDetailRow(context, parent, "Recent:", channel.recentText());
        addDetailRow(context, parent, "Previous:", channel.previousText());
        addDetailRow(context, parent, "Trend:", channel.trendText());
    }

    private static void addDetailRow(Context context, LinearLayout parent, String label, String value) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        var labelTv = new TextView(context);
        labelTv.setText(label);
        labelTv.setTextSize(parent.sp(10));
        labelTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        row.addView(labelTv, new LinearLayout.LayoutParams(
                parent.dp(80), ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var valueTv = new TextView(context);
        valueTv.setText(value != null ? value : "N/A");
        valueTv.setTextSize(parent.sp(10));
        valueTv.setTextColor(UiThemeTokens.TEXT);
        row.addView(valueTv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        var rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = parent.dp(2);
        parent.addView(row, rowParams);
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
