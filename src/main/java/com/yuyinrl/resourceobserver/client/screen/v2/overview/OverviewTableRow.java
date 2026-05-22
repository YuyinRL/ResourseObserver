package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.StarToggleButton;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Overview 表格物品行。
 *
 * <p>三态背景：normal（半透白）/ hover（更亮）/ critical（红底）/ critical-hover（红底+白）。
 * 行首星标：点击切换关注，复用 {@link StarToggleButton}。
 */
public class OverviewTableRow extends BaseWidget {

    private static final int STAR_SIZE = 12;

    private static final int BG_NORMAL = 0x16_FF_FF_FF;
    private static final int BG_HOVER = 0x30_FF_FF_FF;
    private static final int BG_CRITICAL = 0x40_E0_55_55;
    private static final int BG_CRITICAL_HOVER = 0x60_E0_55_55;

    private final OverviewViewModel.TableRow row;
    private final java.util.List<OverviewViewModel.GroupOption> groups;
    private final StarToggleButton star;

    public OverviewTableRow(OverviewViewModel.TableRow row,
                            java.util.List<OverviewViewModel.GroupOption> groups) {
        this.row = row;
        this.groups = groups == null ? java.util.List.of() : groups;
        this.star = new StarToggleButton()
                .setItemId(row == null ? "" : row.itemId())
                .setStarred(row != null && row.starred());
    }

    public OverviewTableRow(OverviewViewModel.TableRow row) {
        this(row, java.util.List.of());
    }

    @Override
    public void setBounds(Rect rect) {
        super.setBounds(rect);
        Rect b = bounds();
        int sy = b.y() + Math.max(0, (b.height() - STAR_SIZE) / 2);
        star.setBounds(new Rect(b.x() + 4, sy, STAR_SIZE, STAR_SIZE));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || row == null || !isMouseOver(mouseX, mouseY)) return false;
        if (button == 0 && star.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 1) {
            OverviewMenuBuilder.openRowMenu((int) mouseX, (int) mouseY, row, groups);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible() || row == null) return;
        Rect b = bounds();
        boolean hovered = b.contains(mouseX, mouseY);
        int bg;
        if (row.critical()) {
            bg = hovered ? BG_CRITICAL_HOVER : BG_CRITICAL;
        } else {
            bg = hovered ? BG_HOVER : BG_NORMAL;
        }
        g.fill(b.x(), b.y(), b.right(), b.bottom(), bg);
        int accent = row.critical()
                ? VanillaTheme.COLOR_STATUS_ERROR
                : (row.starred() ? VanillaTheme.COLOR_STATUS_WARN : VanillaTheme.COLOR_DIVIDER);
        g.fill(b.x(), b.y(), b.x() + 2, b.bottom(), accent);

        // 行首：星标
        star.render(g, mouseX, mouseY, partialTick);

        int iconX = star.bounds().right() + VanillaTheme.SPACING_XS;
        int iconY = b.y() + Math.max(0, (b.height() - VanillaTheme.ICON_SIZE) / 2);
        if (row != null && row.itemId() != null && !row.itemId().isBlank()) {
            RenderUtils.drawItemIconOrSprite(g, row.itemId(), iconX, iconY, VanillaTheme.ICON_SIZE, TerminalSprites.TABLE_ITEM);
        }

        var font = Minecraft.getInstance().font;
        int y = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
        int x = iconX + VanillaTheme.ICON_SIZE + VanillaTheme.SPACING_S;
        int stockW = 54;
        int netW = 50;
        int flowW = 48;
        int gap = VanillaTheme.SPACING_S;
        int nameW = Math.max(24, b.right() - x - stockW - netW - flowW * 2 - gap * 4);
        int textColor = row.critical()
                ? VanillaTheme.COLOR_STATUS_ERROR
                : (row.starred() ? VanillaTheme.COLOR_STATUS_WARN : VanillaTheme.COLOR_TEXT_PRIMARY);

        g.drawString(font, fit(row.displayName(), nameW), x, y, textColor, true);
        x += nameW + gap;
        g.drawString(font, signed(row.production()), x, y, VanillaTheme.COLOR_STATUS_OK, false);
        x += flowW + gap;
        g.drawString(font, signed(-row.consumption()), x, y, VanillaTheme.COLOR_STATUS_ERROR, false);
        x += flowW + gap;
        g.drawString(font, signed(row.net()), x, y, netColor(row.net()), false);
        x += netW + gap;
        g.drawString(font, FormatUtils.formatCompact(row.stock()), x, y, VanillaTheme.COLOR_TEXT_SECONDARY, false);
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

