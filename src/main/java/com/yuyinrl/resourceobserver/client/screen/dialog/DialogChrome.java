package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Dialog 通用外壳：标题栏 / 边框 / ✕ 关闭按钮的工具方法。
 *
 * <p>所有方法均接受 ARGB 颜色（alpha 通道由调用方按需透传 dialog 当前透明度）。</p>
 */
public final class DialogChrome {

    /** 标题栏高度（含 padding）。 */
    public static final int TITLE_BAR_HEIGHT = 18;
    /** ✕ 按钮宽高。 */
    public static final int CLOSE_BTN_SIZE = 12;
    /** ✕ 按钮距右上 padding。 */
    public static final int CLOSE_BTN_PAD = 4;
    /** 标题左 padding。 */
    public static final int TITLE_LEFT_PAD = 8;

    private DialogChrome() {}

    /** 标题栏矩形（位于 dialog 顶部）。 */
    public static Rect titleBarRect(Rect dialog) {
        return new Rect(dialog.x(), dialog.y(), dialog.width(), TITLE_BAR_HEIGHT);
    }

    /** ✕ 按钮矩形（位于标题栏右侧）。 */
    public static Rect closeButtonRect(Rect dialog) {
        int x = dialog.x() + dialog.width() - CLOSE_BTN_SIZE - CLOSE_BTN_PAD;
        int y = dialog.y() + (TITLE_BAR_HEIGHT - CLOSE_BTN_SIZE) / 2;
        return new Rect(x, y, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);
    }

    /** 内容区矩形（标题栏下方）。 */
    public static Rect contentRect(Rect dialog) {
        return new Rect(
                dialog.x() + 1,
                dialog.y() + TITLE_BAR_HEIGHT,
                dialog.width() - 2,
                dialog.height() - TITLE_BAR_HEIGHT - 1);
    }

    /** 绘制 dialog 外框。 */
    public static void drawFrame(GuiGraphics g, Rect dialog, int bgColor, int borderColor) {
        NinePatch.framedFill(g, dialog, bgColor, borderColor);
    }

    /**
     * 绘制完整外壳（边框 + 标题栏 + 标题文本 + ✕ 按钮）。
     */
    public static void drawChrome(GuiGraphics g, Rect dialog, Component title, Font font,
                                  int bgColor, int borderColor, int titleBarColor,
                                  int titleColor, int closeIconColor, int closeIconHoverColor,
                                  int mouseX, int mouseY) {
        drawFrame(g, dialog, bgColor, borderColor);
        Rect tb = titleBarRect(dialog);
        g.fill(tb.x() + 1, tb.y() + 1, tb.x() + tb.width() - 1, tb.y() + tb.height(), titleBarColor);
        g.fill(tb.x() + 1, tb.y() + tb.height() - 1, tb.x() + tb.width() - 1, tb.y() + tb.height(),
                borderColor);
        if (title != null) {
            g.drawString(font, title,
                    tb.x() + TITLE_LEFT_PAD,
                    tb.y() + (TITLE_BAR_HEIGHT - VanillaTheme.FONT_HEIGHT) / 2,
                    titleColor, false);
        }
        Rect btn = closeButtonRect(dialog);
        boolean hover = btn.contains(mouseX, mouseY);
        int icon = hover ? closeIconHoverColor : closeIconColor;
        int x0 = btn.x() + 2;
        int y0 = btn.y() + 2;
        int x1 = btn.x() + btn.width() - 2;
        int y1 = btn.y() + btn.height() - 2;
        drawLine(g, x0, y0, x1, y1, icon);
        drawLine(g, x0, y1, x1, y0, icon);
        if (hover) {
            g.fill(btn.x(), btn.y(), btn.x() + btn.width(), btn.y() + 1, icon);
            g.fill(btn.x(), btn.y() + btn.height() - 1, btn.x() + btn.width(), btn.y() + btn.height(), icon);
            g.fill(btn.x(), btn.y(), btn.x() + 1, btn.y() + btn.height(), icon);
            g.fill(btn.x() + btn.width() - 1, btn.y(), btn.x() + btn.width(), btn.y() + btn.height(), icon);
        }
    }

    /** 简易直线（Bresenham）：用 1×1 fill 像素近似。 */
    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int safety = 256;
        while (safety-- > 0) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx)  { err += dx; y0 += sy; }
        }
    }

    /** 鼠标是否点中 ✕。 */
    public static boolean isCloseClicked(Rect dialog, double mx, double my) {
        return closeButtonRect(dialog).contains(mx, my);
    }
}
