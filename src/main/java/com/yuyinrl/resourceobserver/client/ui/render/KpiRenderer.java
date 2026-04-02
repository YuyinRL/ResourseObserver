package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * KPI 指标卡渲染器 —— 绘制四个关键性能指标卡片。
 * 每张卡片显示：指标标签、数值、趋势文本和小图标。
 * 卡片等宽排列，根据状态枚举决定趋势文本颜色。
 */
public final class KpiRenderer {
    private KpiRenderer() {
    }

    /**
     * 渲染 KPI 区域。
     * 将区域等分为 N 张卡片（N = kpis.size()），间距 8px。
     */
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

    /**
     * 绘制单张 KPI 卡片。
     * 布局：左侧文本（标签/数值/趋势），右上角图标。
     */
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
        int y = card.y() + 6;
        int textMaxWidth = Math.max(24, card.width() - 40);
        List<TextLine> lines = new ArrayList<>();
        lines.add(new TextLine(Component.translatable(metric.label()).getString(), labelColor));
        appendMultiline(lines, metric.value(), valueColor);
        appendMultiline(lines, metric.trend(), trendColor);
        int textClipRight = card.right() - 28;
        int lineHeight = 9;
        int maxLines = Math.max(1, (card.height() - 12) / lineHeight);
        if (textClipRight > x + 4) {
            gfx.enableScissor(x, card.y() + 2, textClipRight, card.bottom() - 2);
            int lineCount = Math.min(lines.size(), maxLines);
            for (int i = 0; i < lineCount; i++) {
                TextLine line = lines.get(i);
                String text = RenderUtils.ellipsis(font, line.text(), textMaxWidth);
                gfx.drawString(font, text, x, y + i * lineHeight, line.color());
            }
            gfx.disableScissor();
        } else {
            int lineCount = Math.min(lines.size(), maxLines);
            for (int i = 0; i < lineCount; i++) {
                TextLine line = lines.get(i);
                String text = RenderUtils.ellipsis(font, line.text(), textMaxWidth);
                gfx.drawString(font, text, x, y + i * lineHeight, line.color());
            }
        }

        int iconBg = 0x4016253C;
        int bx = card.right() - 24;
        int by = card.y() + 8;
        gfx.fill(bx, by, bx + 14, by + 14, iconBg);
        ResourceLocation sprite = TerminalSprites.resolve(metric.iconSprite(), TerminalSprites.KPI_EFFICIENCY);
        gfx.blitSprite(sprite, bx + 2, by + 2, 10, 10);
    }

    /** 根据状态枚举返回对应的颜色值 */
    private static int statusColor(OverviewViewModel.Status status) {
        return switch (status) {
            case POSITIVE -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case NEGATIVE -> UiThemeTokens.ROSE;
            case NEUTRAL -> UiThemeTokens.TEXT;
        };
    }

    private static void appendMultiline(List<TextLine> lines, String text, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] parts = text.split("\\R");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            lines.add(new TextLine(part, color));
        }
    }

    private record TextLine(String text, int color) {
    }
}
