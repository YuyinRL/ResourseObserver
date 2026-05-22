package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 存储页 KPI 卡片。
 */
public class StorageKpiCard extends BaseWidget {

    private StorageNetworkViewModel.StorageKpi kpi;

    public StorageKpiCard setKpi(StorageNetworkViewModel.StorageKpi kpi) {
        this.kpi = kpi;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        NinePatch.framedFill(g, b, 0xFF_24_24_24, VanillaTheme.COLOR_DIVIDER);
        if (kpi == null) return;

        var font = Minecraft.getInstance().font;
        String label = Component.translatable(kpi.label()).getString();
        String value = kpi.value() == null ? "" : kpi.value();
        int lineH = VanillaTheme.FONT_HEIGHT + 4;
        int y = b.y() + Math.max(4, (b.height() - lineH * 2) / 2);
        g.drawString(font, fit(label, b.width() - 8), b.x() + 4, y,
                VanillaTheme.COLOR_TEXT_SECONDARY, true);
        g.drawString(font, fit(value, b.width() - 8), b.x() + 4, y + lineH,
                statusColor(kpi.status()), true);
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
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
