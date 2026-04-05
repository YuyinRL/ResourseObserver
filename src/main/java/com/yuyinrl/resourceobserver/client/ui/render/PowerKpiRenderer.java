package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 电力 KPI 渲染器 —— 绘制电力网络页面的三个 KPI 卡片。
 * 分别显示：总输入 FE/t、总输出 FE/t、储能状态。
 */
public final class PowerKpiRenderer {
    private PowerKpiRenderer() {
    }

    /**
     * 渲染电力 KPI 卡片行。
     *
     * @param area 绘制区域
     * @param kpis KPI 数据列表
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<PowerNetworkViewModel.PowerKpi> kpis
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        int gap = 8;
        int cardCount = Math.max(1, kpis.size());
        int totalGap = (cardCount - 1) * gap;
        int cardW = Math.max(40, (content.width() - totalGap) / cardCount);

        for (int i = 0; i < kpis.size(); i++) {
            PowerNetworkViewModel.PowerKpi kpi = kpis.get(i);
            int cardX = content.x() + i * (cardW + gap);
            UiRect card = new UiRect(cardX, content.y(), cardW, content.height());

            RenderUtils.fillPanel(gfx, card, UiThemeTokens.CARD_BG, UiThemeTokens.CARD_BORDER);

            int valueColor = statusColor(kpi.status());
            String label = Component.translatable(kpi.label()).getString();
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, label, card.width() - 12),
                    card.x() + 6, card.y() + 5, UiThemeTokens.TEXT_MUTED);
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, kpi.value(), card.width() - 12),
                    card.x() + 6, card.y() + 17, valueColor);
        }
    }

    private static int statusColor(OverviewViewModel.Status status) {
        return switch (status) {
            case POSITIVE -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case NEGATIVE -> UiThemeTokens.ROSE;
            case NEUTRAL -> UiThemeTokens.TEXT;
        };
    }
}

