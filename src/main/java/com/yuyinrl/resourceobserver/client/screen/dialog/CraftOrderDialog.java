package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.LongConsumer;

/**
 * G2.3 一键合成下单弹窗。
 *
 * <p>仅接收数字输入；Confirm 时回调 {@link LongConsumer#accept(long)}（amount > 0 才回调）。</p>
 */
public class CraftOrderDialog extends BasicDialog {

    private final TextField input = new TextField();
    private final LongConsumer onConfirm;
    private long pendingAmount;
    private boolean focusedOnce;

    public CraftOrderDialog(Component title, long initialAmount, LongConsumer onConfirm) {
        super(title);
        this.onConfirm = onConfirm;
        input.setPlaceholder(Component.translatable("screen.resourceobserver.crafting.order_hint"));
        input.setValue(Long.toString(Math.max(1L, initialAmount)));
        input.setMaxLength(12);

        addFooterButton(Component.translatable("screen.resourceobserver.crafting.order_cancel"),
                b -> requestClose());
        addFooterButton(Component.translatable("screen.resourceobserver.crafting.order_confirm"),
                b -> tryConfirm());
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_SMALL_W + 28, 112);
    }

    private void tryConfirm() {
        String s = input.value() == null ? "" : input.value().trim();
        long amount;
        try {
            amount = Long.parseLong(s);
        } catch (NumberFormatException e) {
            amount = 0L;
        }
        if (amount <= 0L) return;
        pendingAmount = amount;
        requestClose();
    }

    @Override
    protected void onClose() {
        if (pendingAmount > 0L && onConfirm != null) {
            onConfirm.accept(pendingAmount);
        }
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        int padding = VanillaTheme.SPACING_S;
        var font = Minecraft.getInstance().font;
        Component hint = Component.translatable("screen.resourceobserver.crafting.order_hint");
        g.drawString(font, hint, body.x() + padding, body.y() + padding,
                withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
        g.flush();

        int fieldH = VanillaTheme.BUTTON_HEIGHT;
        Rect fieldRect = new Rect(
                body.x() + padding,
                body.y() + padding + VanillaTheme.FONT_HEIGHT + VanillaTheme.SPACING_S,
                body.width() - padding * 2, fieldH);
        input.setBounds(fieldRect);
        if (!focusedOnce) {
            input.setFocused(true);
            focusedOnce = true;
        }
        input.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected boolean onBodyMouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            tryConfirm();
            return true;
        }
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 仅允许数字输入
        if (codePoint < '0' || codePoint > '9') return false;
        return input.charTyped(codePoint, modifiers);
    }
}
