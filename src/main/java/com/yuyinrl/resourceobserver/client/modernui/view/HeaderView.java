package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 页眉视图 —— 终端界面顶部的标题栏和连接状态卡片。
 * <p>
 * 使用 LinearLayout 组合 TextView 实现，符合 Modern UI 惯用方式。
 * <p>
 * 布局：
 * <pre>
 * FrameLayout (headerBg)
 * ├── LinearLayout (左侧, VERTICAL)
 * │   ├── TextView (标题)
 * │   └── TextView (副标题)
 * └── LinearLayout (右侧, 连接状态卡片)
 *     ├── TextView (状态灯 ●)
 *     └── LinearLayout (VERTICAL)
 *         ├── TextView (连接状态)
 *         └── TextView (坐标)
 * </pre>
 */
public class HeaderView extends FrameLayout {

    private final TextView statusDotTv;
    private final TextView statusTextTv;
    private final TextView coordsTv;
    private final LinearLayout statusCard;

    public HeaderView(Context context) {
        super(context);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.HEADER_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(12), dp(8), dp(10), dp(8));

        // === 左侧：标题 + 副标题 ===
        var leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);

        var titleTv = new TextView(context);
        titleTv.setTextSize(sp(14));
        titleTv.setTextColor(UiThemeTokens.TITLE);
        titleTv.setText(Component.translatable("screen.resourceobserver.overview.header.title").getString());
        leftColumn.addView(titleTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var subtitleTv = new TextView(context);
        subtitleTv.setTextSize(sp(10));
        subtitleTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        subtitleTv.setText(Component.translatable("screen.resourceobserver.overview.header.subtitle").getString());
        var subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(2);
        leftColumn.addView(subtitleTv, subParams);

        addView(leftColumn, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));

        // === 右侧：连接状态卡片 ===
        statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(8), dp(4), dp(8), dp(4));
        updateStatusCardBackground(false);

        // 状态灯
        statusDotTv = new TextView(context);
        statusDotTv.setTextSize(sp(12));
        statusDotTv.setText("●");
        statusCard.addView(statusDotTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 状态文本列
        var statusColumn = new LinearLayout(context);
        statusColumn.setOrientation(LinearLayout.VERTICAL);
        var statusColumnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusColumnParams.leftMargin = dp(6);

        statusTextTv = new TextView(context);
        statusTextTv.setTextSize(sp(10));
        statusColumn.addView(statusTextTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        coordsTv = new TextView(context);
        coordsTv.setTextSize(sp(9));
        coordsTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        var coordsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        coordsParams.topMargin = dp(1);
        statusColumn.addView(coordsTv, coordsParams);

        statusCard.addView(statusColumn, statusColumnParams);

        addView(statusCard, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL
        ));
    }

    /**
     * 更新页眉数据。
     */
    public void update(BlockPos pos, boolean linked) {
        int statusColor = linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE;
        int dotColor = linked ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;

        statusDotTv.setTextColor(dotColor);

        statusTextTv.setTextColor(statusColor);
        statusTextTv.setText(Component.translatable(
                linked ? "screen.resourceobserver.overview.link_status"
                        : "screen.resourceobserver.overview.link_status_unbound"
        ).getString());

        coordsTv.setText(Component.translatable(
                "screen.resourceobserver.overview.header.coords",
                pos.getX(), pos.getY(), pos.getZ()
        ).getString());

        updateStatusCardBackground(linked);
    }

    private void updateStatusCardBackground(boolean linked) {
        ShapeDrawable cardBg = new ShapeDrawable();
        cardBg.setCornerRadius(dp(4));
        cardBg.setColor(0xE0182A41);
        cardBg.setStroke(dp(1), linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE);
        statusCard.setBackground(cardBg);
    }
}
