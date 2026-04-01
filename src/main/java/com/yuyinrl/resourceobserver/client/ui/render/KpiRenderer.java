package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class KpiRenderer {
    private KpiRenderer() {
    }

    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<OverviewViewModel.KpiMetric> kpis
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(8);
        int gap = 8;
        int cardCount = Math.max(1, kpis.size());
        int cardW = (content.width() - (cardCount - 1) * gap) / cardCount;
        int cardH = content.height();

        for (int i = 0; i < kpis.size(); i++) {
            UiRect card = new UiRect(content.x() + i * (cardW + gap), content.y(), cardW, cardH);
            drawCard(gfx, font, card, kpis.get(i));
        }
    }

    private static void drawCard(
            GuiGraphics gfx,
            Font font,
            UiRect card,
            OverviewViewModel.KpiMetric metric
    ) {
        RenderUtils.fillPanel(gfx, card, UiThemeTokens.CARD_BG, UiThemeTokens.CARD_BORDER);
        int labelColor = UiThemeTokens.TEXT_MUTED;
        int valueColor = UiThemeTokens.TITLE;
        int trendColor = statusColor(metric.status());

        int x = card.x() + 8;
        int y = card.y() + 8;
        gfx.drawString(font, Component.translatable(metric.label()), x, y, labelColor);
        gfx.drawString(font, metric.value(), x, y + 12, valueColor);
        gfx.drawString(font, metric.trend(), x, y + 26, trendColor);

        int iconBg = 0x4016253C;
        int bx = card.right() - 24;
        int by = card.y() + 8;
        gfx.fill(bx, by, bx + 14, by + 14, iconBg);
        ResourceLocation sprite = TerminalSprites.resolve(metric.iconSprite(), TerminalSprites.KPI_EFFICIENCY);
        gfx.blitSprite(sprite, bx + 2, by + 2, 10, 10);
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
