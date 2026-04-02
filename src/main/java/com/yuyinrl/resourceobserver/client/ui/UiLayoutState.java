package com.yuyinrl.resourceobserver.client.ui;

import net.minecraft.util.Mth;

/**
 * UI 布局计算状态 —— 根据屏幕尺寸和布局规格计算终端界面的实际布局。
 * <p>
 * 计算流程：
 * 1. 根据屏幕宽度选择布局配置文件（S/M/L）
 * 2. 计算自动缩放比例（限制在 0.70 ~ 1.18 范围内）
 * 3. 叠加手动微调缩放（0.95 ~ 1.05 范围）
 * 4. 计算面板尺寸、标题栏、滚动视口、按钮位置等
 *
 * @param panel          主面板矩形区域
 * @param fixedChrome    固定标题栏区域（不随滚动移动）
 * @param scrollViewport 可滚动内容视口区域
 * @param closeButton    关闭按钮位置
 * @param sizeButton     尺寸切换按钮位置
 * @param sectionGap     区段间距
 * @param headerHeight   页眉高度
 * @param kpiHeight      KPI 区域高度
 * @param chartHeight    图表区域高度
 * @param watchlistHeight 关注列表区域高度
 * @param scrollbarWidth  滚动条宽度
 */
public record UiLayoutState(
        UiRect panel,
        UiRect fixedChrome,
        UiRect scrollViewport,
        UiRect closeButton,
        UiRect sizeButton,
        int sectionGap,
        int headerHeight,
        int kpiHeight,
        int chartHeight,
        int watchlistHeight,
        int scrollbarWidth
) {
    /**
     * 根据屏幕尺寸、布局规格和手动缩放因子计算完整的布局状态。
     * @param screenWidth  屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @param spec         布局规格定义
     * @param manualScale  手动微调缩放因子（1.0 = 无缩放）
     */
    public static UiLayoutState compute(
            int screenWidth,
            int screenHeight,
            UiLayoutSpec spec,
            float manualScale
    ) {
        // 根据屏幕宽度选择 S/M/L 布局配置
        UiLayoutSpec.Profile profile = spec.resolveProfile(screenWidth);

        // 计算自动缩放比例：面板不超出屏幕，保留 32px 边距
        float autoScale = Math.min(
                (screenWidth - 32.0f) / spec.basePanelWidth(),
                (screenHeight - 32.0f) / spec.basePanelHeight()
        );
        float panelScale = Mth.clamp(autoScale, 0.70f, 1.18f);
        // 手动微调缩放，限制在 ±5% 范围内
        float microScale = Mth.clamp(manualScale, 0.95f, 1.05f);

        // 计算面板实际尺寸（不小于最小值，不超出屏幕）
        int panelWidth = Math.max(spec.minPanelWidth(), Math.round(spec.basePanelWidth() * panelScale));
        int panelHeight = Math.max(spec.minPanelHeight(), Math.round(spec.basePanelHeight() * panelScale));
        panelWidth = Math.min(panelWidth, screenWidth - 16);
        panelHeight = Math.min(panelHeight, screenHeight - 16);

        // 计算面板位置（居中），并内缩获取内容区域
        UiRect panel = new UiRect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight);
        UiRect content = panel.inset(profile.margin());

        // 计算固定标题栏区域
        int chromeHeight = Math.max(20, Math.round(profile.chromeHeight() * microScale));
        UiRect fixedChrome = new UiRect(content.x(), content.y(), content.width(), Math.min(chromeHeight, content.height()));

        // 计算可滚动内容视口区域（标题栏下方到面板底部）
        int viewportY = fixedChrome.bottom() + profile.sectionGap();
        UiRect scrollViewport = new UiRect(
                content.x(),
                viewportY,
                content.width(),
                Math.max(40, content.bottom() - viewportY)
        );

        // 计算关闭按钮和尺寸按钮位置（面板右上角）
        int closeSize = 16;
        UiRect closeButton = new UiRect(panel.right() - closeSize - 8, panel.y() + 8, closeSize, closeSize);
        UiRect sizeButton = new UiRect(closeButton.x() - 20, closeButton.y(), 16, 16);

        return new UiLayoutState(
                panel,
                fixedChrome,
                scrollViewport,
                closeButton,
                sizeButton,
                profile.sectionGap(),
                Math.max(36, Math.round(profile.headerHeight() * microScale)),
                Math.max(64, Math.round(profile.kpiHeight() * microScale)),
                Math.max(120, Math.round(profile.chartHeight() * microScale)),
                Math.max(82, Math.round(profile.watchlistHeight() * microScale)),
                Math.max(6, profile.scrollbarWidth())
        );
    }
}
