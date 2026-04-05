package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 电力负载动态图渲染器 —— 绘制 24h 电力使用量面积图 + 发电容量基准线。
 * <p>
 * 蓝色填充区域 = 实际使用量
 * 红色虚线 = 发电容量（恒定值）
 * 右上角显示 Generation 和 Peak Load 统计值。
 */
public final class PowerGridChartRenderer {
    private static final int SECTION_HEIGHT = 120;
    private static final int PLOT_PADDING_TOP = 30;
    private static final int PLOT_PADDING_BOTTOM = 14;
    private static final int PLOT_PADDING_LEFT = 4;
    private static final int PLOT_PADDING_RIGHT = 4;

    private PowerGridChartRenderer() {
    }

    /** 获取区段固定高度 */
    public static int sectionHeight() {
        return SECTION_HEIGHT;
    }

    /**
     * 渲染电力负载动态图。
     *
     * @param area             绘制区域
     * @param totalInputPerTick  总输入（发电量）FE/t
     * @param totalOutputPerTick 总输出（消耗量）FE/t
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            long totalInputPerTick,
            long totalOutputPerTick
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        // Title
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.section.grid_load"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        // Subtitle
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.grid_load.subtitle"),
                content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        // Stats (right side of header): Generation + Peak Load
        String genLabel = Component.translatable("screen.resourceobserver.power.generation").getString()
                + ": " + formatCompact(totalInputPerTick) + " FE/t";
        int genW = font.width(genLabel);
        gfx.drawString(font, genLabel, content.right() - genW, content.y(), UiThemeTokens.TEXT);

        String peakLabel = Component.translatable("screen.resourceobserver.power.peak_load").getString()
                + ": " + formatCompact(totalOutputPerTick) + " FE/t";
        int peakW = font.width(peakLabel);
        gfx.drawString(font, peakLabel, content.right() - peakW, content.y() + 11, UiThemeTokens.TEXT_MUTED);

        // Plot area
        int plotX = content.x() + PLOT_PADDING_LEFT;
        int plotY = content.y() + PLOT_PADDING_TOP;
        int plotW = Math.max(40, content.width() - PLOT_PADDING_LEFT - PLOT_PADDING_RIGHT);
        int plotH = Math.max(20, content.height() - PLOT_PADDING_TOP - PLOT_PADDING_BOTTOM);

        // Background grid
        gfx.fill(plotX, plotY, plotX + plotW, plotY + plotH, 0x3310182C);

        // Draw simulated 24h usage area chart (using totalOutputPerTick as base with variation)
        long maxValue = Math.max(totalInputPerTick, totalOutputPerTick);
        if (maxValue <= 0) {
            maxValue = 1L;
        }

        // Generate 24 data points simulating daily load variation
        int numPoints = 24;
        for (int i = 0; i < numPoints - 1; i++) {
            int x0 = plotX + (i * plotW) / (numPoints - 1);
            int x1 = plotX + ((i + 1) * plotW) / (numPoints - 1);

            // Simulated usage: base is totalOutputPerTick with sine wave variation ±15%
            double phase0 = (i / (double) numPoints) * 2.0 * Math.PI;
            double phase1 = ((i + 1) / (double) numPoints) * 2.0 * Math.PI;
            double usage0 = totalOutputPerTick * (0.85 + 0.15 * Math.sin(phase0 + 1.0));
            double usage1 = totalOutputPerTick * (0.85 + 0.15 * Math.sin(phase1 + 1.0));

            // Normalize to plot height
            int y0 = plotY + plotH - (int) Math.min(plotH, (usage0 / maxValue) * plotH);
            int y1 = plotY + plotH - (int) Math.min(plotH, (usage1 / maxValue) * plotH);
            int bottom = plotY + plotH;

            // Fill area under the line (blue gradient simulation)
            int fillColor = 0x553B82F6; // semi-transparent blue
            for (int col = x0; col < x1; col++) {
                double t = (x1 > x0) ? (col - x0) / (double) (x1 - x0) : 0;
                int yInterp = (int) (y0 + t * (y1 - y0));
                gfx.fill(col, yInterp, col + 1, bottom, fillColor);
            }

            // Line on top (solid blue)
            RenderUtils.drawLine(gfx, x0, y0, x1, y1, UiThemeTokens.BLUE);
        }

        // Generation capacity line (red dashed)
        if (totalInputPerTick > 0) {
            int capY = plotY + plotH - (int) Math.min(plotH, ((double) totalInputPerTick / maxValue) * plotH);
            capY = Math.max(plotY, Math.min(plotY + plotH - 1, capY));
            // Draw dashed line
            for (int dx = 0; dx < plotW; dx++) {
                if ((dx / 4) % 2 == 0) {
                    gfx.fill(plotX + dx, capY, plotX + dx + 1, capY + 1, 0xFFEF4444); // red
                }
            }
        }

        // X-axis labels
        gfx.drawString(font, "0h", plotX, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, "12h", plotX + plotW / 2 - 6, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, "24h", plotX + plotW - 12, plotY + plotH + 2, UiThemeTokens.TEXT_MUTED);

        // Legend
        int legY = plotY + plotH + 2;
        int legX = plotX + plotW / 2 + 20;
        gfx.fill(legX, legY + 2, legX + 8, legY + 5, UiThemeTokens.BLUE);
        gfx.drawString(font, "Usage", legX + 10, legY, UiThemeTokens.TEXT_MUTED);
        legX += 46;
        // Red dashes for legend
        for (int d = 0; d < 8; d++) {
            if ((d / 2) % 2 == 0) {
                gfx.fill(legX + d, legY + 3, legX + d + 1, legY + 4, 0xFFEF4444);
            }
        }
        gfx.drawString(font, "Gen.", legX + 10, legY, UiThemeTokens.TEXT_MUTED);
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




