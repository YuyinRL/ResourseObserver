package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.ButtonWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 Dialog 基类：自动渲染外壳（{@link DialogChrome}）+ ✕ 关闭按钮 + 可选页脚按钮区。
 *
 * <p>子类只需实现 {@link #renderBody(GuiGraphics, Rect, int, int, float)} 渲染主体内容；
 * 页脚通过 {@link #addFooterButton(Component, ButtonWidget.OnPress)} 配置。</p>
 */
public abstract class BasicDialog extends Dialog {

    /** 页脚按钮高度。 */
    protected static final int FOOTER_BTN_H = 18;
    /** 页脚区域高度（含 padding）。 */
    protected static final int FOOTER_H = FOOTER_BTN_H + VanillaTheme.SPACING_S * 2;

    private final List<ButtonWidget> footerButtons = new ArrayList<>();

    protected BasicDialog(Component title) {
        super(title);
    }

    /** 添加一个页脚按钮（由右往左排列）。 */
    protected ButtonWidget addFooterButton(Component label, ButtonWidget.ClickHandler onPress) {
        ButtonWidget btn = new ButtonWidget(label, onPress);
        footerButtons.add(btn);
        return btn;
    }

    /** 内容区（不含标题栏 / 页脚）。 */
    protected Rect bodyRect() {
        Rect content = DialogChrome.contentRect(bounds());
        if (footerButtons.isEmpty()) return content;
        return new Rect(content.x(), content.y(), content.width(),
                Math.max(0, content.height() - FOOTER_H));
    }

    /** 子类绘制主体（已应用透明度，可直接用 {@link #withAlpha(int)}）。 */
    protected abstract void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick);

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (currentAlpha() <= 0f) return;
        DialogChrome.drawChrome(g, bounds(), title(), Minecraft.getInstance().font,
                withAlpha(VanillaTheme.COLOR_DIALOG_BG),
                withAlpha(VanillaTheme.COLOR_DIALOG_BORDER),
                withAlpha(VanillaTheme.COLOR_DIALOG_TITLEBAR),
                withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY),
                withAlpha(VanillaTheme.COLOR_DIALOG_CLOSE_ICON),
                withAlpha(VanillaTheme.COLOR_DIALOG_CLOSE_ICON_HOVER),
                mouseX, mouseY);
        g.flush();

        renderBody(g, bodyRect(), mouseX, mouseY, partialTick);
        g.flush();

        layoutFooter();
        for (ButtonWidget btn : footerButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }
        g.flush();
    }

    private void layoutFooter() {
        if (footerButtons.isEmpty()) return;
        Rect content = DialogChrome.contentRect(bounds());
        int y = content.bottom() - VanillaTheme.SPACING_S - FOOTER_BTN_H;
        int rightX = content.right() - VanillaTheme.SPACING_S;
        for (ButtonWidget btn : footerButtons) {
            int w = Math.max(48, Minecraft.getInstance().font.width(btn.label().getString()) + VanillaTheme.SPACING_M * 2);
            int x = rightX - w;
            btn.setBounds(new Rect(x, y, w, FOOTER_BTN_H));
            rightX = x - VanillaTheme.SPACING_S;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (DialogChrome.isCloseClicked(bounds(), mouseX, mouseY) && button == 0) {
            requestClose();
            return true;
        }
        for (ButtonWidget btn : footerButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return onBodyMouseClicked(mouseX, mouseY, button);
    }

    /** 子类可重写以处理 body 区点击；默认无操作。 */
    protected boolean onBodyMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** 暴露给子类访问按钮的钩子（少数情况需要禁用按钮）。 */
    protected List<ButtonWidget> footerButtons() {
        return footerButtons;
    }
}
