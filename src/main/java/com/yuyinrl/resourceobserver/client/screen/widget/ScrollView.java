package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 垂直滚动容器：单一 child，高度可超出 viewport，鼠标滚轮调整 scrollY。
 *
 * <p>使用 {@code GuiGraphics#enableScissor} 做内容裁剪，右侧绘制简易滚动条。
 * child 的 bounds 由本容器统一控制为 {@code (viewport.x, viewport.y - scrollY, viewport.width, contentHeight)}。</p>
 */
public class ScrollView extends BaseWidget {

    private UiWidget child;
    private int contentHeight;
    private int scrollY;
    private final int scrollStep = 12;

    public ScrollView() {}

    public ScrollView setChild(UiWidget child, int contentHeight) {
        this.child = child;
        this.contentHeight = Math.max(0, contentHeight);
        layoutChild();
        return this;
    }

    public ScrollView setContentHeight(int h) {
        this.contentHeight = Math.max(0, h);
        clampScroll();
        layoutChild();
        return this;
    }

    public int scrollY() { return scrollY; }

    public ScrollView setScrollY(int y) {
        this.scrollY = y;
        clampScroll();
        layoutChild();
        return this;
    }

    private int viewportHeight() { return bounds().height(); }
    private int viewportWidth() {
        return Math.max(0, bounds().width()
                - (contentHeight > viewportHeight() ? VanillaTheme.SCROLLBAR_WIDTH : 0));
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight - viewportHeight());
        if (scrollY < 0) scrollY = 0;
        if (scrollY > max) scrollY = max;
    }

    @Override
    protected void onBoundsChanged() {
        clampScroll();
        layoutChild();
    }

    private void layoutChild() {
        if (child == null) return;
        Rect b = bounds();
        child.setBounds(new Rect(b.x(), b.y() - scrollY, viewportWidth(),
                Math.max(viewportHeight(), contentHeight)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        Rect b = bounds();
        g.enableScissor(b.x(), b.y(), b.right(), b.bottom());
        try {
            if (child != null && child.isVisible()) child.render(g, mx, my, pt);
            g.flush();
        } finally {
            g.disableScissor();
        }
        renderScrollbar(g);
    }

    private void renderScrollbar(GuiGraphics g) {
        if (contentHeight <= viewportHeight()) return;
        Rect b = bounds();
        int barX = b.right() - VanillaTheme.SCROLLBAR_WIDTH;
        int barTrackTop = b.y();
        int barTrackBottom = b.bottom();
        // 轨道
        g.fill(barX, barTrackTop, b.right(), barTrackBottom, VanillaTheme.COLOR_PANEL);
        // 滑块
        int trackH = barTrackBottom - barTrackTop;
        int thumbH = Math.max(8, (int) ((long) trackH * viewportHeight() / contentHeight));
        int thumbY = barTrackTop + (int) ((long) (trackH - thumbH) * scrollY
                / Math.max(1, contentHeight - viewportHeight()));
        g.fill(barX, thumbY, b.right(), thumbY + thumbH, VanillaTheme.COLOR_PANEL_ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!isVisible() || !isMouseOver(mx, my) || contentHeight <= viewportHeight()) return false;
        scrollY -= (int) Math.round(sy * scrollStep);
        clampScroll();
        layoutChild();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || child == null) return false;
        if (!isMouseOver(mx, my)) return false;
        // 让 child 自己判断（child 的 bounds 已偏移，所以 mouseY 不需要调整）
        return child.isVisible() && child.mouseClicked(mx, my, button);
    }
}
