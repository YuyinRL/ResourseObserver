package com.yuyinrl.resourceobserver.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/**
 * 新 Vanilla UI 框架的 widget 顶层接口。
 *
 * <p>同时实现 {@link Renderable}、{@link GuiEventListener}、{@link NarratableEntry}
 * 三个原版接口，因此可以直接通过 {@code Screen#addRenderableWidget} 加入
 * 原版 Screen 的渲染/事件循环。</p>
 *
 * <p>设计纪律：
 * <ul>
 *   <li>布局在构造期一次性确定（无 measure pass，简单稳定）</li>
 *   <li>render 期间不分配/不做业务计算，只读 widget 持有的缓存数据</li>
 *   <li>widget 不感知数据来源（Snapshot），由外层在构造或 setData 时注入</li>
 * </ul>
 */
public interface UiWidget extends Renderable, GuiEventListener, NarratableEntry {

    /** 当前包围盒（屏幕坐标）。 */
    Rect bounds();

    /** 重新设定包围盒。容器在 layout 阶段调用。 */
    void setBounds(Rect rect);

    /** 是否可见。不可见 widget 既不渲染也不响应事件。 */
    boolean isVisible();

    /** 设定可见性。 */
    void setVisible(boolean visible);

    /** 显式 tick（每帧一次），默认空实现；按需子类重写。 */
    default void tick() {}

    @Override
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
