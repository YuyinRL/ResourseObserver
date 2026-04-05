package com.yuyinrl.resourceobserver.client.ui;

/**
 * UI 布局规格定义 —— 定义终端界面的响应式布局参数。
 * <p>
 * 包含三套尺寸配置（S/M/L），根据视口宽度自动选择。
 *
 * @param basePanelWidth  基准面板宽度
 * @param basePanelHeight 基准面板高度
 * @param minPanelWidth   最小面板宽度
 * @param minPanelHeight  最小面板高度
 * @param smallMaxWidth   小尺寸模式的最大宽度阈值
 * @param mediumMaxWidth  中尺寸模式的最大宽度阈值
 * @param smallProfile    小尺寸布局参数
 * @param mediumProfile   中尺寸布局参数
 * @param largeProfile    大尺寸布局参数
 */
public record UiLayoutSpec(
        int basePanelWidth,
        int basePanelHeight,
        int minPanelWidth,
        int minPanelHeight,
        int smallMaxWidth,
        int mediumMaxWidth,
        Profile smallProfile,
        Profile mediumProfile,
        Profile largeProfile
) {
    /** 根据视口宽度选择适合的布局配置文件 */
    public Profile resolveProfile(int viewportWidth) {
        if (viewportWidth <= smallMaxWidth) {
            return smallProfile;
        }
        if (viewportWidth <= mediumMaxWidth) {
            return mediumProfile;
        }
        return largeProfile;
    }

    /** 返回总览界面的默认布局规格 */
    public static UiLayoutSpec defaultOverview() {
        return new UiLayoutSpec(
                980,
                640,
                700,
                460,
                1120,
                1520,
                new Profile(8, 24, 8, 54, 92, 148, 98, 8),
                new Profile(10, 24, 8, 58, 100, 168, 108, 8),
                new Profile(12, 24, 8, 62, 108, 182, 116, 8)
        );
    }

    /** 返回存储网络界面的默认布局规格 */
    public static UiLayoutSpec defaultStorageNetwork() {
        // 存储网络页面使用相同面板尺寸，但 KPI 较矮、无图表、有节点列表（复用 chartHeight）
        return new UiLayoutSpec(
                980,
                640,
                700,
                460,
                1120,
                1520,
                new Profile(8, 24, 8, 54, 44, 72, 42, 8),
                new Profile(10, 24, 8, 58, 48, 78, 46, 8),
                new Profile(12, 24, 8, 62, 52, 84, 50, 8)
        );
    }

    /** 返回电力网络界面的默认布局规格 */
    public static UiLayoutSpec defaultPowerNetwork() {
        // 电力网络页面：KPI 行 + 设备列表 + 负载图 + 警报列表
        // chartHeight 复用为负载图高度，watchlistHeight 复用为警报列表高度
        return new UiLayoutSpec(
                980,
                640,
                700,
                460,
                1120,
                1520,
                new Profile(8, 24, 8, 54, 44, 86, 42, 8),
                new Profile(10, 24, 8, 58, 48, 92, 46, 8),
                new Profile(12, 24, 8, 62, 52, 98, 50, 8)
        );
    }

    /**
     * 布局配置文件 —— 单种尺寸模式下的具体尺寸参数。
     * @param margin         面板内边距
     * @param chromeHeight   顶部标题栏高度
     * @param sectionGap     各区段之间的间距
     * @param headerHeight   页眉区域高度
     * @param kpiHeight      KPI 卡片区域高度
     * @param chartHeight    图表区域高度
     * @param watchlistHeight 关注列表区域高度
     * @param scrollbarWidth  滚动条宽度
     */
    public record Profile(
            int margin,
            int chromeHeight,
            int sectionGap,
            int headerHeight,
            int kpiHeight,
            int chartHeight,
            int watchlistHeight,
            int scrollbarWidth
    ) {
    }
}
