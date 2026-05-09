package com.yuyinrl.resourceobserver.client.screen.theme;

/**
 * 新 Vanilla UI 框架的主题 token：颜色、间距、字号常量。
 *
 * <p>所有数值集中此处，便于后续做"高对比度模式"或夜间模式切换。
 * 颜色采用 ARGB（高字节为 alpha），与 {@code GuiGraphics#fill} 一致。</p>
 */
public final class VanillaTheme {

    private VanillaTheme() {}

    // ---- 颜色：背景 ---------------------------------------------------------
    /** Screen 主背景半透黑（与原版 GUI 兼容）。 */
    public static final int COLOR_BG_DIM = 0x80_10_10_10;
    /** 面板背景（不透明深灰）。 */
    public static final int COLOR_PANEL = 0xFF_2B_2B_2B;
    /** 面板高亮（如选中/hover）。 */
    public static final int COLOR_PANEL_ACCENT = 0xFF_3D_3D_3D;
    /** 分割线。 */
    public static final int COLOR_DIVIDER = 0xFF_55_55_55;

    // ---- 颜色：文字 ---------------------------------------------------------
    public static final int COLOR_TEXT_PRIMARY = 0xFF_FF_FF_FF;
    public static final int COLOR_TEXT_SECONDARY = 0xFF_AA_AA_AA;
    public static final int COLOR_TEXT_DISABLED = 0xFF_70_70_70;

    // ---- 颜色：状态 ---------------------------------------------------------
    public static final int COLOR_STATUS_OK = 0xFF_55_DD_55;
    public static final int COLOR_STATUS_WARN = 0xFF_E0_C0_40;
    public static final int COLOR_STATUS_ERROR = 0xFF_E0_55_55;
    public static final int COLOR_STATUS_INFO = 0xFF_55_AA_E0;

    // ---- 颜色：按钮 ---------------------------------------------------------
    public static final int COLOR_BUTTON_BG = 0xFF_55_55_55;
    public static final int COLOR_BUTTON_BG_HOVER = 0xFF_77_77_77;
    public static final int COLOR_BUTTON_BG_DISABLED = 0xFF_2B_2B_2B;
    public static final int COLOR_BUTTON_BORDER = 0xFF_00_00_00;
    public static final int COLOR_BUTTON_HI = 0xFF_AA_AA_AA;
    public static final int COLOR_BUTTON_LO = 0xFF_22_22_22;

    // ---- 间距 ---------------------------------------------------------------
    public static final int SPACING_XS = 2;
    public static final int SPACING_S = 4;
    public static final int SPACING_M = 8;
    public static final int SPACING_L = 12;
    public static final int SPACING_XL = 16;

    // ---- 字号 ---------------------------------------------------------------
    public static final int FONT_HEIGHT = 9;

    // ---- 控件尺寸 -----------------------------------------------------------
    public static final int BUTTON_HEIGHT = 20;
    public static final int TAB_HEIGHT = 24;
    public static final int ICON_SIZE = 16;
    public static final int SCROLLBAR_WIDTH = 6;

    // ---- Dialog 尺寸（与 ModernUiTheme 对齐的等价值） ------------------------
    /** 小型 Dialog（如分组命名）。 */
    public static final int DIALOG_SMALL_W = 200;
    /** 进度类 Dialog（计算中）。 */
    public static final int DIALOG_PROGRESS_W = 240;
    /** KPI 详情（小）。 */
    public static final int DIALOG_KPI_SMALL_W = 360;
    public static final int DIALOG_KPI_SMALL_H = 280;
    /** KPI 详情（标准）。 */
    public static final int DIALOG_KPI_W = 420;
    public static final int DIALOG_KPI_H = 360;
    /** 计划审核类宽度。 */
    public static final int DIALOG_REVIEW_W = 560;
    /** 大型 Dialog（合成树）。 */
    public static final int DIALOG_XL_W = 720;
    public static final int DIALOG_XL_H = 480;

    // ---- Dialog 颜色 token --------------------------------------------------
    public static final int COLOR_DIALOG_BG = 0xFF_2B_2B_2B;
    public static final int COLOR_DIALOG_BORDER = 0xFF_00_00_00;
    public static final int COLOR_DIALOG_TITLEBAR = 0xFF_3D_3D_3D;
    public static final int COLOR_DIALOG_CLOSE_ICON = 0xFF_AA_AA_AA;
    public static final int COLOR_DIALOG_CLOSE_ICON_HOVER = 0xFF_E0_55_55;
}
