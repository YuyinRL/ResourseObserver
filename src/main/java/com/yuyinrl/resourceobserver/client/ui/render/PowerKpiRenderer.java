package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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
        List<KpiRenderer.SimpleKpiCard> cards = kpis.stream()
                .map(k -> new KpiRenderer.SimpleKpiCard(k.label(), k.value(), k.status()))
                .toList();
        KpiRenderer.renderSimpleCards(gfx, font, area, cards);
    }
}

