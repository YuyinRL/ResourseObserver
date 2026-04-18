package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 存储 KPI 渲染器 —— 绘制存储网络页面的三个 KPI 卡片。
 * 分别显示：总物品数量、物品种类数、填充率。
 */
public final class StorageKpiRenderer {
    private StorageKpiRenderer() {
    }

    /**
     * 渲染存储 KPI 卡片行。
     *
     * @param area 绘制区域
     * @param kpis KPI 数据列表
     */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.StorageKpi> kpis
    ) {
        List<KpiRenderer.SimpleKpiCard> cards = kpis.stream()
                .map(k -> new KpiRenderer.SimpleKpiCard(k.label(), k.value(), k.status()))
                .toList();
        KpiRenderer.renderSimpleCards(gfx, font, area, cards);
    }
}

