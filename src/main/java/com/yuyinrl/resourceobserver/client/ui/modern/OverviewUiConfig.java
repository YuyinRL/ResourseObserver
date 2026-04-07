package com.yuyinrl.resourceobserver.client.ui.modern;

import net.minecraft.util.Mth;

/**
 * Overview 页面迁移配置（ModernUI 版本）。
 * <p>
 * 目标：
 * - 与原版 GUI 的像素坐标体系解耦；
 * - 显式定义边框厚度、圆角、点击热区扩展（hit slop）；
 * - 为后续 ModernUI 布局迁移提供统一尺度来源。
 */
public record OverviewUiConfig(
        int contentWidth,
        int contentHeight,
        int panelPadding,
        int sectionGap,
        int borderStroke,
        int cornerRadius,
        int hitSlop
) {

    public static final OverviewUiConfig DEFAULT = new OverviewUiConfig(
            1168,
            768,
            18,
            12,
            2,
            8,
            4
    );

    /**
     * 按 GUI 缩放因子派生更稳健的点击扩展值，
     * 避免不同 UI 框架（Vanilla/ModernUI）在边框尺寸取整上产生点击误差。
     */
    public int effectiveHitSlop(double guiScale) {
        double scale = Math.max(1.0d, guiScale);
        int scaled = (int) Math.round(hitSlop / scale);
        return Mth.clamp(scaled, 2, 8);
    }

    /**
     * 给边框预留的安全内边距，防止 1px/2px 边框在不同渲染后端下看起来“挤压内容”。
     */
    public int safeInnerPadding() {
        return panelPadding + Math.max(borderStroke, 1);
    }
}
