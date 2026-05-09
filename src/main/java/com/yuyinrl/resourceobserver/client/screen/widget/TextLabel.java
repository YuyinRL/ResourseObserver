package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 单行文本标签。支持左/居中/右对齐与垂直居中。
 */
public class TextLabel extends BaseWidget {

    public enum HAlign { LEFT, CENTER, RIGHT }

    private Component text;
    private int color = VanillaTheme.COLOR_TEXT_PRIMARY;
    private boolean shadow = true;
    private HAlign align = HAlign.LEFT;
    private boolean verticalCenter = true;

    public TextLabel(Component text) {
        this.text = text == null ? Component.empty() : text;
    }

    public TextLabel setText(Component text) {
        this.text = text == null ? Component.empty() : text;
        return this;
    }

    public TextLabel setColor(int argb) { this.color = argb; return this; }
    public TextLabel setShadow(boolean s) { this.shadow = s; return this; }
    public TextLabel setAlign(HAlign a) { this.align = a; return this; }
    public TextLabel setVerticalCenter(boolean v) { this.verticalCenter = v; return this; }

    public Component text() { return text; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible() || text == null) return;
        Rect b = bounds();
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int x = switch (align) {
            case LEFT -> b.x();
            case CENTER -> b.x() + (b.width() - textWidth) / 2;
            case RIGHT -> b.right() - textWidth;
        };
        int y = verticalCenter
                ? b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2
                : b.y();
        g.drawString(font, text, x, y, color, shadow);
    }
}
