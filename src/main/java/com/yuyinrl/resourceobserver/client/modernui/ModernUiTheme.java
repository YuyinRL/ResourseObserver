package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * ModernUI 主题工具类 —— 提供共用的 UI 构建辅助方法。
 * 所有颜色常量来自 {@link UiThemeTokens}。
 */
public final class ModernUiTheme {

    private ModernUiTheme() {}

    // ===================== 全局 UI 尺寸缩放 =====================

    /** 文字缩放倍率（应用于所有 createText / addText / tinyButton 的 sp 值） */
    private static float textScale = 1.2f;
    /** 布局缩放倍率（应用于图标尺寸、行高等） */
    private static float layoutScale = 1.2f;

    // ===================== 对话框尺寸规范（基准 dp，会经 layoutScale 缩放） =====================

    /** 小型弹窗（输入框 / 确认提示）：200×WRAP */
    public static final int DIALOG_SMALL_W = 200;
    /** 进度提示弹窗：240×WRAP */
    public static final int DIALOG_PROGRESS_W = 240;
    /** KPI 详情弹窗：420×360 */
    public static final int DIALOG_KPI_W = 420;
    public static final int DIALOG_KPI_H = 360;
    /** 紧凑型 KPI 详情弹窗（单列指标）：360×280 */
    public static final int DIALOG_KPI_SMALL_W = 360;
    public static final int DIALOG_KPI_SMALL_H = 280;
    /** 合成审核弹窗（流程信息密集）：560×WRAP */
    public static final int DIALOG_REVIEW_W = 560;
    /** 超大弹窗（合成树详情）：720×560 */
    public static final int DIALOG_XL_W = 720;
    public static final int DIALOG_XL_H = 560;

    /** 设置全局 UI 缩放（在 size 按钮点击时调用，rebuildUi 前更新） */
    public static void setUiScale(float text, float layout) {
        textScale = text;
        layoutScale = layout;
    }

    public static float getTextScale() { return textScale; }
    public static float getLayoutScale() { return layoutScale; }

    /** 将 baseDp 按 layoutScale 缩放后转为像素（用于图标、行高等） */
    public static int scaledDp(View v, int baseDp) {
        return v.dp(Math.round(baseDp * layoutScale));
    }

    /** ResourceTerminalFragment 重载 — 使用 fragment 自身的 dp() */
    public static int scaledDp(ResourceTerminalFragment terminal, int baseDp) {
        return terminal.dp(Math.round(baseDp * layoutScale));
    }

    // ===================== ShapeDrawable 工厂 =====================

    public static ShapeDrawable makeBackground(View anchor, int fillColor, int borderColor,
                                               int radiusDp, int borderWidthDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(fillColor);
        if (radiusDp > 0) {
            drawable.setCornerRadius(anchor.dp(radiusDp));
        }
        if (borderWidthDp > 0) {
            drawable.setStroke(anchor.dp(borderWidthDp), borderColor);
        }
        return drawable;
    }

    public static ShapeDrawable cardBackground(View v) {
        return makeBackground(v, UiThemeTokens.CARD_BG, UiThemeTokens.CARD_BORDER, 8, 1);
    }

    public static ShapeDrawable cardBackgroundSelected(View v) {
        return makeBackground(v, 0xEE24466C, UiThemeTokens.CYAN, 8, 1);
    }

    public static ShapeDrawable sectionBackground(View v) {
        return makeBackground(v, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER, 6, 1);
    }

    public static ShapeDrawable panelBackground(View v) {
        return makeBackground(v, UiThemeTokens.PANEL_BG, UiThemeTokens.PANEL_BORDER, 10, 1);
    }

    public static ShapeDrawable tabBackground(View v, boolean active) {
        return makeBackground(v, active ? UiThemeTokens.TAB_ACTIVE : UiThemeTokens.TAB_INACTIVE,
                UiThemeTokens.DIVIDER, 1000, 1);
    }

    public static ShapeDrawable buttonBackground(View v) {
        return makeBackground(v, UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 1000, 1);
    }

    public static ShapeDrawable colorDot(View v, int color) {
        return makeBackground(v, color, color, 1000, 0);
    }

    // ===================== StateListDrawable 工厂（交互反馈） =====================

