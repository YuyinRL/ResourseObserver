package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * G2.1 KPI 详情弹窗（Overview / Power 共用）。
 *
 * <p>采用"内容供应器"模式：每帧从 supplier 取最新行列表，使弹窗能随快照实时刷新。</p>
 */
public class KpiDetailDialog extends BasicDialog {

    /** 单行：左侧标签 + 右侧值，按状态着色。 */
    public record Line(String label, String value, int valueColor) {}

    private final Supplier<List<Line>> linesSupplier;
    private final Size size;
    private int scroll;

    public KpiDetailDialog(Component title, Size size, Supplier<List<Line>> linesSupplier) {
        super(title);
        this.size = size == null ? new Size(VanillaTheme.DIALOG_KPI_SMALL_W, VanillaTheme.DIALOG_KPI_SMALL_H) : size;
        this.linesSupplier = linesSupplier == null ? List::<Line>of : linesSupplier;
        addFooterButton(Component.translatable("gui.ok"), b -> requestClose());
    }

    @Override
    public Size preferredSize() {
        return size;
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        if (body.width() <= 0 || body.height() <= 0) return;
        var font = Minecraft.getInstance().font;
        List<Line> lines = linesSupplier.get();
        if (lines == null) lines = List.of();

        int rowH = VanillaTheme.FONT_HEIGHT + VanillaTheme.SPACING_S;
        int contentH = lines.size() * rowH;
        int viewH = body.height() - VanillaTheme.SPACING_S * 2;
        int maxScroll = Math.max(0, contentH - viewH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        int innerLeft = body.x() + VanillaTheme.SPACING_S;
        int innerRight = body.right() - VanillaTheme.SPACING_S;
        int top = body.y() + VanillaTheme.SPACING_S;

        // 简易剪裁：通过 enableScissor
        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int y = top - scroll;
        for (Line line : lines) {
            if (y + rowH < body.y()) { y += rowH; continue; }
            if (y > body.bottom()) break;
            String label = line.label() == null ? "" : line.label();
            String value = line.value() == null ? "" : line.value();
            g.drawString(font, label, innerLeft, y, withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
            int vw = font.width(value);
            int valueColor = withAlpha(line.valueColor() == 0 ? VanillaTheme.COLOR_TEXT_PRIMARY : line.valueColor());
            g.drawString(font, value, innerRight - vw, y, valueColor, false);
            // 分割线
            g.fill(innerLeft, y + VanillaTheme.FONT_HEIGHT + 1,
                    innerRight, y + VanillaTheme.FONT_HEIGHT + 2,
                    withAlpha(VanillaTheme.COLOR_DIVIDER));
            y += rowH;
        }
        g.flush();
        g.disableScissor();

        // 滚动条提示
        if (maxScroll > 0) {
            int trackH = body.height() - VanillaTheme.SPACING_S * 2;
            int thumbH = Math.max(8, (int) ((float) viewH / contentH * trackH));
            int trackY = body.y() + VanillaTheme.SPACING_S;
            int thumbY = trackY + (int) ((float) scroll / maxScroll * (trackH - thumbH));
            g.fill(body.right() - 3, thumbY, body.right() - 1, thumbY + thumbH,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bodyRect().contains(mouseX, mouseY)) {
            scroll -= (int) (scrollY * 12);
            return true;
        }
        return false;
    }
}
