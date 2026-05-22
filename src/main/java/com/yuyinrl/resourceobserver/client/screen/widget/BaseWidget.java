package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/**
 * widget 基类：管理包围盒 / 可见性 / 焦点 / 默认空实现。
 *
 * <p>子类只需覆盖 {@link #render(GuiGraphics, int, int, float)} 与必要的事件钩子。</p>
 */
public abstract class BaseWidget implements UiWidget {

    private Rect bounds;
    private boolean visible = true;
    private boolean focused;

    protected BaseWidget(Rect bounds) {
        this.bounds = bounds == null ? Rect.EMPTY : bounds;
    }

    protected BaseWidget() {
        this(Rect.EMPTY);
    }

    @Override
    public final Rect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(Rect rect) {
        this.bounds = rect == null ? Rect.EMPTY : rect;
        onBoundsChanged();
    }

    /** 子类在 bounds 变化时重新计算自身布局可重写本方法。 */
    protected void onBoundsChanged() {}

    @Override
    public final boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && bounds.contains(mx, my);
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean f) {
        this.focused = f;
    }

    @Override
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // 默认 narration 静默；具体 widget 按需重写。
    }
}
