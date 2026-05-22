package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.TextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * G2.2 分组命名 / 文本输入弹窗（180dp 等价宽）。
 *
 * <p>构造时传入初值与确认回调；关闭按钮 / Cancel / 失焦 ESC 等价取消。</p>
 */
public class GroupNameDialog extends BasicDialog {

    private final TextField input = new TextField();
    private final Consumer<String> onConfirm;
    private boolean confirmed;

    public GroupNameDialog(Component title, Component placeholder, String initial, Consumer<String> onConfirm) {
        super(title);
        this.onConfirm = onConfirm;
        input.setPlaceholder(placeholder == null ? Component.literal("") : placeholder);
        input.setValue(initial == null ? "" : initial);
        input.setMaxLength(48);

        addFooterButton(Component.translatable("gui.cancel"), b -> requestClose());
        addFooterButton(Component.translatable("gui.ok"), b -> {
            confirmed = true;
            requestClose();
        });
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_SMALL_W, 86);
    }

    @Override
    protected void onClose() {
        if (confirmed && onConfirm != null) {
            onConfirm.accept(input.value());
        }
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        int padding = VanillaTheme.SPACING_S;
        int fieldH = 16;
        Rect fieldRect = new Rect(body.x() + padding, body.y() + padding,
                body.width() - padding * 2, fieldH);
        input.setBounds(fieldRect);
        input.setFocused(true);
        input.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected boolean onBodyMouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter 提交
        if (keyCode == 257 || keyCode == 335) {
            confirmed = true;
            requestClose();
            return true;
        }
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return input.charTyped(codePoint, modifiers);
    }
}
