package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Grid load chart renderer.
 */
public final class PowerGridChartRenderer {
    private static final int SECTION_HEIGHT = 128;
    private static final int PLOT_PADDING_TOP = 36;
    private static final int PLOT_PADDING_BOTTOM = 14;
    private static final int PLOT_PADDING_LEFT = 4;
    private static final int PLOT_PADDING_RIGHT = 4;

    private PowerGridChartRenderer() {
    }

    public static int sectionHeight() {
        return SECTION_HEIGHT;
    }

    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            long effectiveInputPerTick,
            long effectiveOutputPerTick,
            long rawInputPerTick,
            long rawOutputPerTick,
            long excludedExternalRate
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.section.grid_load"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.grid_load.subtitle"),
                content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        String genLabel = Component.translatable("screen.resourceobserver.power.generation").getString()
                + ": " + formatCompact(effectiveInputPerTick) + " FE/t";
        int genW = font.width(genLabel);
        gfx.drawString(font, genLabel, content.right() - genW, content.y(), UiThemeTokens.TEXT);

        String loadLabel = Component.translatable("screen.resourceobserver.power.peak_load").getString()
                + ": " + formatCompact(effectiveOutputPerTick) + " FE/t";
        int loadW = font.width(loadLabel);
        gfx.drawString(font, loadLabel, content.right() - loadW, content.y() + 11, UiThemeTokens.TEXT_MUTED);

        String rawHint = Component.translatable("screen.resourceobserver.power.grid_load.raw_hint",
                formatCompact(rawInputPerTick),
                formatCompact(rawOutputPerTick),
                formatCompact(excludedExternalRate)
        ).getString();
        gfx.drawString(font,
                RenderUtils.ellipsis(font, rawHint, content.width()),
                content.x(),
                content.y() + 22,
                UiThemeTokens.TEXT_MUTED);

        int plotX = content.x() + PLOT_PADDING_LEFT;
        int plotY = content.y() + PLOT_PADDING_TOP;
        int plotW = Math.max(40, content.width() - PLOT_PADDING_LEFT - PLOT_PADDING_RIGHT);
        int plotH = Math.max(20, content.height() - PLOT_PADDING_TOP - PLOT_PADDING_BOTTOM);

        gfx.fill(plotX, plotY, plotX + plotW, plotY + plotH, 0x3310182C);

        long maxValue = Math.max(1L, Math.max(effectiveInputPerTick, effectiveOutputPerTick));
        int numPoints = 24;
        for (int i = 0; i < numPoints - 1; i++) {
            int x0 = plotX + (i * plotW) / (numPoints - 1);
            int x1 = plotX + ((i + 1) * plotW) / (numPoints - 1);

            double phase0 = (i / (double) numPoints) * 2.0 * Math.PI;
            double phase1 = ((i + 1) / (double) numPoints) * 2.0 * Math.PI;
            double usage0 = effectiveOutputPerTick * (0.85 + 0.15 * Math.sin(phase0 + 1.0));
            double usage1 = effectiveOutputPerTick * (0.85 + 0.15 * Math.sin(phase1 + 1.0));

            int y0 = plotY + plotH - (int) Math.min(plotH, (usage0 / maxValue) * plotH);
            int y1 = plotY + plotH - (int) Math.min(plotH, (usage1 / maxValue) * plotH);
            int bottom = plotY + plotH;

            int fillColor = 0x553B82F6;
            for (int col = x0; col < x1; col++) {
                double t = (x1 > x0) ? (col - x0) / (double) (x1 - x0) : 0;
                int yInterp = (int) (y0 + t * (y1 - y0));
                gfx.fill(col, yInterp, col + 1, bottom, fillColor);
            }

            RenderUtils.drawLine(gfx, x0, y0, x1, y1, UiThemeTokens.BLUE);
        }

        if (effectiveInputPerTick > 0) {
            int capY = plotY + plotH - (int) Math.min(plotH, ((double) effectiveInputPerTick / maxValue) * plotH);
            capY = Math.max(plotY, Math.min(plotY + plotH - 1, capY));
            for (int dx = 0; dx < plotW; dx++) {
                if ((dx / 4) % 2 == 0) {
                    gfx.fill(plotX + dx, capY, plotX + dx + 1, capY + 1, 0xFFEF4444);
                }
            }
        }

        gfx.drawString(font, "0h", plotX, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, "12h", plotX + plotW / 2 - 6, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, "24h", plotX + plotW - 12, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);
    }

    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            long totalInputPerTick,
            long totalOutputPerTick
    ) {
        render(gfx, font, area, totalInputPerTick, totalOutputPerTick, totalInputPerTick, totalOutputPerTick, 0L);
    }

    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
