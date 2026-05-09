package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * 图标按钮：仅图标不带文字。图标使用 {@link ResourceLocation} 指向纹理 sprite。
 *
 * <p>纹理来源由调用方决定（mod 自带或 vanilla GUI sprites）。</p>
 */
public class IconButton extends BaseWidget {

    @FunctionalInterface
    public interface ClickHandler { void onClick(IconButton self); }

    private final ResourceLocation icon;
    private final int iconWidth;
    private final int iconHeight;
    private boolean enabled = true;
    private ClickHandler onClick;

    public IconButton(ResourceLocation icon, int iconWidth, int iconHeight, ClickHandler onClick) {
        this.icon = icon;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.onClick = onClick;
    }

    public IconButton setEnabled(boolean enabled) { this.enabled = enabled; return this; }
    public IconButton setOnClick(ClickHandler h) { this.onClick = h; return this; }
    public boolean isEnabled() { return enabled; }

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

        if (icon != null) {
            int ix = b.x() + (b.width() - iconWidth) / 2;
            int iy = b.y() + (b.height() - iconHeight) / 2;
            // blit 使用 blitSprite 适配 1.21+ 资源管理
            g.blitSprite(icon, ix, iy, iconWidth, iconHeight);
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
}
