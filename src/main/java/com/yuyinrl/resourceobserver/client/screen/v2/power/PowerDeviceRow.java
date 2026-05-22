package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ProgressBar;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Power 设备表格单行。
 */
public class PowerDeviceRow extends BaseWidget {

    private final PowerNetworkViewModel.DeviceEntry device;
    private final ProgressBar usageBar = new ProgressBar();

    public PowerDeviceRow(PowerNetworkViewModel.DeviceEntry device) {
        this.device = device;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible() || device == null) return;
        Rect b = bounds();
        boolean hovered = b.contains(mouseX, mouseY);
        g.fill(b.x(), b.y(), b.right(), b.bottom(), hovered ? 0x30_FF_FF_FF : 0x18_FF_FF_FF);
        g.fill(b.x(), b.y(), b.x() + 3, b.bottom(), alertColor(device.alertLevel()));

        var font = Minecraft.getInstance().font;
        int y = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
        int x = b.x() + 6;
        g.drawString(font, iconFor(device.category()), x, y, categoryColor(device.category()), true);
        x += 14;

        int statusW = 44;
        int capacityW = 78;
        int energyW = 62;
        int categoryW = 54;
        int gap = VanillaTheme.SPACING_S;
        int nameW = Math.max(34, b.right() - x - statusW - capacityW - energyW - categoryW - gap * 4);
        g.drawString(font, fit(device.displayName(), nameW), x, y, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        x += nameW + gap;

        String category = categoryText(device.category());
        g.drawString(font, fit(category, categoryW), x, y, categoryColor(device.category()), false);
        x += categoryW + gap;

        g.drawString(font, energyText(device.energyPerTick()), x, y, VanillaTheme.COLOR_STATUS_INFO, false);
        x += energyW + gap;

        usageBar.setProgress(device.usageRatio()).setFillColor(alertColor(device.alertLevel()));
        usageBar.setBounds(new Rect(x, b.y() + 6, capacityW, 6));
        usageBar.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, storedText(device), x, b.y() + 14, VanillaTheme.COLOR_TEXT_DISABLED, false);
        x += capacityW + gap;

        Rect pillRect = new Rect(x, b.y() + 5, statusW, 12);
        renderPill(g, pillRect, statusText(device.alertLevel()), alertColor(device.alertLevel()));
    }

    private static String energyText(long value) {
        return FormatUtils.formatCompact(value) + " FE/t";
    }

    private static String storedText(PowerNetworkViewModel.DeviceEntry device) {
        return FormatUtils.formatCompact(device.storedEnergy()) + "/" + FormatUtils.formatCompact(device.maxCapacity());
    }

    private static void renderPill(GuiGraphics g, Rect rect, String text, int color) {
        var font = Minecraft.getInstance().font;
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xFF_1B_1B_1B);
        g.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), color);
        int tx = rect.x() + Math.max(3, (rect.width() - font.width(text)) / 2);
        g.drawString(font, fit(text, rect.width() - 4), tx, rect.y() + 2, color, true);
    }

    private static String statusText(PowerNetworkViewModel.AlertLevel level) {
        String key = switch (level == null ? PowerNetworkViewModel.AlertLevel.NORMAL : level) {
            case NORMAL -> "screen.resourceobserver.power.status.normal";
            case WARNING -> "screen.resourceobserver.power.status.warning";
            case CRITICAL -> "screen.resourceobserver.power.status.critical";
        };
        return Component.translatable(key).getString();
    }

    private static int alertColor(PowerNetworkViewModel.AlertLevel level) {
        if (level == null) return VanillaTheme.COLOR_STATUS_OK;
        return switch (level) {
            case NORMAL -> VanillaTheme.COLOR_STATUS_OK;
            case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
            case CRITICAL -> VanillaTheme.COLOR_STATUS_ERROR;
        };
    }

    private static String iconFor(String category) {
        return switch (normalizedCategory(category)) {
            case "mining" -> "⛏";
            case "assembly" -> "⚙";
            case "logistics" -> "⇄";
            default -> "•";
        };
    }

    private static String categoryText(String category) {
        String key = switch (normalizedCategory(category)) {
            case "mining" -> "screen.resourceobserver.power.category.mining";
            case "assembly" -> "screen.resourceobserver.power.category.assembly";
            case "logistics" -> "screen.resourceobserver.power.category.logistics";
            default -> "screen.resourceobserver.power.category.other";
        };
        return Component.translatable(key).getString();
    }

    private static int categoryColor(String category) {
        return switch (normalizedCategory(category)) {
            case "mining" -> VanillaTheme.COLOR_STATUS_ERROR;
            case "assembly" -> VanillaTheme.COLOR_STATUS_WARN;
            case "logistics" -> VanillaTheme.COLOR_STATUS_INFO;
            default -> VanillaTheme.COLOR_TEXT_DISABLED;
        };
    }

    private static String normalizedCategory(String category) {
        return category == null ? "other" : category.toLowerCase(Locale.ROOT);
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }
}
