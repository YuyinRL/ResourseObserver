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
import net.minecraft.network.chat.Component;

/**
 * 存储详情弹窗 —— 显示 AE2 存储容量的详细分解信息。
 * <p>
 * 包含：
 * - 磁盘物品/流体通道使用率
 * - 外部物品/流体通道使用率
 * - 可靠性指示
 * <p>
 * 此类为纯 View 构建器（Modern UI 没有 DialogFragment），
 * 通过 {@link #createOverlayView} 创建叠加层 View，由 {@link OverviewFragment} 管理生命周期。
 */
public final class StorageDetailDialogFragment {

    private StorageDetailDialogFragment() {
    }

    /**
     * 创建存储详情弹窗的叠加层 View。
     *
     * @param context   UI 上下文
     * @param detail    存储详情数据
     * @param onDismiss 关闭回调
     * @return 可直接添加到 FrameLayout 的叠加层 View
     */
    public static View createOverlayView(Context context,
                                         OverviewViewModel.StorageDetail detail,
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
                root.dp(380),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.setLayoutParams(rootParams);

        // 标题
        var title = new TextView(context);
        title.setText(Component.translatable("screen.resourceobserver.overview.kpi.storage").getString());
        title.setTextSize(root.sp(14));
        title.setTextColor(UiThemeTokens.TITLE);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 绑定状态
        if (!detail.hasAe2Binding()) {
            var hint = new TextView(context);
            hint.setText(detail.hintText() != null ? detail.hintText() : "No AE2 binding");
            hint.setTextSize(root.sp(10));
            hint.setTextColor(UiThemeTokens.TEXT_MUTED);
            var hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            hintParams.topMargin = root.dp(4);
            root.addView(hint, hintParams);
        } else {
            // 分割线
            addDivider(context, root);

            // 磁盘存储
            addSectionTitle(context, root, "Disk Storage");
            if (detail.diskItem().available()) {
                addChannel(context, root, detail.diskItem());
            }
            if (detail.diskFluid().available()) {
                addChannel(context, root, detail.diskFluid());
            }
            addReliabilityIndicator(context, root, "Disk Reliable", detail.diskReliable());

            addDivider(context, root);

            // 外部存储
            addSectionTitle(context, root, "External Storage");
            if (detail.externalItem().available()) {
                addChannel(context, root, detail.externalItem());
            }
            if (detail.externalFluid().available()) {
                addChannel(context, root, detail.externalFluid());
            }
            addReliabilityIndicator(context, root, "External Reliable", detail.externalReliable());
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

    private static void addSectionTitle(Context context, LinearLayout parent, String text) {
        var tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(parent.sp(11));
        tv.setTextColor(UiThemeTokens.CYAN);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = parent.dp(4);
        parent.addView(tv, params);
    }

    private static void addChannel(Context context, LinearLayout parent, OverviewViewModel.StorageChannel channel) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);

        // Label
        var label = new TextView(context);
        label.setText(channel.label());
        label.setTextSize(parent.sp(10));
        label.setTextColor(UiThemeTokens.TEXT);
        row.addView(label);

        // Usage
        addDetailRow(context, row, "Usage:", channel.usageText());
        addDetailRow(context, row, "Types:", channel.typesText());

        if (channel.usageDetailText() != null && !channel.usageDetailText().equals("N/A")) {
            addDetailRow(context, row, "Detail:", channel.usageDetailText());
        }

        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = parent.dp(2);
        params.bottomMargin = parent.dp(4);
        parent.addView(row, params);
    }

    private static void addDetailRow(Context context, LinearLayout parent, String label, String value) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        var labelTv = new TextView(context);
        labelTv.setText(label);
        labelTv.setTextSize(parent.sp(9));
        labelTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        row.addView(labelTv, new LinearLayout.LayoutParams(
                parent.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var valueTv = new TextView(context);
        valueTv.setText(value != null ? value : "N/A");
        valueTv.setTextSize(parent.sp(9));
        valueTv.setTextColor(UiThemeTokens.TEXT);
        row.addView(valueTv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));

        var rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = parent.dp(1);
        parent.addView(row, rowParams);
    }

    private static void addReliabilityIndicator(Context context, LinearLayout parent, String label, boolean reliable) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        var labelTv = new TextView(context);
        labelTv.setText(label + ": ");
        labelTv.setTextSize(parent.sp(9));
        labelTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        row.addView(labelTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var valueTv = new TextView(context);
        valueTv.setText(reliable ? "✓" : "✗");
        valueTv.setTextSize(parent.sp(9));
        valueTv.setTextColor(reliable ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE);
        row.addView(valueTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = parent.dp(2);
        parent.addView(row, rowParams);
    }
}
