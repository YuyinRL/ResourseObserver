package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 容器：顶部为 tab 按钮条，下方根据选中 tab 显示对应内容 widget。
 *
 * <p>tab 个数与 page 数量在构造完毕后即固定（用 {@link #addTab} 累加）。
 * 选中的 page 会根据本 widget 的 bounds 被自动布局到 tab 条下方。</p>
 */
public class Tabs extends BaseWidget {

    private record TabEntry(Component label, UiWidget content) {}

    private final List<TabEntry> tabs = new ArrayList<>();
    private int selected;
    private final int tabBarHeight;

    /** 选中变化回调（可选）。 */
    @FunctionalInterface
    public interface SelectionListener { void onSelected(int newIndex); }
    private SelectionListener listener;

    public Tabs() {
        this(VanillaTheme.TAB_HEIGHT);
    }

    public Tabs(int tabBarHeight) {
        this.tabBarHeight = Math.max(16, tabBarHeight);
    }

    public Tabs addTab(Component label, UiWidget content) {
        tabs.add(new TabEntry(label, content));
        return this;
    }

    public Tabs setOnSelected(SelectionListener l) { this.listener = l; return this; }

    public int selectedIndex() { return selected; }

    public void select(int index) {
        if (index < 0 || index >= tabs.size() || index == selected) return;
        selected = index;
        layoutContent();
        if (listener != null) listener.onSelected(selected);
    }

    @Override
    protected void onBoundsChanged() {
        layoutContent();
    }

    private void layoutContent() {
        if (tabs.isEmpty()) return;
        Rect b = bounds();
        Rect contentArea = new Rect(b.x(), b.y() + tabBarHeight,
                b.width(), Math.max(0, b.height() - tabBarHeight));
        for (int i = 0; i < tabs.size(); i++) {
            UiWidget c = tabs.get(i).content;
            if (c != null) {
                c.setVisible(i == selected);
                c.setBounds(contentArea);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        Rect b = bounds();
        renderTabBar(g, mx, my, b);
        if (selected >= 0 && selected < tabs.size()) {
            UiWidget c = tabs.get(selected).content;
            if (c != null) c.render(g, mx, my, pt);
        }
    }

    private void renderTabBar(GuiGraphics g, int mx, int my, Rect b) {
        if (tabs.isEmpty()) return;
        int n = tabs.size();
        int tabW = b.width() / n;
        int leftover = b.width() - tabW * n;
        int x = b.x();
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < n; i++) {
            int w = tabW + (i == n - 1 ? leftover : 0);
            Rect r = new Rect(x, b.y(), w, tabBarHeight);
            boolean isSelected = i == selected;
            boolean hovered = !isSelected && r.contains(mx, my);
            int fill = isSelected
                    ? VanillaTheme.COLOR_PANEL_ACCENT
                    : (hovered ? VanillaTheme.COLOR_BUTTON_BG_HOVER : VanillaTheme.COLOR_BUTTON_BG);
            NinePatch.embossedFill(g, r, fill,
                    VanillaTheme.COLOR_BUTTON_HI,
                    VanillaTheme.COLOR_BUTTON_LO);
            Component label = tabs.get(i).label;
            if (label != null) {
                int tw = font.width(label);
                int tx = r.x() + (r.width() - tw) / 2;
                int ty = r.y() + (r.height() - VanillaTheme.FONT_HEIGHT) / 2;
                g.drawString(font, label, tx, ty,
                        isSelected ? VanillaTheme.COLOR_TEXT_PRIMARY : VanillaTheme.COLOR_TEXT_SECONDARY,
                        true);
            }
            x += w;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || button != 0) return false;
        Rect b = bounds();
        // tab 条命中
        if (mx >= b.x() && mx < b.right() && my >= b.y() && my < b.y() + tabBarHeight) {
            int n = tabs.size();
            if (n == 0) return false;
            int relX = (int) (mx - b.x());
            int tabW = b.width() / n;
            int idx = Math.min(n - 1, relX / Math.max(1, tabW));
            if (idx != selected) {
                select(idx);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        // 内容区域命中
        if (selected >= 0 && selected < tabs.size()) {
            UiWidget c = tabs.get(selected).content;
            if (c != null && c.isVisible() && c.isMouseOver(mx, my)) {
                return c.mouseClicked(mx, my, button);
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (selected >= 0 && selected < tabs.size()) {
            UiWidget c = tabs.get(selected).content;
            if (c != null && c.isVisible() && c.isMouseOver(mx, my)) {
                return c.mouseScrolled(mx, my, sx, sy);
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected >= 0 && selected < tabs.size()) {
            UiWidget c = tabs.get(selected).content;
            if (c != null && c.isVisible()) return c.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }
}
