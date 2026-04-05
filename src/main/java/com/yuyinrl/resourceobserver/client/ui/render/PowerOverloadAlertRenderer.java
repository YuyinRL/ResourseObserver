package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 过载预警卡片渲染器 —— 渲染电力过载风险摘要卡片。
 * <p>
 * 显示内容：
 *   标题 "Overload Risk Summary"
 *   主体文字描述当前过载状态和详细指标
 *   底部进度条显示接近过载的比率
 */
public final class PowerOverloadAlertRenderer {
    private static final int CARD_HEIGHT = 68;
    private static final int BAR_HEIGHT = 4;

    private PowerOverloadAlertRenderer() {
    }

    /** 获取卡片固定高度 */
    public static int cardHeight() {
        return CARD_HEIGHT;
    }

    /**
     * 渲染过载预警卡片。
     *
     * @param area         绘制区域
     * @param overloadInfo 过载预警信息
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            PowerNetworkViewModel.OverloadInfo overloadInfo
    ) {
        // Card background with left border highlight
        int borderColor = alertBorderColor(overloadInfo);
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.CARD_BG, borderColor);

        // Left accent bar (3px wide)
        gfx.fill(area.x(), area.y(), area.x() + 3, area.bottom(), borderColor);

        UiRect content = area.inset(8);
        int textX = content.x() + 2;

        // Title: "Overload Risk Summary"
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.overload.title"),
                textX, content.y(), UiThemeTokens.TEXT);

        // Subtitle: risk level
        int riskColor = riskColor(overloadInfo);
        String riskText = riskText(overloadInfo);
        gfx.drawString(font, riskText, textX, content.y() + 11, riskColor);

        // Detail line 1: "Overload risk within next 30m"
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.overload.window_risk"),
                textX, content.y() + 24, UiThemeTokens.TEXT_MUTED);

        // Detail line 2: "Capacity Headroom: XX.X% | Reserve: YY FE/t"
        String headroom = String.format(Locale.ROOT, "%.1f%%", overloadInfo.headroomPercent());
        String reserve = formatCompact(overloadInfo.reservePerTick()) + " FE/t";
        String detailLine = Component.translatable("screen.resourceobserver.power.overload.headroom", headroom).getString()
                + "  |  "
                + Component.translatable("screen.resourceobserver.power.overload.reserve", reserve).getString();
        gfx.drawString(font,
                RenderUtils.ellipsis(font, detailLine, content.width() - 4),
                textX, content.y() + 35, UiThemeTokens.TEXT);

        // Bottom progress bar: overload proximity ratio
        int barY = content.bottom() - BAR_HEIGHT - 2;
        int barW = content.width() - 4;
        double overloadRatio = Math.min(1.0, Math.max(0.0, 1.0 - overloadInfo.headroomPercent() / 100.0));
        int barFg = overloadRatio >= 0.9 ? UiThemeTokens.ROSE
                : (overloadRatio >= 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
        RenderUtils.drawProgressBar(gfx, textX, barY, barW, BAR_HEIGHT, overloadRatio, 0x44334455, barFg);
    }

    private static int alertBorderColor(PowerNetworkViewModel.OverloadInfo info) {
        if (info.headroomPercent() < 5.0) return UiThemeTokens.ROSE;
        if (info.headroomPercent() < 15.0) return UiThemeTokens.AMBER;
        return UiThemeTokens.EMERALD;
    }

    private static int riskColor(PowerNetworkViewModel.OverloadInfo info) {
        if (info.headroomPercent() < 5.0) return UiThemeTokens.ROSE;
        if (info.headroomPercent() < 15.0) return UiThemeTokens.AMBER;
        return UiThemeTokens.EMERALD;
    }

    private static String riskText(PowerNetworkViewModel.OverloadInfo info) {
        if (info.headroomPercent() < 5.0) {
            return Component.translatable("screen.resourceobserver.power.overload.risk.critical").getString();
        }
        if (info.headroomPercent() < 15.0) {
            return Component.translatable("screen.resourceobserver.power.overload.risk.warning").getString();
        }
        return Component.translatable("screen.resourceobserver.power.overload.risk.low").getString();
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
