package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 页眉渲染器 —— 绘制终端界面顶部的标题栏和连接状态卡片。
 * 显示内容：标题文本、副标题、连接状态指示灯和观察者坐标。
 */
public final class HeaderRenderer {
    private HeaderRenderer() {
    }

    /**
     * 渲染页眉区域。
     * @param header      页眉矩形区域
     * @param observerPos 观察者方块坐标（显示在状态卡片中）
     * @param linked      是否已绑定网络（决定状态颜色：绿=已连接，红=未连接）
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect header,
            BlockPos observerPos,
            boolean linked
    ) {
        // 绘制页眉背景和边框
        RenderUtils.fillPanel(gfx, header, UiThemeTokens.HEADER_BG, UiThemeTokens.SECTION_BORDER);

        // 左侧：标题和副标题
        int titleX = header.x() + 12;
        int titleY = header.y() + 8;
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.header.title"), titleX, titleY, UiThemeTokens.TITLE);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.header.subtitle"), titleX, titleY + 12, UiThemeTokens.TEXT_MUTED);

        // 右侧：连接状态卡片（响应式宽度计算）
        int padding = 10;
        int minCardW = 160;
        int desiredCardW = 250;
        int maxCardW = Math.min(300, Math.max(minCardW, header.width() / 2));
        int cardW = Math.max(minCardW, Math.min(desiredCardW, maxCardW));
        int cardX = header.right() - padding - cardW;
        int leftLimit = header.x() + header.width() / 2;
        if (cardX < leftLimit) {
            cardX = leftLimit;
            cardW = Math.max(minCardW, header.right() - padding - cardX);
        }
        int cardH = 28;
        int cardY = header.y() + 6;
        UiRect card = new UiRect(cardX, cardY, cardW, cardH);
        gfx.fill(card.x(), card.y(), card.right(), card.bottom(), 0xE0182A41);
        RenderUtils.drawBorder(gfx, card, linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE);

        int dotColor = linked ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
        UiRect dot = new UiRect(card.x() + 6, card.y() + 6, 5, 5);
        gfx.fill(dot.x(), dot.y(), dot.right(), dot.bottom(), dotColor);

        String statusText = Component.translatable(
                linked
                        ? "screen.resourceobserver.overview.link_status"
                        : "screen.resourceobserver.overview.link_status_unbound"
        ).getString();
        String coordsText = Component.translatable(
                "screen.resourceobserver.overview.header.coords",
                observerPos.getX(),
                observerPos.getY(),
                observerPos.getZ()
        ).getString();
        int textX = card.x() + 14;
        int textW = card.width() - 18;
        gfx.drawString(font, RenderUtils.ellipsis(font, statusText, textW), textX, card.y() + 3, linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE);
        gfx.drawString(font, RenderUtils.ellipsis(font, coordsText, textW), textX, card.y() + 15, UiThemeTokens.TEXT_MUTED);

        gfx.blitSprite(TerminalSprites.HEADER_STATUS, card.right() - 12, card.y() + 4, 8, 8);
    }
}
