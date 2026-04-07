package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.TerminalPage;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 标签页栏视图 —— 显示 Overview / Storage / Power 三个标签。
 * <p>
 * 当前活跃的标签使用高亮背景和青色文本；
 * 点击非活跃标签触发回调切换页面。
 */
public class TabBarView extends LinearLayout {

    private final TerminalPage activePage;

    public TabBarView(Context context, TerminalPage activePage, Consumer<TerminalPage> onPageSelected) {
        super(context);
        this.activePage = activePage;
        setOrientation(HORIZONTAL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(8), dp(4), dp(8), dp(4));

        for (TerminalPage page : TerminalPage.values()) {
            boolean active = (page == activePage);
            TextView tab = createTab(context, page, active);
            if (!active) {
                tab.setOnClickListener(v -> onPageSelected.accept(page));
            }
            LayoutParams params = new LayoutParams(dp(80), ViewGroup.LayoutParams.MATCH_PARENT);
            if (page.ordinal() > 0) {
                params.leftMargin = dp(4);
            }
            addView(tab, params);
        }
    }

    private TextView createTab(Context context, TerminalPage page, boolean active) {
        TextView tv = new TextView(context);
        String label = Component.translatable(page.translationKey()).getString();
        tv.setText(label);
        tv.setTextSize(sp(10));
        tv.setTextColor(active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT);
        tv.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        tv.setGravity(icyllis.modernui.view.Gravity.CENTER);

        ShapeDrawable tabBg = new ShapeDrawable();
        tabBg.setCornerRadius(dp(3));
        tabBg.setColor(active ? UiThemeTokens.TAB_ACTIVE : UiThemeTokens.TAB_INACTIVE);
        tabBg.setStroke(dp(1), UiThemeTokens.DIVIDER);
        tv.setBackground(tabBg);
        tv.setClickable(!active);
        return tv;
    }
}
