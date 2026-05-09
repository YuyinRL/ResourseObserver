package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tooltip 宿主：包裹一个子 widget，鼠标 hover 时绘制 tooltip。
 *
 * <p>tooltip 内容通过 {@link Supplier} 延迟生成，避免每帧重新创建。</p>
 */
public class TooltipHost extends BaseWidget {

    private final UiWidget child;
    private Supplier<List<Component>> tooltipSupplier;

    public TooltipHost(UiWidget child, Supplier<List<Component>> tooltipSupplier) {
        this.child = child;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void setBounds(Rect rect) {
        super.setBounds(rect);
        if (child != null) child.setBounds(rect);
    }

    public TooltipHost setTooltipSupplier(Supplier<List<Component>> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!isVisible()) return;
        if (child != null && child.isVisible()) child.render(g, mx, my, pt);
        if (tooltipSupplier != null && bounds().contains(mx, my)) {
            List<Component> lines = tooltipSupplier.get();
            if (lines != null && !lines.isEmpty()) {
                g.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || child == null) return false;
        return child.isMouseOver(mx, my) && child.mouseClicked(mx, my, button);
    }
}
