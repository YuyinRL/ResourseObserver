package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 原版风格按钮：凸起边框、hover 高亮、点击播放原版按钮音效。
 *
 * <p>构造时传入 onClick 回调，setEnabled(false) 时点击无响应且变灰。</p>
 */
public class ButtonWidget extends BaseWidget {

    /** 按钮点击回调。 */
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(ButtonWidget self);
    }

    private Component label;
    private boolean enabled = true;
    private ClickHandler onClick;
    private int textColor = VanillaTheme.COLOR_TEXT_PRIMARY;

    public ButtonWidget(Component label, ClickHandler onClick) {
        this.label = label == null ? Component.empty() : label;
        this.onClick = onClick;
    }

    public ButtonWidget setLabel(Component label) {
        this.label = label == null ? Component.empty() : label;
        return this;
    }

    public ButtonWidget setEnabled(boolean enabled) { this.enabled = enabled; return this; }
    public ButtonWidget setOnClick(ClickHandler h) { this.onClick = h; return this; }
    public ButtonWidget setTextColor(int argb) { this.textColor = argb; return this; }

    public boolean isEnabled() { return enabled; }
    public Component label() { return label; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        Rect b = bounds();
        boolean hovered = enabled && b.contains(mx, my);

        int fill = !enabled
                ? VanillaTheme.COLOR_BUTTON_BG_DISABLED
                : (hovered ? VanillaTheme.COLOR_BUTTON_BG_HOVER : VanillaTheme.COLOR_BUTTON_BG);

        NinePatch.embossedFill(g, b, fill,
                VanillaTheme.COLOR_BUTTON_HI,
                VanillaTheme.COLOR_BUTTON_LO);

        if (label != null) {
            var font = Minecraft.getInstance().font;
            int tw = font.width(label);
            int tx = b.x() + (b.width() - tw) / 2;
            int ty = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
            int color = enabled ? textColor : VanillaTheme.COLOR_TEXT_DISABLED;
            g.drawString(font, label, tx, ty, color, true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || !enabled || button != 0 || !isMouseOver(mx, my)) return false;
        if (onClick != null) onClick.onClick(this);
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return enabled ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // 简化：不主动 narrate；后续可用 NarratedElementType.TITLE
    }
}