    /**
     * 亮度混合：将颜色的 RGB 通道向白色偏移 amount（0.0~1.0），保留 alpha。
     */
    private static int lighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + 255 * amount));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + 255 * amount));
        int b = Math.min(255, (int) ((color & 0xFF) + 255 * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 创建带 pressed / hovered / default 三态的 StateListDrawable（增强反馈）。
     */
    public static StateListDrawable statefulBackground(View anchor, int fillColor, int borderColor,
                                                       int radiusDp, int borderWidthDp) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{R.attr.state_pressed},
                makeBackground(anchor, lighten(fillColor, 0.22f), lighten(borderColor, 0.35f), radiusDp, borderWidthDp));
        sld.addState(new int[]{R.attr.state_hovered},
                makeBackground(anchor, lighten(fillColor, 0.12f), lighten(borderColor, 0.22f), radiusDp, borderWidthDp));
        sld.addState(new int[]{},
                makeBackground(anchor, fillColor, borderColor, radiusDp, borderWidthDp));
        return sld;
    }

    /** 按钮三态背景（胶囊圆角） */
    public static StateListDrawable buttonBackgroundStateful(View v) {
        return statefulBackground(v, UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 1000, 1);
    }

    /** 标签页三态背景（胶囊圆角，区分激活/未激活） */
    public static StateListDrawable tabBackgroundStateful(View v, boolean active) {
        return statefulBackground(v,
                active ? UiThemeTokens.TAB_ACTIVE : UiThemeTokens.TAB_INACTIVE,
                UiThemeTokens.DIVIDER, 1000, 1);
    }

    /** 顶栏图标按钮的圆形背景（波纹 + 悬停/按下高亮） */
    private static RippleDrawable chromeCircleBg(View v, boolean isClose) {
        int hoverFill = isClose ? 0x40E8384F : 0x20FFFFFF;
        int pressFill = isClose ? 0x70E8384F : 0x40FFFFFF;
        // 尺寸按钮加描边轮廓
        int border = isClose ? 0 : UiThemeTokens.DIVIDER;
        int bw = isClose ? 0 : 1;
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{R.attr.state_pressed},
                makeBackground(v, pressFill, border, 1000, bw));
        sld.addState(new int[]{R.attr.state_hovered},
                makeBackground(v, hoverFill, border, 1000, bw));
        sld.addState(new int[]{},
                makeBackground(v, 0x00000000, border, 1000, bw));
        int rippleColor = isClose ? 0x40FB7185 : 0x30FFFFFF;
        return wrapRipple(sld, rippleColor, v, 1000);
    }

    /** 卡片三态背景（圆角） */
    public static StateListDrawable cardBackgroundStateful(View v) {
        return statefulBackground(v, UiThemeTokens.CARD_BG, UiThemeTokens.CARD_BORDER, 8, 1);
    }

    /** 卡片选中态三态背景（圆角） */
    public static StateListDrawable cardBackgroundSelectedStateful(View v) {
        return statefulBackground(v, 0xEE24466C, UiThemeTokens.CYAN, 8, 1);
    }

    /** 表格行三态背景（圆角，普通/critical） */
    public static StateListDrawable rowBackgroundStateful(View v, boolean critical) {
        int baseFill = critical ? 0x40501020 : 0x00000000;
        int hoverFill = critical ? 0x60601830 : 0x30182840;
        int pressFill = critical ? 0x80702038 : 0x50203050;
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{R.attr.state_pressed},
                makeBackground(v, pressFill, 0, 4, 0));
        sld.addState(new int[]{R.attr.state_hovered},
                makeBackground(v, hoverFill, 0, 4, 0));
        sld.addState(new int[]{},
                makeBackground(v, baseFill, 0, 4, 0));
        return sld;
    }

    /** 分组表头三态背景（圆角） */
    public static StateListDrawable groupHeaderBackgroundStateful(View v) {
        return statefulBackground(v, 0xCC0C1420, UiThemeTokens.DIVIDER, 4, 1);
    }

    /** 表头单元格（可点击排序列）三态背景 — 圆角半透明高亮 */
    public static StateListDrawable headerCellBackgroundStateful(View v) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{R.attr.state_pressed},
                makeBackground(v, 0x50203050, 0, 4, 0));
        sld.addState(new int[]{R.attr.state_hovered},
                makeBackground(v, 0x30182840, 0, 4, 0));
        sld.addState(new int[]{},
                makeBackground(v, 0x00000000, 0, 4, 0));
        return sld;
    }

    // ===================== TextView 工厂 =====================

    public static TextView createText(View context, String text, int color, int sp, boolean singleLine) {
        TextView tv = new TextView(context.getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sp * textScale);
        tv.setIncludeFontPadding(false);
        if (singleLine) {
            tv.setSingleLine();
            tv.setEllipsize(TextUtils.TruncateAt.END);
        }
        return tv;
    }

    public static TextView addText(LinearLayout parent, String text, int color, int sp,
                                   int topMargin, int width, boolean singleLine) {
        TextView tv = createText(parent, text, color, sp, singleLine);
        // width: MATCH_PARENT(-1) 和 WRAP_CONTENT(-2) 应原样传递，0 用于 weight 布局也保留
        int resolvedWidth = (width == ViewGroup.LayoutParams.MATCH_PARENT
                || width == ViewGroup.LayoutParams.WRAP_CONTENT)
                ? width
                : (width <= 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : width);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                resolvedWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = topMargin;
        parent.addView(tv, lp);
        return tv;
    }

    public static TextView tinyButton(View context, String label) {
        TextView button = new TextView(context.getContext());
        button.setText(label);
        button.setTextSize(10 * textScale);
        button.setIncludeFontPadding(false);
        button.setSingleLine();
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextColor(UiThemeTokens.TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4));
        button.setBackground(buttonBackgroundStateful(button));
        button.setClickable(true);
        button.setFocusable(true);
        addPressScaleEffect(button);
        return button;
    }

    /** Fragment 重载 */
    public static TextView tinyButton(Fragment fragment, String label) {
        return tinyButton(Objects.requireNonNull(fragment.getView()), label);
    }

    /**
     * 顶栏图标按钮 —— 圆形背景 + 波纹反馈 + 悬停缩放动画。
     * @param isClose true 时为关闭按钮（红色调），否则为普通图标按钮
     */
    public static TextView chromeIconButton(View context, String symbol, boolean isClose) {
        TextView btn = new TextView(context.getContext());
        btn.setText(symbol);
        btn.setTextSize(11 * textScale);
        btn.setIncludeFontPadding(false);
        btn.setSingleLine();
        btn.setGravity(Gravity.CENTER);
        int size = context.dp(Math.round(22 * layoutScale));
        btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        // 文字颜色
        int normalColor = isClose ? UiThemeTokens.ROSE : UiThemeTokens.TEXT;
        btn.setTextColor(new ColorStateList(
                new int[][]{
                        {R.attr.state_pressed},
                        {R.attr.state_hovered},
                        {}
                },
                new int[]{0xFFFFFFFF, 0xFFFFFFFF, normalColor}
        ));
        // 圆形背景 + 波纹
        btn.setBackground(chromeCircleBg(context, isClose));
        btn.setClickable(true);
        btn.setFocusable(true);
        addHoverScaleEffect(btn);
        return btn;
    }

    /** Fragment 重载 */
    public static TextView chromeIconButton(Fragment fragment, String symbol, boolean isClose) {
        return chromeIconButton(Objects.requireNonNull(fragment.getView()), symbol, isClose);
    }

    /**
     * 顶栏图标按钮 —— 使用自定义 View 作为图标（圆形背景 + 波纹反馈 + 悬停缩放动画）。
     * <p>
     * 用于替代 emoji 文本图标（emoji 在 ModernUI 字体中无法渲染）。
     * 按钮结构：外层 FrameLayout 持有背景和交互，内层 iconView 居中显示。
     */
    public static View chromeDrawableButton(View context, View iconView, boolean isClose) {
        int size = context.dp(Math.round(22 * layoutScale));
        FrameLayout wrapper = new FrameLayout(context.getContext());
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(size, size);
        wrapper.addView(iconView, iconLp);

        // 圆形背景 + 波纹
        wrapper.setBackground(chromeCircleBg(context, isClose));
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        addHoverScaleEffect(wrapper);
        return wrapper;
    }

    /** Fragment 重载 */
    public static View chromeDrawableButton(Fragment fragment, View iconView, boolean isClose) {
        return chromeDrawableButton(Objects.requireNonNull(fragment.getView()), iconView, isClose);
    }

    // ===================== Section 工厂 =====================

    public static LinearLayout section(View context, int width, int height) {
        LinearLayout section = new LinearLayout(context.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(sectionBackground(section));
        section.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        // 允许子 View 缩放 / 动画溢出，不被裁切
        section.setClipChildren(false);
        section.setClipToPadding(false);
        return section;
    }

    /** Fragment 重载 —— 页面构建器传入 terminal（Fragment）时使用 */
    public static LinearLayout section(Fragment fragment, int width, int height) {
        return section(Objects.requireNonNull(fragment.getView()), width, height);
    }

    public static void addSection(ViewGroup parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        parent.addView(child, params);
    }

    // ===================== Layout 辅助 =====================

    public static LinearLayout.LayoutParams leftGap(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = margin;
        return p;
    }

    public static View spacer(View context) {
        View spacer = new View(context.getContext());
        return spacer;
    }

    /** Fragment 重载 */
    public static View spacer(Fragment fragment) {
        return spacer(Objects.requireNonNull(fragment.getView()));
    }

    public static LinearLayout.LayoutParams spacerParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1);
        p.weight = 1.0f;
        return p;
    }

    // ===================== 颜色映射 =====================

    public static int statusColor(OverviewViewModel.Status status) {
        return switch (status) {
            case POSITIVE -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case NEGATIVE -> UiThemeTokens.ROSE;
            case NEUTRAL -> UiThemeTokens.TEXT;
        };
    }

    // ===================== 格式化 =====================

    public static String compact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (abs >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (abs >= 1_000L) return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        return Long.toString(value);
    }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ===================== RippleDrawable 包装 =====================

    /**
     * 用 RippleDrawable 包装 StateListDrawable，为交互元素添加波纹点击反馈。
     */
    public static RippleDrawable wrapRipple(StateListDrawable content, int rippleColor,
                                            View anchor, int radiusDp) {
        ShapeDrawable mask = new ShapeDrawable();
        mask.setColor(0xFFFFFFFF);
        if (radiusDp > 0) mask.setCornerRadius(anchor.dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** 按钮带波纹反馈（胶囊形） */
    public static RippleDrawable buttonRippleBackground(View v) {
        return wrapRipple(buttonBackgroundStateful(v), 0x40FFFFFF, v, 1000);
    }

    /** 标签页带波纹反馈（胶囊形） */
    public static RippleDrawable tabRippleBackground(View v, boolean active) {
        return wrapRipple(tabBackgroundStateful(v, active), 0x30FFFFFF, v, 1000);
    }

    /** 卡片带波纹反馈 */
    public static RippleDrawable cardRippleBackground(View v) {
        return wrapRipple(cardBackgroundStateful(v), 0x20FFFFFF, v, 8);
    }

    // ===================== 动画工具 =====================

    /** 淡入动画：alpha 0→1 */
    public static void fadeIn(View view, long durationMs) {
        view.setAlpha(0f);
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
        anim.setDuration(durationMs);
        anim.setInterpolator(TimeInterpolator.DECELERATE);
        anim.start();
    }

    /** 从底部滑入 + 淡入 */
    public static void slideInFromBottom(View view, float distanceDp, long durationMs) {
        float dist = view.dp(distanceDp);
        view.setAlpha(0f);
        view.setTranslationY(dist);
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dist, 0f));
        anim.setDuration(durationMs);
        anim.setInterpolator(TimeInterpolator.DECELERATE);
        anim.start();
    }

    /** 缩放弹入动画：从 0.85 放大到 1.0 + 淡入 */
    public static void scaleIn(View view, long durationMs) {
        view.setAlpha(0f);
        view.setScaleX(0.85f);
        view.setScaleY(0.85f);
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1f));
        anim.setDuration(durationMs);
        anim.setInterpolator(TimeInterpolator.OVERSHOOT);
        anim.start();
    }

    /** 为 View 添加悬停缩放效果（hover 时放大 1.03，松开恢复）
     *  注意：直接父级容器需预先关闭 clipChildren / clipToPadding，
     *  但 ScrollView 等外层容器必须保留裁切，否则内容会溢出到 UI 外部。 */
    public static void addHoverScaleEffect(View view) {
        view.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == icyllis.modernui.view.MotionEvent.ACTION_HOVER_ENTER) {
                ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, v.getScaleX(), 1.03f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, v.getScaleY(), 1.03f));
                a.setDuration(150);
                a.setInterpolator(TimeInterpolator.DECELERATE);
                a.start();
            } else if (action == icyllis.modernui.view.MotionEvent.ACTION_HOVER_EXIT) {
                ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, v.getScaleX(), 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, v.getScaleY(), 1f));
                a.setDuration(200);
                a.setInterpolator(TimeInterpolator.DECELERATE);
                a.start();
            }
            return false;
        });
    }

    /** 为 View 添加悬停上浮效果（hover 时向上平移 2dp，松开还原）。
     *  translationY 不影响触摸命中区域，适合内部包含可点击子元素的卡片。 */
    public static void addHoverLiftEffect(View view) {
        final float liftPx = view.dp(2);
        view.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == icyllis.modernui.view.MotionEvent.ACTION_HOVER_ENTER) {
                ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y,
                        v.getTranslationY(), -liftPx);
                a.setDuration(150);
                a.setInterpolator(TimeInterpolator.DECELERATE);
                a.start();
            } else if (action == icyllis.modernui.view.MotionEvent.ACTION_HOVER_EXIT) {
                ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y,
                        v.getTranslationY(), 0f);
                a.setDuration(200);
                a.setInterpolator(TimeInterpolator.DECELERATE);
                a.start();
            }
            return false;
        });
    }

    /** 为 View 添加点击缩放反馈（按下缩小 0.95，松开弹回） */
    public static void addPressScaleEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == icyllis.modernui.view.MotionEvent.ACTION_DOWN) {
                ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.95f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.95f));
                a.setDuration(100);
                a.setInterpolator(TimeInterpolator.DECELERATE);
                a.start();
            } else if (action == icyllis.modernui.view.MotionEvent.ACTION_UP
                    || action == icyllis.modernui.view.MotionEvent.ACTION_CANCEL) {
                ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, v.getScaleX(), 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, v.getScaleY(), 1f));
                a.setDuration(200);
                a.setInterpolator(TimeInterpolator.OVERSHOOT);
                a.start();
            }
            return false;
        });
    }

    /** 创建带子视图添加/移除动画的 LayoutTransition */
    public static LayoutTransition smoothLayoutTransition() {
        return new LayoutTransition();
    }

    /**
     * 对列表容器中的每个子 View 依次做延迟滑入动画（瀑布效果）。
     * @param parent 父容器
     * @param startIndex 从第几个子 View 开始（跳过标题等）
     * @param delayPerItemMs 每项延迟间隔
     * @param durationMs 每项动画时长
     */
    public static void staggerSlideIn(ViewGroup parent, int startIndex,
                                      long delayPerItemMs, long durationMs) {
        for (int i = startIndex; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            float dist = child.dp(12);
            child.setAlpha(0f);
            child.setTranslationY(dist);
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(child,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dist, 0f));
            anim.setDuration(durationMs);
            anim.setStartDelay((long) (i - startIndex) * delayPerItemMs);
            anim.setInterpolator(TimeInterpolator.DECELERATE);
            anim.start();
        }
    }

    // ===================== 分组折叠/展开动画 =====================

    /** 展开行容器：GONE → VISIBLE + 逐行淡入滑入 */
    public static void expandRows(ViewGroup container) {
        container.setVisibility(View.VISIBLE);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            float dist = child.dp(8);
            child.setAlpha(0f);
            child.setTranslationY(-dist);
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(child,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -dist, 0f));
            anim.setDuration(180);
            anim.setStartDelay(i * 25L);
            anim.setInterpolator(TimeInterpolator.DECELERATE);
            anim.start();
        }
    }

    /** 折叠行容器：逐行淡出后设为 GONE */
    public static void collapseRows(ViewGroup container) {
        int count = container.getChildCount();
        if (count == 0) {
            container.setVisibility(View.GONE);
            return;
        }
        long totalDuration = 150 + (long) (count - 1) * 15;
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            float dist = child.dp(6);
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(child,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dist));
            anim.setDuration(150);
            anim.setStartDelay(i * 15L);
            anim.setInterpolator(TimeInterpolator.ACCELERATE);
            anim.start();
        }
        container.postDelayed(() -> container.setVisibility(View.GONE), totalDuration + 30);
    }

    // ===================== 收藏/星标动画 =====================

    /** 星标切换弹跳动画 */
    public static void animateStarToggle(View star) {
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(star,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.5f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.5f, 1f));
        anim.setDuration(300);
        anim.setInterpolator(TimeInterpolator.OVERSHOOT);
        anim.start();
    }

    /** 收藏卡片移除动画：缩小 + 淡出，完成后执行回调 */
    public static void animateCardRemove(View card, Runnable onFinished) {
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(card,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f));
        anim.setDuration(200);
        anim.setInterpolator(TimeInterpolator.ACCELERATE);
        anim.start();
        card.postDelayed(onFinished, 220);
    }
}
