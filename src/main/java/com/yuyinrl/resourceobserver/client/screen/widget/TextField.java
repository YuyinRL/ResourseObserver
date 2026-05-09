package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla V2 单行文本输入框。
 */
public class TextField extends BaseWidget {

    /** 文本变化回调。 */
    @FunctionalInterface
    public interface ChangeHandler {
        void onChanged(String value);
    }

    private String value = "";
    private String placeholder = "";
    private int cursor;
    private int maxLength = 64;
    private ChangeHandler onChanged;

    public TextField setValue(String value) {
        this.value = trim(value == null ? "" : value);
        this.cursor = Math.min(cursor, this.value.length());
        return this;
    }

    public String value() {
        return value;
    }

    public TextField setPlaceholder(Component placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder.getString();
        return this;
    }

    public TextField setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        setValue(value);
        return this;
    }

    public TextField setOnChanged(ChangeHandler onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        int fill = isFocused() ? VanillaTheme.COLOR_PANEL_ACCENT : 0xFF_1E_1E_1E;
        NinePatch.framedFill(g, b, fill, VanillaTheme.COLOR_BUTTON_BORDER);

        var font = Minecraft.getInstance().font;
        int textX = b.x() + VanillaTheme.SPACING_S;
        int textY = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
        int textWidth = Math.max(0, b.width() - VanillaTheme.SPACING_M);
        String draw = value.isEmpty() && !isFocused() ? placeholder : value;
        int color = value.isEmpty() && !isFocused()
                ? VanillaTheme.COLOR_TEXT_DISABLED
                : VanillaTheme.COLOR_TEXT_PRIMARY;
        g.drawString(font, fit(draw, textWidth), textX, textY, color, false);

        if (isFocused() && ((Util.getMillis() / 500L) & 1L) == 0L) {
            String beforeCursor = font.plainSubstrByWidth(value.substring(0, cursor), textWidth);
            int cursorX = textX + font.width(beforeCursor);
            if (cursorX < b.right() - VanillaTheme.SPACING_S + 1) {
                g.fill(cursorX, textY - 1, cursorX + 1, textY + VanillaTheme.FONT_HEIGHT + 1,
                        VanillaTheme.COLOR_TEXT_PRIMARY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || button != 0) return false;
        boolean inside = isMouseOver(mouseX, mouseY);
        setFocused(inside);
        if (!inside) return false;
        cursor = value.length();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible() || !isFocused()) return false;
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            cursor = value.length();
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            setText("");
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0 && !value.isEmpty()) {
                    setText(value.substring(0, cursor - 1) + value.substring(cursor));
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < value.length()) {
                    setText(value.substring(0, cursor) + value.substring(cursor + 1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = value.length();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                setFocused(false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isVisible() || !isFocused() || !isAllowedCharacter(codePoint)) return false;
        insert(Character.toString(codePoint));
        return true;
    }

    private static boolean isAllowedCharacter(char codePoint) {
        return codePoint >= 32 && codePoint != 127;
    }

    private void insert(String text) {
        if (text == null || text.isEmpty() || value.length() >= maxLength) return;
        String filtered = text.replace("\r", "").replace("\n", "");
        int allowed = Math.min(filtered.length(), maxLength - value.length());
        if (allowed <= 0) return;
        setText(value.substring(0, cursor) + filtered.substring(0, allowed) + value.substring(cursor));
        cursor += allowed;
    }

    private void setText(String next) {
        value = trim(next == null ? "" : next);
        cursor = Math.min(cursor, value.length());
        if (onChanged != null) onChanged.onChanged(value);
    }

    private String trim(String text) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width);
    }
}
