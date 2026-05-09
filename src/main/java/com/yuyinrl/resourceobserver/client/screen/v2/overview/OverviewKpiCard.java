package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Overview 页 KPI 卡片。
 */
public class OverviewKpiCard extends BaseWidget {

    private OverviewViewModel.KpiMetric kpi;
    private boolean selected;

    public OverviewKpiCard setKpi(OverviewViewModel.KpiMetric kpi) {
        this.kpi = kpi;
        return this;
    }

    public OverviewKpiCard setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public OverviewViewModel.KpiMetric kpi() {
        return kpi;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        int fill = selected ? VanillaTheme.COLOR_PANEL_ACCENT : 0xFF_24_24_24;
        NinePatch.framedFill(g, b, fill, selected ? statusColor(status()) : VanillaTheme.COLOR_DIVIDER);
        if (kpi == null) return;

        var font = Minecraft.getInstance().font;
        String label = kpi.label() == null ? "" : kpi.label();
        String value = kpi.value() == null ? "" : kpi.value();
        String trend = kpi.trend() == null ? "" : kpi.trend();
        int x = b.x() + VanillaTheme.SPACING_S;
        int width = b.width() - VanillaTheme.SPACING_M;
        int lineH = VanillaTheme.FONT_HEIGHT + 4;
        int textY = b.y() + Math.max(4, (b.height() - lineH * (trend.isBlank() ? 2 : 3)) / 2);
        g.drawString(font, fit(label, width), x, textY, VanillaTheme.COLOR_TEXT_SECONDARY, true);
        textY += lineH;
        g.drawString(font, fit(value, width), x, textY, statusColor(kpi.status()), true);
        if (!trend.isBlank()) {
            textY += lineH;
            g.drawString(font, fit(trend, width), x, textY, VanillaTheme.COLOR_TEXT_DISABLED, false);
        }
    }

    private OverviewViewModel.Status status() {
        return kpi == null ? OverviewViewModel.Status.NEUTRAL : kpi.status();
    }

    private static int statusColor(OverviewViewModel.Status status) {
        if (status == null) return VanillaTheme.COLOR_TEXT_PRIMARY;
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
