package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 电力负载分布图渲染器 —— 绘制水平分段条形图和环形可视化。
 * 显示各设备类别（Mining / Assembly / Logistics / Other）的能耗占比分布。
 * <p>
 * 使用双层可视化：
 * 1. 水平分段条形图（精确显示比例）
 * 2. 简化的环形图近似（使用同心弧段）
 */
public final class PowerLoadChartRenderer {
    private static final int BAR_HEIGHT = 10;
    private static final int SECTION_HEIGHT = 80;
    private static final int RING_OUTER_RADIUS = 28;
    private static final int RING_INNER_RADIUS = 18;

    private PowerLoadChartRenderer() {
    }

    /** 获取区段固定高度 */
    public static int sectionHeight() {
        return SECTION_HEIGHT;
    }

    /**
     * 渲染负载分布图区域。
     *
     * @param area     绘制区域
     * @param segments 负载分段数据
     * @param totalDemandFEt 总需求 FE/t（用于环形图中心标签）
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<PowerNetworkViewModel.LoadSegment> segments,
            long totalDemandFEt
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.section.load_chart"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        // Subtitle
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.load_chart.subtitle"),
                content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        if (segments.isEmpty()) {
            gfx.drawString(font,
                    Component.translatable("screen.resourceobserver.power.no_data").getString(),
                    content.x(), content.y() + 24, UiThemeTokens.TEXT_MUTED);
            return;
        }

        // ===== Donut chart (centered) =====
        int ringCenterX = content.x() + content.width() / 2;
        int ringCenterY = content.y() + 24 + RING_OUTER_RADIUS + 2;
        drawDonutChart(gfx, ringCenterX, ringCenterY, RING_OUTER_RADIUS, RING_INNER_RADIUS, segments);

        // Center label: demand value + "Demand"
        String demandText = formatCompactGW(totalDemandFEt);
        int demandWidth = font.width(demandText);
        gfx.drawString(font, demandText, ringCenterX - demandWidth / 2, ringCenterY - 5, UiThemeTokens.TITLE);
        String demandLabel = Component.translatable("screen.resourceobserver.power.current_demand").getString();
        int labelWidth = font.width(demandLabel);
        gfx.drawString(font, demandLabel, ringCenterX - labelWidth / 2, ringCenterY + 5, UiThemeTokens.TEXT_MUTED);

        // ===== Legend below donut =====
        int legendY = ringCenterY + RING_OUTER_RADIUS + 6;
        int legendX = content.x() + 4;
        for (int i = 0; i < segments.size(); i++) {
            PowerNetworkViewModel.LoadSegment segment = segments.get(i);
            int currentLegendY = legendY + i * 10;
            if (currentLegendY + 10 > content.bottom()) {
                break;
            }
            // Color dot
            gfx.fill(legendX, currentLegendY + 1, legendX + 6, currentLegendY + 7, segment.color());
            // Label
            String legendText = segment.displayName() + " " + String.format(Locale.ROOT, "%.0f%%", segment.percentage()) + " Use";
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, legendText, content.width() - 16),
                    legendX + 9, currentLegendY, UiThemeTokens.TEXT_MUTED);
        }
    }

    /**
     * 渲染负载分布图（兼容旧接口）。
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<PowerNetworkViewModel.LoadSegment> segments
    ) {
        render(gfx, font, area, segments, 0L);
    }

    private static String formatCompactGW(long fePerTick) {
        // Convert FE/t to a readable power unit
        if (fePerTick >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fGW", fePerTick / 1_000_000_000.0);
        }
        if (fePerTick >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fMW", fePerTick / 1_000_000.0);
        }
        if (fePerTick >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fKW", fePerTick / 1_000.0);
        }
        return fePerTick + " FE/t";
    }

    /**
     * 使用像素填充绘制简化环形图。
     * 逐像素扫描环形区域，根据角度确定所属分段并填充颜色。
     */
    private static void drawDonutChart(
            GuiGraphics gfx,
            int centerX,
            int centerY,
            int outerR,
            int innerR,
            List<PowerNetworkViewModel.LoadSegment> segments
    ) {
        if (segments.isEmpty()) {
            return;
        }

        // 计算每个分段的起止角度（弧度）
        double[] startAngles = new double[segments.size()];
        double[] endAngles = new double[segments.size()];
        double accum = -Math.PI / 2.0; // 从正上方（12点方向）开始
        for (int i = 0; i < segments.size(); i++) {
            startAngles[i] = accum;
            accum += (segments.get(i).percentage() / 100.0) * 2.0 * Math.PI;
            endAngles[i] = accum;
        }

        int outerRSq = outerR * outerR;
        int innerRSq = innerR * innerR;

        // 逐像素扫描
        for (int dy = -outerR; dy <= outerR; dy++) {
            for (int dx = -outerR; dx <= outerR; dx++) {
                int distSq = dx * dx + dy * dy;
                if (distSq > outerRSq || distSq < innerRSq) {
                    continue;
                }
                double angle = Math.atan2(dy, dx);
                // 查找该角度属于哪个分段
                int color = findSegmentColor(angle, startAngles, endAngles, segments);
                if (color != 0) {
                    gfx.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }

        // 中心空心圆填充背景
        for (int dy = -innerR + 1; dy < innerR; dy++) {
            for (int dx = -innerR + 1; dx < innerR; dx++) {
                if (dx * dx + dy * dy < (innerR - 1) * (innerR - 1)) {
                    gfx.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, UiThemeTokens.SECTION_BG);
                }
            }
        }
    }

    /** 根据角度查找所属分段颜色 */
    private static int findSegmentColor(
            double angle,
            double[] startAngles,
            double[] endAngles,
            List<PowerNetworkViewModel.LoadSegment> segments
    ) {
        // 归一化角度到 startAngles[0] 开始的范围
        double normalizedAngle = angle;
        double startBase = startAngles[0];
        // 确保角度在 [startBase, startBase + 2PI) 范围内
        while (normalizedAngle < startBase) {
            normalizedAngle += 2.0 * Math.PI;
        }
        while (normalizedAngle >= startBase + 2.0 * Math.PI) {
            normalizedAngle -= 2.0 * Math.PI;
        }

        for (int i = 0; i < segments.size(); i++) {
            double start = startAngles[i];
            double end = endAngles[i];
            // 归一化分段角度
            double normalizedStart = start;
            while (normalizedStart < startBase) normalizedStart += 2.0 * Math.PI;
            while (normalizedStart >= startBase + 2.0 * Math.PI) normalizedStart -= 2.0 * Math.PI;
            double normalizedEnd = end;
            while (normalizedEnd < startBase) normalizedEnd += 2.0 * Math.PI;
            while (normalizedEnd >= startBase + 2.0 * Math.PI) normalizedEnd -= 2.0 * Math.PI;

            if (normalizedEnd > normalizedStart) {
                if (normalizedAngle >= normalizedStart && normalizedAngle < normalizedEnd) {
                    return segments.get(i).color();
                }
            } else {
                // 跨越 2PI 边界
                if (normalizedAngle >= normalizedStart || normalizedAngle < normalizedEnd) {
                    return segments.get(i).color();
                }
            }
        }
        // 默认返回最后一个分段颜色
        return segments.isEmpty() ? 0 : segments.get(segments.size() - 1).color();
    }
}

