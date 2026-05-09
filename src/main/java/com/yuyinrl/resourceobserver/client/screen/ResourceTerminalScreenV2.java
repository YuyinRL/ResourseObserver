package com.yuyinrl.resourceobserver.client.screen;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Column;
import com.yuyinrl.resourceobserver.client.screen.widget.Panel;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.Tabs;
import com.yuyinrl.resourceobserver.client.screen.widget.TextLabel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Resource Terminal V2 —— 新 Vanilla UI 框架的终端 Screen 骨架。
 *
 * <p>F0.C 仅展示 Tabs + 三个空 placeholder（Overview / Storage / Power），
 * 用于验证布局/事件管线工作正常；后续 F1/F2/F3 会逐个领域接入 Snapshot 数据。</p>
 */
public class ResourceTerminalScreenV2 extends Screen {

    private static final int CONTENT_PADDING = 16;
    private static final int CONTENT_MAX_WIDTH = 480;
    private static final int CONTENT_MAX_HEIGHT = 280;

    private Tabs tabs;
    private Rect contentRect = Rect.EMPTY;

    public ResourceTerminalScreenV2() {
        super(Component.translatable("screen.resourceobserver.terminal_title"));
    }

    @Override
    protected void init() {
        super.init();

        int w = Math.min(CONTENT_MAX_WIDTH, this.width - CONTENT_PADDING * 2);
        int h = Math.min(CONTENT_MAX_HEIGHT, this.height - CONTENT_PADDING * 2);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        this.contentRect = new Rect(x, y, w, h);

        tabs = new Tabs(VanillaTheme.TAB_HEIGHT)
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.overview"), buildPlaceholder("Overview · F1 待接入 Snapshot"))
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.storage_network"), buildPlaceholder("Storage · F2 待接入"))
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.power_network"), buildPlaceholder("Power · F3 待接入"));
        tabs.setBounds(contentRect.shrink(VanillaTheme.SPACING_S));

        addRenderableWidget(tabs);
    }

    /** 构造一个填满 panel、内容垂直居中的占位文本面板。 */
    private Panel buildPlaceholder(String text) {
        Column col = new Column(VanillaTheme.SPACING_M)
                .addFlexSpacer(1)
                .addFixed(new TextLabel(Component.literal(text))
                                .setAlign(TextLabel.HAlign.CENTER)
                                .setColor(VanillaTheme.COLOR_TEXT_SECONDARY),
                        VanillaTheme.FONT_HEIGHT)
                .addFlexSpacer(1);
        Panel p = new Panel() {
            @Override
            public void setBounds(Rect rect) {
                super.setBounds(rect);
                col.setBounds(rect);
            }
        };
        p.add(col);
        return p;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 先绘半透黑背景遮罩
        g.fill(0, 0, this.width, this.height, VanillaTheme.COLOR_BG_DIM);
        // 内容区面板背景
        NinePatch.framedFill(g, contentRect,
                VanillaTheme.COLOR_PANEL,
                VanillaTheme.COLOR_BUTTON_BORDER);
        // 标题
        g.drawCenteredString(this.font, this.title,
                contentRect.x() + contentRect.width() / 2,
                contentRect.y() + VanillaTheme.SPACING_S,
                VanillaTheme.COLOR_TEXT_PRIMARY);
        // 由 Screen 渲染已 add 的 widgets（包括 tabs）
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
