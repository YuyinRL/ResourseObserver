package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Power 过载告警单行。
 */
public class PowerOverloadAlertRow extends BaseWidget {

    private final PowerNetworkViewModel.OverloadAlert alert;

    public PowerOverloadAlertRow(PowerNetworkViewModel.OverloadAlert alert) {
        this.alert = alert;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible() || alert == null) return;
        Rect b = bounds();
        int color = alertColor(alert.alertLevel());
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x28_FF_FF_FF);
        g.fill(b.x(), b.y(), b.x() + 3, b.bottom(), color);

        var font = Minecraft.getInstance().font;
        int x = b.x() + 7;
        int y = b.y() + 3;
        g.drawString(font, alert.alertLevel() == PowerNetworkViewModel.AlertLevel.CRITICAL ? "!" : "△", x, y,
                color, true);
        x += 12;

        int lossW = 44;
        int nameW = Math.max(46, b.width() / 4);
        int descW = Math.max(40, b.right() - x - nameW - lossW - VanillaTheme.SPACING_M);
        g.drawString(font, fit(alert.displayName(), nameW), x, y, color, true);
        x += nameW + VanillaTheme.SPACING_S;
        g.drawString(font, fit(alert.description(), descW), x, y, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        String loss = throughputLossText(alert.throughputLoss());
        g.drawString(font, loss, b.right() - font.width(loss) - 4, y, color, true);
    }

    private static String throughputLossText(double value) {
        double percent = value > 1.0 ? value : value * 100.0;
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    private static int alertColor(PowerNetworkViewModel.AlertLevel level) {
        if (level == null) return VanillaTheme.COLOR_STATUS_WARN;
        return switch (level) {
            case NORMAL -> VanillaTheme.COLOR_STATUS_OK;
            case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
            case CRITICAL -> VanillaTheme.COLOR_STATUS_ERROR;
        };
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }
}
