package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.DialogFragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import javax.annotation.Nullable;

/**
 * KPI 详情弹窗 —— 显示单个 KPI 指标的详细信息。
 * <p>
 * 包含：
 * - 标题（指标类型名称）
 * - 状态提示文本
 * - 物品通道（Recent / Previous / Trend）
 * - 流体通道（如有）
 */
public class KpiDetailDialogFragment extends DialogFragment {

    private final OverviewViewModel.KpiDetail detail;

    public KpiDetailDialogFragment(OverviewViewModel.KpiDetail detail) {
        this.detail = detail;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        Context context = requireContext();

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
        addDivider(root);

        // 物品通道
        if (detail.itemChannel() != null && detail.itemChannel().available()) {
            addChannel(root, detail.itemChannel());
        }

        // 流体通道
        if (detail.fluidChannel() != null && detail.fluidChannel().available()) {
            addDivider(root);
            addChannel(root, detail.fluidChannel());
        }

        // 关闭按钮
        var closeBtn = new TextView(context);
        closeBtn.setText("Close");
        closeBtn.setTextSize(root.sp(11));
        closeBtn.setTextColor(UiThemeTokens.CYAN);
        closeBtn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        closeBtn.setClickable(true);
        closeBtn.setOnClickListener(v -> dismiss());
        var closeBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeBtnParams.topMargin = root.dp(8);
        root.addView(closeBtn, closeBtnParams);

        return root;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(requireContext());
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

    private void addChannel(LinearLayout parent, OverviewViewModel.KpiDetailChannel channel) {
        Context context = requireContext();

        var label = new TextView(context);
        label.setText(channel.label());
        label.setTextSize(parent.sp(11));
        label.setTextColor(UiThemeTokens.TEXT);
        parent.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addDetailRow(parent, "Recent:", channel.recentText());
        addDetailRow(parent, "Previous:", channel.previousText());
        addDetailRow(parent, "Trend:", channel.trendText());
    }

    private void addDetailRow(LinearLayout parent, String label, String value) {
        Context context = requireContext();
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
