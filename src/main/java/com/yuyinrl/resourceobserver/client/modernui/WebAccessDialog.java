package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.WebTokenPayload;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Web Dashboard 访问对话框 —— 展示登录链接、提供复制 / 重新生成按钮。
 * <p>
 * 不复用复杂的动画/遮罩管线，以最小成本满足"玩家能看到 URL 并复制"这一核心需求。
 */
public final class WebAccessDialog {

    private WebAccessDialog() {
    }

    public static void show(ResourceTerminalFragment terminal, WebTokenPayload payload) {
        FrameLayout overlay = terminal.getDialogOverlay();
        FrameLayout container = overlay != null ? overlay : terminal.getContentContainer();
        if (container == null) return;
        // 移除已存在的同类对话框，避免重复叠加
        container.removeAllViews();
        terminal.setDialogOpen(true);

        // 半透明遮罩
        View dim = new View(terminal.getContext());
        ShapeDrawable dimBg = new ShapeDrawable();
        dimBg.setColor(0xAA04070E);
        dim.setBackground(dimBg);
        dim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.setClickable(true);
        container.addView(dim);

        LinearLayout dialog = new LinearLayout(terminal.getContext());
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackground(ModernUiTheme.makeBackground(dialog,
                UiThemeTokens.PANEL_BG, UiThemeTokens.SECTION_BORDER, 10, 1));
        dialog.setPadding(terminal.dp(16), terminal.dp(14), terminal.dp(16), terminal.dp(14));
        dialog.setClickable(true);

        FrameLayout.LayoutParams dialogLp = new FrameLayout.LayoutParams(
                terminal.dp(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialogLp.gravity = Gravity.CENTER;

        // 标题
        TextView title = new TextView(terminal.getContext());
        title.setText(tr("gui.resourceobserver.web_access.title"));
        title.setTextSize(13 * ModernUiTheme.getTextScale());
        title.setTextColor(UiThemeTokens.CYAN);
        dialog.addView(title);

        // 提示
        TextView warn = new TextView(terminal.getContext());
        String warnKey = payload.regenerated()
                ? "gui.resourceobserver.web_access.regenerated_hint"
                : "gui.resourceobserver.web_access.warning";
        warn.setText(tr(warnKey));
        warn.setTextSize(10 * ModernUiTheme.getTextScale());
        warn.setTextColor(UiThemeTokens.TEXT_MUTED);
        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warnLp.topMargin = terminal.dp(6);
        dialog.addView(warn, warnLp);

        // URL 显示区域（可读）
        TextView urlView = new TextView(terminal.getContext());
        String displayUrl = payload.url() == null || payload.url().isBlank() ? payload.baseUrl() : payload.url();
        urlView.setText(displayUrl);
        urlView.setTextSize(10 * ModernUiTheme.getTextScale());
        urlView.setTextColor(UiThemeTokens.TEXT);
        urlView.setBackground(ModernUiTheme.makeBackground(urlView,
                UiThemeTokens.HEADER_BG, UiThemeTokens.SECTION_BORDER, 6, 1));
        urlView.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = terminal.dp(10);
        dialog.addView(urlView, urlLp);

        if (!payload.reliable()) {
            TextView unreliable = new TextView(terminal.getContext());
            unreliable.setText(tr("gui.resourceobserver.web_access.unreliable_url"));
            unreliable.setTextSize(9 * ModernUiTheme.getTextScale());
            unreliable.setTextColor(UiThemeTokens.AMBER);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ulp.topMargin = terminal.dp(4);
            dialog.addView(unreliable, ulp);
        }

        // 按钮行
        LinearLayout buttonRow = new LinearLayout(terminal.getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = terminal.dp(12);
        dialog.addView(buttonRow, btnRowLp);

        TextView copyBtn = makeButton(terminal, tr("gui.resourceobserver.web_access.copy"), UiThemeTokens.CYAN);
        copyBtn.setOnClickListener(v -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(displayUrl);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.resourceobserver.web_access.copied"), true);
        });
        buttonRow.addView(copyBtn);

        TextView regenBtn = makeButton(terminal, tr("gui.resourceobserver.web_access.regenerate"), UiThemeTokens.AMBER);
        regenBtn.setOnClickListener(v -> {
            terminal.requestWebToken(true);
            // 服务端会重新推一份 WebTokenPayload，触发 show() 再次构建对话框
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = terminal.dp(8);
        buttonRow.addView(regenBtn, rlp);

        TextView closeBtn = makeButton(terminal, tr("gui.resourceobserver.web_access.close"), UiThemeTokens.TEXT);
        closeBtn.setOnClickListener(v -> {
            container.removeView(dim);
            container.removeView(dialog);
            terminal.setDialogOpen(false);
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = terminal.dp(8);
        buttonRow.addView(closeBtn, clp);

        // 点击遮罩关闭
        dim.setOnClickListener(v -> closeBtn.performClick());

        container.addView(dialog, dialogLp);
    }

    private static TextView makeButton(ResourceTerminalFragment terminal, String text, int color) {
        TextView btn = new TextView(terminal.getContext());
        btn.setText(text);
        btn.setTextSize(11 * ModernUiTheme.getTextScale());
        btn.setTextColor(color);
        btn.setPadding(terminal.dp(12), terminal.dp(6), terminal.dp(12), terminal.dp(6));
        btn.setBackground(ModernUiTheme.buttonBackgroundStateful(btn));
        btn.setClickable(true);
        btn.setFocusable(true);
        ModernUiTheme.addPressScaleEffect(btn);
        return btn;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
