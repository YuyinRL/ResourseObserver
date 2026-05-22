package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 透明占位 widget：不渲染，仅参与布局（如在 Row/Column 中作为 flex spacer）。
 */
public final class Spacer extends BaseWidget {

    public Spacer() {
        super();
    }

    public Spacer(Rect bounds) {
        super(bounds);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不渲染
    }
}
