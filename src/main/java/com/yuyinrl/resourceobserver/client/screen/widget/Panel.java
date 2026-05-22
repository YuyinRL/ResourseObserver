package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单容器：children 按其自身 bounds 在 panel 内以绝对坐标渲染。
 *
 * <p>Panel 不做布局重计算；当 panel 自身 bounds 变化时不会自动调整 children。
 * 适合需要精确像素布局或与 Row/Column 嵌套使用的场景。</p>
 *
 * <p>事件分发顺序：从最后添加的子向最先添加的子（绘制顺序的反向），
 * 与原版 Screen 的"上层优先"约定一致。</p>
 */
public class Panel extends BaseWidget {

    private final List<UiWidget> children = new ArrayList<>();

    public Panel() {
        super();
    }

    public Panel(Rect bounds) {
        super(bounds);
    }

    /** 添加一个子 widget，返回 panel 自身以便链式调用。 */
    public Panel add(UiWidget child) {
        if (child != null) children.add(child);
        return this;
    }

    /** 移除全部子 widget。 */
    public void clear() {
        children.clear();
    }

    /** 子 widget 列表的不可变视图。 */
    public List<UiWidget> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        for (UiWidget c : children) {
            if (c.isVisible()) {
                c.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            UiWidget c = children.get(i);
            if (c.isVisible() && c.isMouseOver(mouseX, mouseY)
                    && c.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        boolean handled = false;
        for (int i = children.size() - 1; i >= 0; i--) {
            UiWidget c = children.get(i);
            if (c.isVisible() && c.mouseReleased(mouseX, mouseY, button)) {
                handled = true;
                break;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isVisible()) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            UiWidget c = children.get(i);
            if (c.isVisible() && c.isMouseOver(mouseX, mouseY)
                    && c.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UiWidget c = children.get(i);
            if (c.isVisible() && c.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        for (UiWidget c : children) {
            if (c.isVisible()) c.tick();
        }
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        for (UiWidget c : children) {
            if (c.isVisible()) c.updateNarration(output);
        }
    }
}
