package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * G2.4-A 计算中提示弹窗：不可关闭的进度条 dialog，等待服务端回传计划摘要。
 */
public class CraftPlanningDialog extends BasicDialog {

    private final String message;
    private long startMs;

    public CraftPlanningDialog(Component title, String message) {
        super(title);
        this.message = message == null ? "" : message;
        this.startMs = System.currentTimeMillis();
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_PROGRESS_W, 72);
    }

    @Override
    public boolean dismissOnDimClick() { return false; }

    @Override
    public boolean dismissOnEsc() { return false; }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int padding = VanillaTheme.SPACING_S;
        // 文字提示（按字体宽度逐行截断）
        int textY = body.y() + padding;
        int innerW = body.width() - padding * 2;
        String remaining = message;
        int safety = 6;
        while (!remaining.isEmpty() && safety-- > 0) {
            String line = font.width(remaining) <= innerW
                    ? remaining
                    : font.plainSubstrByWidth(remaining, innerW);
            g.drawString(font, line, body.x() + padding, textY,
                    withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);
            textY += VanillaTheme.FONT_HEIGHT + 1;
            if (line.length() >= remaining.length()) break;
            remaining = remaining.substring(line.length());
        }
        // 不确定进度条：循环游标
        int barY = body.bottom() - padding - 5;
        int barX0 = body.x() + padding;
        int barX1 = body.right() - padding;
        g.fill(barX0, barY, barX1, barY + 3, withAlpha(VanillaTheme.COLOR_DIVIDER));
        long t = System.currentTimeMillis() - startMs;
        int total = barX1 - barX0;
        int chunkW = Math.max(20, total / 4);
        int phase = (int) ((t / 12) % (total + chunkW)) - chunkW;
        int leadX = barX0 + Math.max(0, phase);
        int trailX = Math.min(barX1, barX0 + phase + chunkW);
        if (trailX > leadX) {
            g.fill(leadX, barY, trailX, barY + 3, withAlpha(VanillaTheme.COLOR_STATUS_INFO));
        }
    }

}
