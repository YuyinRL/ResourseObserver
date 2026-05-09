package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.crafting.CraftingAvailability;
import com.yuyinrl.resourceobserver.client.screen.v2.crafting.CraftOrderFlow;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.StarToggleButton;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.compact;

/**
 * 存储物品表格单行。
 */
public class StorageItemRow extends BaseWidget {

    private static final int STAR_SIZE = 12;

    private static final int BG_NORMAL = 0x18_FF_FF_FF;
    private static final int BG_HOVER = 0x30_FF_FF_FF;
    private static final int BG_CRITICAL = 0x40_E0_55_55;
    private static final int BG_CRITICAL_HOVER = 0x60_E0_55_55;

    private final StorageNetworkViewModel.ItemRow item;
    private final StarToggleButton star;

    public StorageItemRow(StorageNetworkViewModel.ItemRow item) {
        this(item, false);
    }

    public StorageItemRow(StorageNetworkViewModel.ItemRow item, boolean starred) {
        this.item = item;
        this.star = new StarToggleButton().setItemId(item.itemId()).setStarred(starred);
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
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        if (button == 0 && star.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1) {
            CraftingAvailability.Match craftable = CraftingAvailability.find(item.itemId());
            if (!craftable.craftable()) {
                return false;
            }
            CraftOrderFlow.start(item.itemId(), item.displayName(), craftable.networkId());
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        boolean hovered = b.contains(mouseX, mouseY);
        boolean critical = item.alertLevel() == StorageNetworkViewModel.AlertLevel.RED;
        int bg;
        if (critical) {
            bg = hovered ? BG_CRITICAL_HOVER : BG_CRITICAL;
        } else {
            bg = hovered ? BG_HOVER : BG_NORMAL;
        }
        g.fill(b.x(), b.y(), b.right(), b.bottom(), bg);
        g.fill(b.x(), b.y(), b.x() + 3, b.bottom(), alertColor(item.alertLevel()));

        // 行首：星标
        star.render(g, mouseX, mouseY, partialTick);

        int iconX = star.bounds().right() + VanillaTheme.SPACING_XS;
        int iconY = b.y() + Math.max(0, (b.height() - VanillaTheme.ICON_SIZE) / 2);
        if (item.itemId() != null && !item.itemId().isBlank()) {
            RenderUtils.drawItemIconOrSprite(g, item.itemId(), iconX, iconY,
                    VanillaTheme.ICON_SIZE, TerminalSprites.TABLE_ITEM);
        }

        var font = Minecraft.getInstance().font;
        int x = iconX + VanillaTheme.ICON_SIZE + VanillaTheme.SPACING_S;
        int amountW = 68;
        int globalW = 68;
        int burnW = 44;
        int bufferW = 48;
        int gap = VanillaTheme.SPACING_S;
        int nameW = Math.max(30, b.right() - x - amountW - globalW - burnW - bufferW - gap * 4);
        int ty = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;

        g.drawString(font, fit(item.displayName(), nameW), x, ty, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        x += nameW + gap;
        g.drawString(font, compact(item.localAmount()), x, ty,
                VanillaTheme.COLOR_TEXT_SECONDARY, true);
        x += amountW + gap;
        g.drawString(font, compact(item.globalAmount()), x, ty,
                VanillaTheme.COLOR_TEXT_SECONDARY, true);
        x += globalW + gap;
        g.drawString(font, compact(Math.round(item.burnRatePerMin())), x, ty,
                burnColor(item.burnRatePerMin()), true);
        x += burnW + gap;
        g.drawString(font, item.estimatedBufferText(), x, ty, alertColor(item.alertLevel()), true);
    }

    private static int alertColor(StorageNetworkViewModel.AlertLevel level) {
        if (level == null) return VanillaTheme.COLOR_STATUS_OK;
        return switch (level) {
            case GREEN -> VanillaTheme.COLOR_STATUS_OK;
            case YELLOW -> VanillaTheme.COLOR_STATUS_WARN;
            case RED -> VanillaTheme.COLOR_STATUS_ERROR;
        };
    }

    private static int burnColor(double burnRate) {
        return burnRate > 0.0 ? VanillaTheme.COLOR_STATUS_WARN : VanillaTheme.COLOR_TEXT_DISABLED;
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }
}
