package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.dialog.Dialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 右键菜单（轻量浮层 Dialog）。
 *
 * <p>无 dim 遮罩、无 chrome、无入场缩放，纯列表式弹窗。
 * 由 {@link DialogHost} 统一管理生命周期：点击外部 / ESC 关闭。
 *
 * <p>支持二级子菜单（点击主菜单中带 ▶ 的项时，关闭自身并在右侧/原位再打开一个子菜单）。
 */
public final class ContextMenu extends Dialog {

    /** 单条菜单项。 */
    public static final class MenuItem {
        public final Component label;
        public final Runnable action;
        public final boolean hasSubMenu;
        public final boolean disabled;
        public final boolean separator;

        private MenuItem(Component label, Runnable action, boolean hasSubMenu, boolean disabled, boolean separator) {
            this.label = label;
            this.action = action;
            this.hasSubMenu = hasSubMenu;
            this.disabled = disabled;
            this.separator = separator;
        }

        public static MenuItem of(Component label, Runnable action) {
            return new MenuItem(label, action, false, false, false);
        }

        public static MenuItem submenu(Component label, Runnable openSubMenu) {
            return new MenuItem(label, openSubMenu, true, false, false);
        }

        public static MenuItem disabled(Component label) {
            return new MenuItem(label, null, false, true, false);
        }

        public static MenuItem separator() {
            return new MenuItem(Component.empty(), null, false, true, true);
        }
    }

    private static final int ITEM_H = 14;
    private static final int SEPARATOR_H = 5;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 3;
    private static final int MIN_W = 90;
    private static final int MAX_W = 200;

    private static final int COLOR_BG = 0xFF_22_22_22;
    private static final int COLOR_BORDER = 0xFF_00_00_00;
    private static final int COLOR_HOVER = 0xFF_44_44_44;
    private static final int COLOR_SEPARATOR = 0xFF_55_55_55;

    private final List<MenuItem> items;
    private int hoverIndex = -1;

    public ContextMenu(List<MenuItem> items) {
        super(Component.empty());
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public boolean dimEnabled() {
        return false;
    }

    @Override
    public boolean centerScaleAnimation() {
        return false;
    }

    @Override
    public Size preferredSize() {
        var font = Minecraft.getInstance().font;
        int w = MIN_W;
        int h = PADDING_Y * 2;
        for (MenuItem item : items) {
            if (item.separator) {
                h += SEPARATOR_H;
                continue;
            }
            int textW = font.width(item.label) + PADDING_X * 2 + (item.hasSubMenu ? 10 : 0);
            if (textW > w) w = textW;
            h += ITEM_H;
        }
        if (w > MAX_W) w = MAX_W;
        return new Size(w, h);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect b = bounds();
        int alphaBg = (int) Math.round(((COLOR_BG >>> 24) & 0xFF) * currentAlpha());
        int bg = (alphaBg << 24) | (COLOR_BG & 0x00FF_FFFF);
        int border = withAlpha(COLOR_BORDER);
        g.fill(b.x() - 1, b.y() - 1, b.right() + 1, b.bottom() + 1, border);
        g.fill(b.x(), b.y(), b.right(), b.bottom(), bg);

        var font = Minecraft.getInstance().font;
        int y = b.y() + PADDING_Y;
        hoverIndex = computeHoverIndex(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            if (item.separator) {
                int sy = y + SEPARATOR_H / 2;
                g.fill(b.x() + 6, sy, b.right() - 6, sy + 1, withAlpha(COLOR_SEPARATOR));
                y += SEPARATOR_H;
                continue;
            }
            if (i == hoverIndex && !item.disabled) {
                g.fill(b.x() + 1, y, b.right() - 1, y + ITEM_H, withAlpha(COLOR_HOVER));
            }
            int color = item.disabled
                    ? withAlpha(VanillaTheme.COLOR_TEXT_DISABLED)
                    : withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY);
            String label = fit(item.label.getString(), b.width() - PADDING_X * 2 - (item.hasSubMenu ? 10 : 0));
            g.drawString(font, label, b.x() + PADDING_X, y + (ITEM_H - VanillaTheme.FONT_HEIGHT) / 2, color, false);
            if (item.hasSubMenu) {
                g.drawString(font, "\u25B6", b.right() - PADDING_X - 6, y + (ITEM_H - VanillaTheme.FONT_HEIGHT) / 2, color, false);
            }
            y += ITEM_H;
        }
    }

    private int computeHoverIndex(int mouseX, int mouseY) {
        Rect b = bounds();
        if (mouseX < b.x() || mouseX > b.right() || mouseY < b.y() || mouseY > b.bottom()) return -1;
        int y = b.y() + PADDING_Y;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int h = item.separator ? SEPARATOR_H : ITEM_H;
            if (mouseY >= y && mouseY < y + h) {
                return item.separator || item.disabled ? -1 : i;
            }
            y += h;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int idx = computeHoverIndex((int) mouseX, (int) mouseY);
        if (idx < 0) return false;
        MenuItem item = items.get(idx);
        if (item.disabled || item.action == null) return false;
        // 关闭自身后再执行 action（action 内部可 openAt 二级菜单）
        requestClose();
        try {
            item.action.run();
        } catch (Exception ignored) {
            // 静默失败避免卡 UI
        }
        return true;
    }

    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("\u2026"))) + "\u2026";
    }

    /** 便捷构造：直接 list of items。 */
    public static ContextMenu of(MenuItem... items) {
        List<MenuItem> list = new ArrayList<>(items.length);
        for (MenuItem item : items) {
            if (item != null) list.add(item);
        }
        return new ContextMenu(list);
    }
}
