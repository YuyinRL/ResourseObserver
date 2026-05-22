package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Power 页 KPI 卡片。
 */
public class PowerKpiCard extends BaseWidget {

    private PowerNetworkViewModel.PowerKpi kpi;

    public PowerKpiCard setKpi(PowerNetworkViewModel.PowerKpi kpi) {
        this.kpi = kpi;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        NinePatch.framedFill(g, b, 0xFF_24_24_24, statusColor(kpi == null ? null : kpi.status()));
        if (kpi == null) return;

        var font = Minecraft.getInstance().font;
        String label = kpi.label() == null ? "" : kpi.label();
        String value = kpi.value() == null ? "" : kpi.value();
        int width = b.width() - VanillaTheme.SPACING_M;
        int lineH = VanillaTheme.FONT_HEIGHT + 4;
        int y = b.y() + Math.max(4, (b.height() - lineH * 2) / 2);
        g.drawString(font, fit(label, width), b.x() + VanillaTheme.SPACING_S, y,
                VanillaTheme.COLOR_TEXT_SECONDARY, true);
        g.drawString(font, fit(value, width), b.x() + VanillaTheme.SPACING_S, y + lineH,
                statusColor(kpi.status()), true);
    }

    private static int statusColor(OverviewViewModel.Status status) {
        if (status == null) return VanillaTheme.COLOR_DIVIDER;
        return switch (status) {
            case POSITIVE -> VanillaTheme.COLOR_STATUS_OK;
            case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
            case NEGATIVE -> VanillaTheme.COLOR_STATUS_ERROR;
            case NEUTRAL -> VanillaTheme.COLOR_TEXT_PRIMARY;
        };
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }
}
