package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

/**
 * 对话框/弹窗 UI 公共片段（"Chrome"）。
 *
 * <p>该工具类抽离了多个 PageBuilder（Overview/PowerNetwork/...）共享的对话框装饰组件，
 * 用于消除重复代码并保持视觉风格一致。当前包含：
 * <ul>
 *   <li>{@link #addScrollFadeOverlays} —— 滚动容器顶/底部渐隐遮罩</li>
 * </ul>
 *
 * <p>所有方法均为静态、无状态，且依赖 {@link ResourceTerminalFragment#dp(int)} 进行 dp→px 转换。
 */
final class DialogChrome {

    private DialogChrome() {
        // 工具类禁止实例化
    }

    /**
     * 在 FrameLayout 内添加顶部和底部渐隐遮罩。
     *
     * <p>用于暗示 ScrollView 内容可继续滚动 —— 顶部从面板色渐隐到透明，底部反之。
     * 遮罩高度固定为 14dp，颜色取自 {@link UiThemeTokens#PANEL_BG}。
     *
     * @param terminal 终端 Fragment（提供 Context 与 dp 计算）
     * @param wrapper  目标 FrameLayout，遮罩将作为最后两个子 View 添加
     */
    static void addScrollFadeOverlays(ResourceTerminalFragment terminal, FrameLayout wrapper) {
        int fadeH = terminal.dp(14);
        int panelBg = UiThemeTokens.PANEL_BG;
        int transparent = panelBg & 0x00FFFFFF; // 同色，alpha=0

        // 顶部渐隐：不透明 → 透明
        View topFade = new View(terminal.getContext());
        GradientDrawable topGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{panelBg, transparent});
        topGrad.setCornerRadius(0);
        topFade.setBackground(topGrad);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, fadeH);
        topLp.gravity = Gravity.TOP;
        wrapper.addView(topFade, topLp);

        // 底部渐隐：透明 → 不透明
        View bottomFade = new View(terminal.getContext());
        GradientDrawable bottomGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{transparent, panelBg});
        bottomGrad.setCornerRadius(0);
        bottomFade.setBackground(bottomGrad);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, fadeH);
        bottomLp.gravity = Gravity.BOTTOM;
        wrapper.addView(bottomFade, bottomLp);
    }
}
