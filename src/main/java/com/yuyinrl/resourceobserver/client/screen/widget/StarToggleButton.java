package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.V2UiActions;
import com.yuyinrl.resourceobserver.network.UiActionType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 星标切换按钮：点击触发 {@link UiActionType#TOGGLE_WATCH}，并播放 300ms 弹跳 + 旋转动画。
 *
 * <p>状态：
 * <ul>
 *   <li>{@code starred=true} → 渲染填充五角星 ★（金黄色）</li>
 *   <li>{@code starred=false} → 渲染空心 ☆（半透灰）</li>
 * </ul>
 *
 * <p>动画：点击瞬间 t=0 → t=300ms：scale 在 0..150ms 0.0→1.5 OVERSHOOT，150..300ms 1.5→1.0 ease-out；
 * 旋转 0..300ms 线性 0°→360°。
 */
public class StarToggleButton extends BaseWidget {

    private static final int SIZE = 12;
    private static final long ANIM_MS = 300L;

    private static final int COLOR_STARRED = 0xFF_E0_C0_40;
    private static final int COLOR_UNSTARRED = 0x80_FF_FF_FF;
    private static final int COLOR_HOVER = 0xFF_FF_E0_60;

    private boolean starred;
    private String itemId = "";
    private long animStartMs = -1L;

    public StarToggleButton() {
        super(new Rect(0, 0, SIZE, SIZE));
    }

    public StarToggleButton setStarred(boolean starred) {
        this.starred = starred;
        return this;
    }

    public StarToggleButton setItemId(String itemId) {
        this.itemId = itemId == null ? "" : itemId;
        return this;
    }

    public boolean starred() {
        return starred;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        starred = !starred;
        animStartMs = System.currentTimeMillis();
        if (!itemId.isBlank()) {
            V2UiActions.send(UiActionType.TOGGLE_WATCH, itemId, "");
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        boolean hovered = b.contains(mouseX, mouseY);

        float scale = 1f;
        float rotateDeg = 0f;
        if (animStartMs >= 0L) {
            long dt = System.currentTimeMillis() - animStartMs;
            if (dt >= ANIM_MS) {
                animStartMs = -1L;
            } else {
                float t = dt / (float) ANIM_MS;
                rotateDeg = 360f * t;
                if (t < 0.5f) {
                    float u = t / 0.5f;
                    scale = 0f + 1.5f * easeOutOvershoot(u);
                } else {
                    float u = (t - 0.5f) / 0.5f;
                    scale = 1.5f - 0.5f * easeOut(u);
                }
            }
        }

        int color = starred ? (hovered ? COLOR_HOVER : COLOR_STARRED) : COLOR_UNSTARRED;
        String glyph = starred ? "\u2605" : "\u2606"; // ★ / ☆

        var font = Minecraft.getInstance().font;
        float cx = b.x() + b.width() / 2f;
        float cy = b.y() + b.height() / 2f;
        int gw = font.width(glyph);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        if (scale != 1f || rotateDeg != 0f) {
            pose.scale(scale, scale, 1f);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotateDeg));
        }
        pose.translate(-gw / 2f, -VanillaTheme.FONT_HEIGHT / 2f, 0);
        g.drawString(font, glyph, 0, 0, color, false);
        pose.popPose();
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    private static float easeOutOvershoot(float t) {
        final float s = 1.70158f;
        float u = t - 1f;
        return u * u * ((s + 1f) * u + s) + 1f;
    }
}
