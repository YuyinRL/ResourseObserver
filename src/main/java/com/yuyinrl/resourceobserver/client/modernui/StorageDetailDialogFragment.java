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
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 存储详情弹窗 —— 显示 AE2 存储容量的详细分解信息。
 * <p>
 * 包含：
 * - 磁盘物品/流体通道使用率
 * - 外部物品/流体通道使用率
 * - 可靠性指示
 */
public class StorageDetailDialogFragment extends DialogFragment {

    private final OverviewViewModel.StorageDetail detail;

    public StorageDetailDialogFragment(OverviewViewModel.StorageDetail detail) {
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
            addDivider(root);

            // 磁盘存储
            addSectionTitle(root, "Disk Storage");
            if (detail.diskItem().available()) {
                addChannel(root, detail.diskItem());
            }
            if (detail.diskFluid().available()) {
                addChannel(root, detail.diskFluid());
            }
            addReliabilityIndicator(root, "Disk Reliable", detail.diskReliable());

            addDivider(root);

            // 外部存储
            addSectionTitle(root, "External Storage");
            if (detail.externalItem().available()) {
                addChannel(root, detail.externalItem());
            }
            if (detail.externalFluid().available()) {
                addChannel(root, detail.externalFluid());
            }
            addReliabilityIndicator(root, "External Reliable", detail.externalReliable());
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

    private void addSectionTitle(LinearLayout parent, String text) {
        var tv = new TextView(requireContext());
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

    private void addChannel(LinearLayout parent, OverviewViewModel.StorageChannel channel) {
        Context context = requireContext();

        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);

        // Label
        var label = new TextView(context);
        label.setText(channel.label());
        label.setTextSize(parent.sp(10));
        label.setTextColor(UiThemeTokens.TEXT);
        row.addView(label);

        // Usage
        addDetailRow(row, "Usage:", channel.usageText());
        addDetailRow(row, "Types:", channel.typesText());

        if (channel.usageDetailText() != null && !channel.usageDetailText().equals("N/A")) {
            addDetailRow(row, "Detail:", channel.usageDetailText());
        }

        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = parent.dp(2);
        params.bottomMargin = parent.dp(4);
        parent.addView(row, params);
    }

    private void addDetailRow(LinearLayout parent, String label, String value) {
        Context context = requireContext();
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

    private void addReliabilityIndicator(LinearLayout parent, String label, boolean reliable) {
        Context context = requireContext();
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
