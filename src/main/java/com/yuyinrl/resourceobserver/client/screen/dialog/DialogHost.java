package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * V2 Dialog 模态浮层管理器。
 *
 * <p>一个 Screen 持有一个 host 实例，集中负责：
 * <ul>
 *   <li>Dialog 栈维护（push / closeTop / closeAll）</li>
 *   <li>事件优先拦截（mouseClick / keyPressed / charTyped / scroll / drag / release）</li>
 *   <li>渲染顺序：dim overlay 在主内容之上，dialog 在 dim 之上，多 dialog 时栈底先画</li>
 *   <li>居中布局：依据 dialog 的 preferredSize 居中至 screen</li>
 * </ul>
 *
 * <p>子组件可通过 {@link #current()} 静态访问当前活跃 host（仅当某个 V2 Screen 注册了自身时非空）。</p>
 */
public final class DialogHost {

    /** dim overlay 最大透明度；默认 dialog 不启用，仅保留给少数需要压暗背景的弹窗。 */
    private static final int DIM_MAX_ALPHA = 0x44;

    private static volatile DialogHost CURRENT;

    private final Screen screen;
    private final Deque<Dialog> stack = new ArrayDeque<>();

    public DialogHost(Screen screen) {
        this.screen = screen;
    }

    /** 注册为当前活跃 host（在 Screen.init 调用）。 */
    public void install() {
        CURRENT = this;
    }

    /** 注销当前活跃 host（在 Screen.removed 调用）。 */
    public void uninstall() {
        if (CURRENT == this) {
            CURRENT = null;
        }
        for (Dialog d : stack) {
            d.requestClose();
        }
        stack.clear();
    }

    /** 当前活跃 host（可空）。 */
    public static DialogHost current() {
        return CURRENT;
    }

    /** 打开一个 dialog（推入栈顶并居中布局）。 */
    public void open(Dialog dialog) {
        Dialog.Size size = dialog.preferredSize();
        int sw = screen.width;
        int sh = screen.height;
        int w = Math.min(size.width(), sw - 8);
        int h = Math.min(size.height(), sh - 8);
        int x = (sw - w) / 2;
        int y = (sh - h) / 2;
        dialog.setBounds(new Rect(x, y, w, h));
        stack.push(dialog);
        dialog.attach(this);
    }

    /**
     * 在指定锚点位置打开 dialog（用于 ContextMenu 等浮层）。
     * 自动越界翻转：若右下放不下，向左/上翻转。
     */
    public void openAt(Dialog dialog, int anchorX, int anchorY) {
        Dialog.Size size = dialog.preferredSize();
        int sw = screen.width;
        int sh = screen.height;
        int w = Math.min(size.width(), sw - 8);
        int h = Math.min(size.height(), sh - 8);
        int x = anchorX;
        int y = anchorY;
        if (x + w > sw - 4) x = Math.max(4, anchorX - w);
        if (y + h > sh - 4) y = Math.max(4, anchorY - h);
        if (x < 4) x = 4;
        if (y < 4) y = 4;
        dialog.setBounds(new Rect(x, y, w, h));
        stack.push(dialog);
        dialog.attach(this);
    }

    /** 关闭栈顶 dialog（开始出场动画）。 */
    public void closeTop() {
        Dialog top = stack.peek();
        if (top != null) {
            top.requestClose();
        }
    }

    /** 关闭所有 dialog。 */
    public void closeAll() {
        for (Dialog d : stack) {
            d.requestClose();
        }
    }

    public boolean isAnyOpen() {
        return !stack.isEmpty();
    }

    public Dialog top() {
        return stack.peek();
    }

    /** 每帧 tick：移除已完全关闭的 dialog。 */
    public void tick() {
        stack.removeIf(Dialog::isFullyClosed);
        for (Dialog d : stack) {
            d.tick();
        }
    }

    /** 渲染所有 dialog（在主内容之上）。 */
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (stack.isEmpty()) {
            return;
        }
        Dialog top = stack.peek();
        if (top.dimEnabled()) {
            float dimAlpha = top.currentAlpha();
            int dim = ((int) (DIM_MAX_ALPHA * dimAlpha) << 24);
            g.fill(0, 0, screen.width, screen.height, dim);
            g.flush();
        }

        Iterator<Dialog> it = stack.descendingIterator();
        while (it.hasNext()) {
            renderScaled(g, it.next(), mouseX, mouseY, partialTick);
            g.flush();
        }
    }

    private void renderScaled(GuiGraphics g, Dialog d, int mouseX, int mouseY, float partialTick) {
        if (!d.centerScaleAnimation()) {
            d.render(g, mouseX, mouseY, partialTick);
            return;
        }
        g.flush();
        float scale = d.currentScale();
        Rect b = d.bounds();
        float cx = b.x() + b.width() / 2f;
        float cy = b.y() + b.height() / 2f;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-cx, -cy, 0);
        d.render(g, mouseX, mouseY, partialTick);
        g.flush();
        pose.popPose();
    }

    // ===================== 事件拦截 =====================

    public boolean mouseClicked(double mx, double my, int btn) {
        Dialog top = stack.peek();
        if (top == null || top.isFullyClosed()) {
            return false;
        }
        if (top.isMouseOver(mx, my)) {
            top.mouseClicked(mx, my, btn);
            return true;
        }
        if (btn == 0 && top.dismissOnDimClick()) {
            top.requestClose();
        }
        return true;
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        Dialog top = stack.peek();
        if (top == null) {
            return false;
        }
        top.mouseReleased(mx, my, btn);
        return true;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        Dialog top = stack.peek();
        if (top == null) {
            return false;
        }
        top.mouseDragged(mx, my, btn, dx, dy);
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double sdx, double sdy) {
        Dialog top = stack.peek();
        if (top == null) {
            return false;
        }
        top.mouseScrolled(mx, my, sdx, sdy);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Dialog top = stack.peek();
        if (top == null) {
            return false;
        }
        if (keyCode == 256 /* GLFW_KEY_ESCAPE */ && top.dismissOnEsc()) {
            top.requestClose();
            return true;
        }
        top.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(char c, int modifiers) {
        Dialog top = stack.peek();
        if (top == null) {
            return false;
        }
        top.charTyped(c, modifiers);
        return true;
    }
}
