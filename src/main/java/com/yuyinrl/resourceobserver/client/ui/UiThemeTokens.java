package com.yuyinrl.resourceobserver.client.ui;

/**
 * UI 主题色彩令牌 —— 定义终端界面的深色主题配色方案。
 * 所有颜色值为 ARGB 格式（0xAARRGGBB），供各渲染器统一使用。
 */
public final class UiThemeTokens {
    private UiThemeTokens() {
    }

    // ========== 面板与区段背景 ==========
    public static final int PANEL_BG = 0xE00A1020;         // 主面板背景（深蓝半透明）
    public static final int PANEL_BORDER = 0xFF2A3C62;     // 主面板边框
    public static final int SECTION_BG = 0xCC101A30;       // 内容区段背景
    public static final int SECTION_BORDER = 0xFF1E2A44;   // 内容区段边框
    public static final int HEADER_BG = 0xFF111A2E;        // 页眉背景

    // ========== 文本颜色 ==========
    public static final int TITLE = 0xFFFFFFFF;            // 标题文本（白色）
    public static final int TEXT = 0xFFCFD8E8;             // 正文文本（浅灰蓝）
    public static final int TEXT_MUTED = 0xFF7B8AA8;       // 次要文本（暗灰蓝）
    public static final int DIVIDER = 0xFF22314F;          // 分割线

    // ========== 语义色彩（用于 KPI、状态指示） ==========
    public static final int CYAN = 0xFF22D3EE;             // 青色 —— 生产/正面
    public static final int AMBER = 0xFFF59E0B;            // 琥珀色 —— 警告
    public static final int EMERALD = 0xFF34D399;          // 翠绿色 —— 盈余/库存
    public static final int ROSE = 0xFFFB7185;             // 玫瑰色 —— 消耗/亏损
    public static final int BLUE = 0xFF60A5FA;             // 蓝色 —— 信息/链接

    // ========== 卡片与标签页 ==========
    public static final int CARD_BG = 0xCC0F172A;          // 卡片背景
    public static final int CARD_BORDER = 0xFF1F2A42;      // 卡片边框
    public static final int TAB_ACTIVE = 0xFF1F4E82;       // 激活标签页背景
    public static final int TAB_INACTIVE = 0xFF16253C;     // 未激活标签页背景
}
