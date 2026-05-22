package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 水平线性布局容器。children 按添加顺序从左到右排列。
 *
 * <p>布局规则（构造期解算，无 measure pass）：
 * <ol>
 *   <li>fixed 子项：固定宽度，由 {@link #addFixed} 指定</li>
 *   <li>flex 子项：按 weight 比例分配剩余空间，由 {@link #addFlex} 指定</li>
 *   <li>子项之间插入 {@code spacing} 间距</li>
 *   <li>所有子项的高度撑满 row 高度</li>
 * </ol>
 *
 * <p>整数除法的余数累计到最后一个 flex 子项，保证总宽度精确等于 row 宽度。</p>
 */
public class Row extends BaseWidget {

    private record Slot(UiWidget widget, int fixedSize, int weight) {}

    private final int spacing;
    private final List<Slot> slots = new ArrayList<>();

    public Row(int spacing) {
        this.spacing = Math.max(0, spacing);
    }

    /** 添加固定宽度子项。 */
    public Row addFixed(UiWidget w, int width) {
        slots.add(new Slot(w, Math.max(0, width), 0));
        return this;
    }

    /** 添加按 weight 分配剩余宽度的子项（weight >= 1）。 */
    public Row addFlex(UiWidget w, int weight) {
        slots.add(new Slot(w, 0, Math.max(1, weight)));
        return this;
    }

    /** 便捷：固定宽度的不可见占位。 */
    public Row addSpacer(int width) {
        return addFixed(new Spacer(), width);
    }

    /** 便捷：按 weight 占位。 */
    public Row addFlexSpacer(int weight) {
        return addFlex(new Spacer(), weight);
    }

    public List<UiWidget> children() {
        List<UiWidget> list = new ArrayList<>(slots.size());
        for (Slot s : slots) list.add(s.widget);
        return Collections.unmodifiableList(list);
    }

    @Override
    public void setBounds(Rect rect) {
        super.setBounds(rect);
    }

    @Override
    protected void onBoundsChanged() {
        layout();
    }

    private void layout() {
        if (slots.isEmpty()) return;
        Rect b = bounds();
        java.util.List<LinearLayoutSolver.Slot> specs = new ArrayList<>(slots.size());
        for (Slot s : slots) specs.add(new LinearLayoutSolver.Slot(s.fixedSize, s.weight));
        int[] sizes = LinearLayoutSolver.solve(specs, b.width(), spacing);

        int x = b.x();
        for (int i = 0; i < slots.size(); i++) {
            int w = sizes[i];
            slots.get(i).widget.setBounds(new Rect(x, b.y(), Math.max(0, w), b.height()));
            x += w;
            if (i < slots.size() - 1) x += spacing;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        for (Slot s : slots) {
            if (s.widget.isVisible()) s.widget.render(g, mx, my, pt);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!isVisible()) return false;
        for (Slot s : slots) {
            if (s.widget.isVisible() && s.widget.isMouseOver(mx, my)
                    && s.widget.mouseClicked(mx, my, btn)) return true;
        }
        return false;
    }
}
