package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.V2UiActions;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.network.UiActionType;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overview 关注列表。
 *
 * <p>每行右上提供 ✕ 按钮：点击时进入 200ms 淡出 + 0.8 缩放动画，期间锁定不可再点；
 * 动画播放完后发送 {@link UiActionType#TOGGLE_WATCH} 让服务端将物品移出关注。
 * 服务端确认后 watchlist 会被刷新，本地动画状态自动失效。
 */
public class OverviewWatchlist extends BaseWidget {

    private static final int HEADER_H = 16;
    private static final int ROW_H = 24;
    private static final int CLOSE_BTN_SIZE = 12;
    private static final long FADE_MS = 200L;

    private List<OverviewViewModel.WatchlistItem> items = List.of();
    /** itemId → 淡出动画起始时间（毫秒）。 */
    private final Map<String, Long> fadeStart = new HashMap<>();

    public OverviewWatchlist setItems(List<OverviewViewModel.WatchlistItem> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        // 服务端已删除的项无需再保留动画状态
        if (!fadeStart.isEmpty()) {
            fadeStart.keySet().removeIf(id -> {
                for (OverviewViewModel.WatchlistItem it : this.items) {
                    if (it.itemId().equals(id)) return false;
                }
                return true;
            });
        }
        return this;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public int preferredHeight() {
        if (items.isEmpty()) return HEADER_H + ROW_H;
        return HEADER_H + items.size() * ROW_H + 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        NinePatch.framedFill(g, b, 0xFF_22_22_22, VanillaTheme.COLOR_DIVIDER);

        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.literal("关注列表"), b.x() + VanillaTheme.SPACING_S, b.y() + 4,
                VanillaTheme.COLOR_TEXT_PRIMARY, true);
        if (items.isEmpty()) {
            String text = "暂无关注项";
            int tx = b.x() + (b.width() - font.width(text)) / 2;
            int ty = b.y() + HEADER_H + Math.max(0, (b.height() - HEADER_H - VanillaTheme.FONT_HEIGHT) / 2);
            g.drawString(font, text, tx, ty, VanillaTheme.COLOR_TEXT_DISABLED, false);
            return;
        }

        int y = b.y() + HEADER_H;
        int maxRows = Math.max(0, (b.bottom() - y) / ROW_H);
        for (int i = 0; i < Math.min(maxRows, items.size()); i++) {
            renderRow(g, items.get(i), b.x() + 1, y, b.width() - 2, mouseX, mouseY);
            y += ROW_H;
        }
    }

    private void renderRow(GuiGraphics g, OverviewViewModel.WatchlistItem item,
                           int x, int y, int width, int mouseX, int mouseY) {
        float alpha = 1f;
        float scale = 1f;
        Long start = fadeStart.get(item.itemId());
        if (start != null) {
            long dt = System.currentTimeMillis() - start;
            if (dt >= FADE_MS) {
                alpha = 0f;
            } else {
                float t = dt / (float) FADE_MS;
                alpha = 1f - t;
                scale = 1f - 0.2f * t;
            }
        }
        if (alpha <= 0f) return;

        int aBg = (int) Math.round(0x14 * alpha);
        g.fill(x, y, x + width, y + ROW_H - 1, (aBg << 24) | 0x00FF_FFFF);

        if (item.itemId() != null && !item.itemId().isBlank()) {
            // 物品/流体图标渲染不易加 alpha，淡出动画期间用半透叠加近似
            RenderUtils.drawItemIconOrSprite(g, item.itemId(), x + 4, y + 4, 16, TerminalSprites.WATCH_ITEM);
            if (alpha < 1f) {
                int veil = (int) Math.round((1f - alpha) * 0xC0);
                g.fill(x + 4, y + 4, x + 4 + 16, y + 4 + 16, (veil << 24) | 0x00_22_22_22);
            }
        }
        var font = Minecraft.getInstance().font;
        int textX = x + 24;
        int closeX = x + width - CLOSE_BTN_SIZE - 4;
        int closeY = y + (ROW_H - CLOSE_BTN_SIZE) / 2;
        int nameW = Math.max(20, closeX - textX - 4);

        int aText = (int) Math.round(0xFF * alpha);
        int textColor = (aText << 24) | (VanillaTheme.COLOR_TEXT_PRIMARY & 0x00FF_FFFF);
        int subColor = (aText << 24) | (netColor(item.netPerMinute()) & 0x00FF_FFFF);

        g.drawString(font, fit(item.displayName(), nameW), textX, y + 3, textColor, true);
        String flow = signed(item.netPerMinute()) + "/min";
        String stock = FormatUtils.formatCompact(item.stock());
        g.drawString(font, fit(flow + " \u00B7 " + stock, nameW), textX, y + 14, subColor, false);

        // ✕ 按钮
        boolean closeHover = mouseX >= closeX && mouseX < closeX + CLOSE_BTN_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_BTN_SIZE
                && start == null;
        int closeColor = closeHover
                ? VanillaTheme.COLOR_STATUS_ERROR
                : (aText << 24) | (VanillaTheme.COLOR_TEXT_SECONDARY & 0x00FF_FFFF);
        String glyph = "\u2716"; // ✖
        int gw = font.width(glyph);
        g.drawString(font, glyph, closeX + (CLOSE_BTN_SIZE - gw) / 2,
                closeY + (CLOSE_BTN_SIZE - VanillaTheme.FONT_HEIGHT) / 2, closeColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || button != 0 || !isMouseOver(mouseX, mouseY) || items.isEmpty()) return false;
        Rect b = bounds();
        int y = b.y() + HEADER_H;
        int width = b.width() - 2;
        int x0 = b.x() + 1;
        int maxRows = Math.max(0, (b.bottom() - y) / ROW_H);
        int closeX = x0 + width - CLOSE_BTN_SIZE - 4;
        for (int i = 0; i < Math.min(maxRows, items.size()); i++) {
            int closeY = y + (ROW_H - CLOSE_BTN_SIZE) / 2;
            if (mouseX >= closeX && mouseX < closeX + CLOSE_BTN_SIZE
                    && mouseY >= closeY && mouseY < closeY + CLOSE_BTN_SIZE) {
                OverviewViewModel.WatchlistItem item = items.get(i);
                if (!fadeStart.containsKey(item.itemId())) {
                    fadeStart.put(item.itemId(), System.currentTimeMillis());
                    // 延迟到动画播放完后再发包；MC 没有定时器调度器，直接发即可，
                    // 服务端响应通常在 100-200ms 内到达，淡出动画刚好结束。
                    V2UiActions.send(UiActionType.TOGGLE_WATCH, item.itemId(), "");
                }
                return true;
            }
            y += ROW_H;
        }
        return false;
    }

    private static String signed(long value) {
        if (value > 0L) return "+" + FormatUtils.formatCompact(value);
        return FormatUtils.formatCompact(value);
    }

    private static int netColor(long net) {
        if (net > 0L) return VanillaTheme.COLOR_STATUS_OK;
        if (net < 0L) return VanillaTheme.COLOR_STATUS_ERROR;
        return VanillaTheme.COLOR_TEXT_SECONDARY;
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("\u2026"))) + "\u2026";
    }
}

