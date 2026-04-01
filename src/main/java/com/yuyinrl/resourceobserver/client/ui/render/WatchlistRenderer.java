package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WatchlistRenderer {
    private WatchlistRenderer() {
    }

    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<OverviewViewModel.WatchlistItem> items,
            String selectedItemId
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(8);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.section.watchlist"), content.x(), content.y(), UiThemeTokens.TEXT);

        UiRect listArea = new UiRect(content.x(), content.y() + 14, content.width(), Math.max(20, content.height() - 16));
        int maxColumns = listArea.width() >= 640 ? 3 : (listArea.width() >= 420 ? 2 : 1);
        int cardCount = Math.max(1, Math.min(maxColumns, items.size()));
        int gap = 8;
        int cardW = (listArea.width() - (cardCount - 1) * gap) / cardCount;
        int cardH = listArea.height();

        List<Hitbox> hitboxes = new ArrayList<>();
        List<RemoveHitbox> removeHitboxes = new ArrayList<>();
        for (int i = 0; i < cardCount && i < items.size(); i++) {
            OverviewViewModel.WatchlistItem item = items.get(i);
            UiRect card = new UiRect(listArea.x() + i * (cardW + gap), listArea.y(), cardW, cardH);
            boolean selected = item.itemId().equals(selectedItemId);
            UiRect removeRect = drawCard(gfx, font, card, item, selected);
            hitboxes.add(new Hitbox(card, item.itemId()));
            removeHitboxes.add(new RemoveHitbox(removeRect, item.itemId()));
        }
        return new RenderResult(hitboxes, removeHitboxes);
    }

    private static UiRect drawCard(
            GuiGraphics gfx,
            Font font,
            UiRect card,
            OverviewViewModel.WatchlistItem item,
            boolean selected
    ) {
        int bg = selected ? 0xCC142338 : UiThemeTokens.CARD_BG;
        int border = selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER;
        RenderUtils.fillPanel(gfx, card, bg, border);
        int x = card.x() + 7;
        int y = card.y() + 6;

        RenderUtils.drawItemIconOrSprite(
                gfx,
                item.itemId(),
                x,
                y,
                10,
                TerminalSprites.resolve(item.iconSprite(), TerminalSprites.WATCH_ITEM)
        );
        gfx.drawString(font, RenderUtils.ellipsis(font, item.displayName(), card.width() - 28), x + 12, y + 1, UiThemeTokens.TEXT);

        UiRect removeRect = new UiRect(card.right() - 14, y - 1, 10, 10);
        gfx.fill(removeRect.x(), removeRect.y(), removeRect.right(), removeRect.bottom(), 0x552A1018);
        RenderUtils.drawBorder(gfx, removeRect, 0xAA7F1D28);
        gfx.drawString(font, "x", removeRect.x() + 3, removeRect.y() + 1, UiThemeTokens.ROSE);

        y += 14;
        int netColor = item.netPerMinute() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
        String netText = (item.netPerMinute() >= 0 ? "+" : "") + item.netPerMinute() + "/min";
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.watchlist.net", netText), x, y, netColor);
        y += 11;
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.watchlist.stock", item.stock(), item.capacity()), x, y, UiThemeTokens.TEXT_MUTED);
        y += 11;
        double ratio = item.capacity() > 0 ? (double) item.stock() / (double) item.capacity() : 0.0;
        RenderUtils.drawProgressBar(gfx, x, y, card.width() - 14, 3, ratio, 0xFF1E293B, UiThemeTokens.CYAN);
        return removeRect;
    }

    public record Hitbox(UiRect rect, String itemId) {
    }

    public record RemoveHitbox(UiRect rect, String itemId) {
    }

    public record RenderResult(List<Hitbox> itemHitboxes, List<RemoveHitbox> removeHitboxes) {
    }
}
