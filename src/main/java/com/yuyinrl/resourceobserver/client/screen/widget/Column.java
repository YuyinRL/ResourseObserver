package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 垂直线性布局容器。children 按添加顺序从上到下排列。
 *
 * <p>布局规则与 {@link Row} 对称（区别仅在轴向）。</p>
 */
public class Column extends BaseWidget {

    private record Slot(UiWidget widget, int fixedSize, int weight) {}

    private final int spacing;
    private final List<Slot> slots = new ArrayList<>();

    public Column(int spacing) {
        this.spacing = Math.max(0, spacing);
    }

    /** 添加固定高度子项。 */
    public Column addFixed(UiWidget w, int height) {
        slots.add(new Slot(w, Math.max(0, height), 0));
        return this;
    }

    /** 添加按 weight 分配剩余高度的子项（weight >= 1）。 */
    public Column addFlex(UiWidget w, int weight) {
        slots.add(new Slot(w, 0, Math.max(1, weight)));
        return this;
    }

    public Column addSpacer(int height) {
        return addFixed(new Spacer(), height);
    }

    public Column addFlexSpacer(int weight) {
        return addFlex(new Spacer(), weight);
    }

    public List<UiWidget> children() {
        List<UiWidget> list = new ArrayList<>(slots.size());
        for (Slot s : slots) list.add(s.widget);
        return Collections.unmodifiableList(list);
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
        int[] sizes = LinearLayoutSolver.solve(specs, b.height(), spacing);

        int y = b.y();
        for (int i = 0; i < slots.size(); i++) {
            int h = sizes[i];
            slots.get(i).widget.setBounds(new Rect(b.x(), y, b.width(), Math.max(0, h)));
            y += h;
            if (i < slots.size() - 1) y += spacing;
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
