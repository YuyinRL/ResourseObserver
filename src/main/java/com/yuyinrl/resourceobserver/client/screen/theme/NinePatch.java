package com.yuyinrl.resourceobserver.client.screen.theme;

import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 极简 9-patch / 边框绘制工具。新 Vanilla UI 框架以最少绘制调用呈现"框 + 边 + 内填"。
 *
 * <p>本类不做实际 9-patch 切片，而是通过四条 {@code fill} 边框 + 内部填充得到 vanilla 风的硬边框。
 * 后续若引入资源贴图（如原版 button 9-patch），可在此扩展同名重载方法。</p>
 */
public final class NinePatch {

    private NinePatch() {}

    /** 画一个带 1px 边框的矩形（先画填充，再覆盖一圈边框）。 */
    public static void framedFill(GuiGraphics g, Rect r, int fill, int border) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return;
        int x1 = r.x();
        int y1 = r.y();
        int x2 = r.right();
        int y2 = r.bottom();
        g.fill(x1, y1, x2, y2, fill);
        g.fill(x1, y1, x2, y1 + 1, border);
        g.fill(x1, y2 - 1, x2, y2, border);
        g.fill(x1, y1, x1 + 1, y2, border);
        g.fill(x2 - 1, y1, x2, y2, border);
    }

    /** 仅画 1px 边框（不填充）。 */
    public static void border(GuiGraphics g, Rect r, int color) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return;
        int x1 = r.x();
        int y1 = r.y();
        int x2 = r.right();
        int y2 = r.bottom();
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    /** 凸起边框（左/上 高光，右/下 阴影），原版按钮风格。 */
    public static void embossedFill(GuiGraphics g, Rect r, int fill, int hi, int lo) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return;
        int x1 = r.x();
        int y1 = r.y();
        int x2 = r.right();
        int y2 = r.bottom();
        g.fill(x1, y1, x2, y2, fill);
        g.fill(x1, y1, x2, y1 + 1, hi);
        g.fill(x1, y1, x1 + 1, y2, hi);
        g.fill(x1, y2 - 1, x2, y2, lo);
        g.fill(x2 - 1, y1, x2, y2, lo);
    }
}
