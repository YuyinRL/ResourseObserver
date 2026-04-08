package com.yuyinrl.resourceobserver.client.ui;

import net.minecraft.util.Mth;

/**
 * UI 布局计算状态 —— 根据屏幕尺寸和布局规格计算终端界面的实际布局。
 * <p>
 * 计算流程：
 * 1. 根据屏幕宽度选择布局配置文件（S/M/L）
 * 2. 面板占屏幕 90%（两侧各预留 5% 边距）
 * 3. 叠加手动微调缩放（S=0.95 / M=1.00 / L=1.05）
 * 4. 内容区段高度按面板与基准尺寸的比值动态缩放（0.70~1.50）
 * 5. 计算面板尺寸、标题栏、滚动视口、按钮位置等
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
     * <p>
     * 面板占屏幕 90%（两侧各预留 5% 边距），内容区段高度按面板尺寸
     * 相对于基准尺寸的比例进行动态缩放。
     *
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

        // 手动微调缩放，限制在 ±5% 范围内
        float microScale = Mth.clamp(manualScale, 0.95f, 1.05f);

        // 面板占屏幕 90%（两侧各留 5%），再叠加手动缩放
        int panelWidth = Math.round(screenWidth * 0.90f * microScale);
        int panelHeight = Math.round(screenHeight * 0.90f * microScale);

        // 不小于最小值，不超出屏幕减 4px 安全边距
        panelWidth = Mth.clamp(panelWidth, spec.minPanelWidth(), screenWidth - 4);
        panelHeight = Mth.clamp(panelHeight, spec.minPanelHeight(), screenHeight - 4);

        // 内容区段高度缩放因子：基于面板与基准尺寸的比值
        float contentScale = Mth.clamp(
                Math.min(
                        panelWidth / (float) spec.basePanelWidth(),
                        panelHeight / (float) spec.basePanelHeight()
                ),
                0.70f, 1.50f
        );

        // 计算面板位置（居中），并内缩获取内容区域
        UiRect panel = new UiRect(
                (screenWidth - panelWidth) / 2,
                (screenHeight - panelHeight) / 2,
                panelWidth,
                panelHeight
        );
        UiRect content = panel.inset(profile.margin());

        // 计算固定标题栏区域
        int chromeHeight = Math.max(20, Math.round(profile.chromeHeight() * contentScale));
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
        int btnSize = Math.max(16, Math.round(16 * contentScale));
        UiRect closeButton = new UiRect(panel.right() - btnSize - 8, panel.y() + 8, btnSize, btnSize);
        UiRect sizeButton = new UiRect(closeButton.x() - btnSize - 4, closeButton.y(), btnSize, btnSize);

        return new UiLayoutState(
                panel,
                fixedChrome,
                scrollViewport,
                closeButton,
                sizeButton,
                Math.max(4, Math.round(profile.sectionGap() * contentScale)),
                Math.max(36, Math.round(profile.headerHeight() * contentScale)),
                Math.max(64, Math.round(profile.kpiHeight() * contentScale)),
                Math.max(120, Math.round(profile.chartHeight() * contentScale)),
                Math.max(82, Math.round(profile.watchlistHeight() * contentScale)),
                Math.max(6, Math.round(profile.scrollbarWidth() * contentScale))
        );
    }
}
