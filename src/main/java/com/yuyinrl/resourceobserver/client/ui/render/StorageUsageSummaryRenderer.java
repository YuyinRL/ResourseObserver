package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 存储使用率摘要渲染器 —— 绘制环形图（Donut Chart）和分类标签。
 * 环形图中心显示总使用百分比，下方显示分类图例。
 */
public final class StorageUsageSummaryRenderer {
    private static final int RING_OUTER_RADIUS = 26;
    private static final int RING_INNER_RADIUS = 16;
    private static final int SECTION_HEIGHT = 100;

    private StorageUsageSummaryRenderer() {
    }

    /** 获取区段固定高度 */
    public static int sectionHeight() {
        return SECTION_HEIGHT;
    }

    /**
     * 渲染使用率摘要区域（环形图 + 图例）。
     *
     * @param area            绘制区域
     * @param segments        分段数据
     * @param totalUsedRatio  总使用比例（0.0 ~ 1.0）
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.UsageSegment> segments,
            double totalUsedRatio
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.section.usage"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        if (segments.isEmpty()) {
            gfx.drawString(font, "N/A",
                    content.x(), content.y() + 13, UiThemeTokens.TEXT_MUTED);
            return;
        }

        // ===== Donut chart (center of content area) =====
        int ringCenterX = content.x() + content.width() / 2;
        int ringCenterY = content.y() + 14 + RING_OUTER_RADIUS + 2;
        drawDonutChart(gfx, ringCenterX, ringCenterY, RING_OUTER_RADIUS, RING_INNER_RADIUS, segments);

        // Center label: percentage + "Total Used"
        String pctText = String.format(Locale.ROOT, "%.1f%%", totalUsedRatio * 100.0);
        int pctWidth = font.width(pctText);
        gfx.drawString(font, pctText, ringCenterX - pctWidth / 2, ringCenterY - 5, UiThemeTokens.TITLE);
        String usedLabel = Component.translatable("screen.resourceobserver.storage.usage.total_used").getString();
        int usedWidth = font.width(usedLabel);
        gfx.drawString(font, usedLabel, ringCenterX - usedWidth / 2, ringCenterY + 5, UiThemeTokens.TEXT_MUTED);

        // ===== Legend below donut =====
        int legendY = ringCenterY + RING_OUTER_RADIUS + 8;
        for (int i = 0; i < segments.size(); i++) {
            StorageNetworkViewModel.UsageSegment segment = segments.get(i);
            int legendX = content.x() + 4;
            int currentLegendY = legendY + i * 10;
            if (currentLegendY + 10 > content.bottom()) {
                break;
            }
            // Color dot
            gfx.fill(legendX, currentLegendY + 1, legendX + 6, currentLegendY + 7, segment.color());
            // Label text
            String legendText = segment.displayName() + " " + String.format(Locale.ROOT, "%.0f%%", segment.percentage());
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, legendText, content.width() - 16),
                    legendX + 9, currentLegendY, UiThemeTokens.TEXT_MUTED);
        }
    }

    /**
     * 渲染使用率摘要（兼容旧接口，无 totalUsedRatio 时默认 0）。
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.UsageSegment> segments
    ) {
        render(gfx, font, area, segments, 0.0);
    }

    /**
     * 使用像素填充绘制环形图。
     */
    private static void drawDonutChart(
            GuiGraphics gfx,
            int centerX,
            int centerY,
            int outerR,
            int innerR,
            List<StorageNetworkViewModel.UsageSegment> segments
    ) {
        if (segments.isEmpty()) {
            return;
        }

        double[] startAngles = new double[segments.size()];
        double[] endAngles = new double[segments.size()];
        double accum = -Math.PI / 2.0;
        for (int i = 0; i < segments.size(); i++) {
            startAngles[i] = accum;
            accum += (segments.get(i).percentage() / 100.0) * 2.0 * Math.PI;
            endAngles[i] = accum;
        }

        int outerRSq = outerR * outerR;
        int innerRSq = innerR * innerR;

        for (int dy = -outerR; dy <= outerR; dy++) {
            for (int dx = -outerR; dx <= outerR; dx++) {
                int distSq = dx * dx + dy * dy;
                if (distSq > outerRSq || distSq < innerRSq) {
                    continue;
                }
                double angle = Math.atan2(dy, dx);
                int color = findSegmentColor(angle, startAngles, endAngles, segments);
                if (color != 0) {
                    gfx.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }

        // Center hole fill
        for (int dy = -innerR + 1; dy < innerR; dy++) {
            for (int dx = -innerR + 1; dx < innerR; dx++) {
                if (dx * dx + dy * dy < (innerR - 1) * (innerR - 1)) {
                    gfx.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, UiThemeTokens.SECTION_BG);
                }
            }
        }
    }

    private static int findSegmentColor(
            double angle,
            double[] startAngles,
            double[] endAngles,
            List<StorageNetworkViewModel.UsageSegment> segments
    ) {
        double normalizedAngle = angle;
        double startBase = startAngles[0];
        while (normalizedAngle < startBase) normalizedAngle += 2.0 * Math.PI;
        while (normalizedAngle >= startBase + 2.0 * Math.PI) normalizedAngle -= 2.0 * Math.PI;

        for (int i = 0; i < segments.size(); i++) {
            double normalizedStart = startAngles[i];
            while (normalizedStart < startBase) normalizedStart += 2.0 * Math.PI;
            while (normalizedStart >= startBase + 2.0 * Math.PI) normalizedStart -= 2.0 * Math.PI;
            double normalizedEnd = endAngles[i];
            while (normalizedEnd < startBase) normalizedEnd += 2.0 * Math.PI;
            while (normalizedEnd >= startBase + 2.0 * Math.PI) normalizedEnd -= 2.0 * Math.PI;

            if (normalizedEnd > normalizedStart) {
                if (normalizedAngle >= normalizedStart && normalizedAngle < normalizedEnd) {
                    return segments.get(i).color();
                }
            } else {
                if (normalizedAngle >= normalizedStart || normalizedAngle < normalizedEnd) {
                    return segments.get(i).color();
                }
            }
        }
        return segments.isEmpty() ? 0 : segments.get(segments.size() - 1).color();
    }
}
