package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModelMapper;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * G6.3 存储详情弹窗 —— 展示 AE2 Disk / External × Item / Fluid 4 通道详情。
 *
 * <p>对齐 ModernUI {@code OverviewPageBuilder.populateStorageDetailBody}：
 * 提示文本 + 磁盘段（item + fluid）+ 外储段（item + fluid）+ 可靠性行。</p>
 */
public class StorageDetailDialog extends BasicDialog {

    private final Supplier<OverviewSnapshot.StorageDetailSnapshot> snapshotSupplier;
    private final int titleColor;
    private int scroll;

    public StorageDetailDialog(Component title,
                               int titleColor,
                               Supplier<OverviewSnapshot.StorageDetailSnapshot> snapshotSupplier) {
        super(title);
        this.titleColor = titleColor;
        this.snapshotSupplier = snapshotSupplier == null
                ? () -> null
                : snapshotSupplier;
        addFooterButton(Component.translatable("gui.ok"), b -> requestClose());
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_KPI_W, VanillaTheme.DIALOG_KPI_H);
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        if (body.width() <= 0 || body.height() <= 0) return;
        OverviewSnapshot.StorageDetailSnapshot snap = snapshotSupplier.get();
        if (snap == null) return;
        OverviewViewModel.StorageDetail detail = OverviewViewModelMapper.toStorageDetail(snap);

        var font = Minecraft.getInstance().font;
        int innerLeft = body.x() + VanillaTheme.SPACING_M;
        int innerRight = body.right() - VanillaTheme.SPACING_M;
        int viewTop = body.y() + VanillaTheme.SPACING_S;
        int viewH = body.height() - VanillaTheme.SPACING_S * 2;

        int rowH = VanillaTheme.FONT_HEIGHT + 2;

        // 计算总内容高度（粗略估算）
        int contentH = rowH; // hint
        contentH += rowH + 6; // divider + section header
        contentH += channelHeight(detail.diskItem(), rowH);
        contentH += channelHeight(detail.diskFluid(), rowH);
        contentH += rowH + 6;
        contentH += channelHeight(detail.externalItem(), rowH);
        contentH += channelHeight(detail.externalFluid(), rowH);
        contentH += rowH + 6;
        contentH += rowH;

        int maxScroll = Math.max(0, contentH - viewH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int y = viewTop - scroll;

        // 提示文字
        String hint = detail.hintText() == null || detail.hintText().isBlank()
                ? Component.translatable("screen.resourceobserver.overview.kpi.storage.normal").getString()
                : detail.hintText();
        y = drawLine(g, font, hint, innerLeft, y, withAlpha(titleColor != 0 ? titleColor : VanillaTheme.COLOR_TEXT_PRIMARY));

        // 分隔线 + 磁盘
        y = drawDivider(g, innerLeft, innerRight, y);
        y = drawLine(g, font,
                Component.translatable("screen.resourceobserver.overview.storage.detail.section.disk").getString(),
                innerLeft, y, withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY));
        y = drawChannel(g, font, detail.diskItem(), innerLeft, innerRight, y);
        y = drawChannel(g, font, detail.diskFluid(), innerLeft, innerRight, y);

        // 分隔线 + 外储
        y = drawDivider(g, innerLeft, innerRight, y);
        y = drawLine(g, font,
                Component.translatable("screen.resourceobserver.overview.storage.detail.section.external").getString(),
                innerLeft, y, withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY));
        y = drawChannel(g, font, detail.externalItem(), innerLeft, innerRight, y);
        y = drawChannel(g, font, detail.externalFluid(), innerLeft, innerRight, y);

        // 可靠性
        y = drawDivider(g, innerLeft, innerRight, y);
        String diskBool = boolLabel(detail.diskReliable());
        String extBool = boolLabel(detail.externalReliable());
        String reliable = Component.translatable(
                "screen.resourceobserver.overview.storage.detail.reliability",
                diskBool, extBool).getString();
        drawLine(g, font, reliable, innerLeft, y, withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));

        g.flush();
        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int trackH = body.height() - VanillaTheme.SPACING_S * 2;
            int thumbH = Math.max(8, (int) ((float) viewH / contentH * trackH));
            int trackY = body.y() + VanillaTheme.SPACING_S;
            int thumbY = trackY + (int) ((float) scroll / maxScroll * (trackH - thumbH));
            g.fill(body.right() - 3, thumbY, body.right() - 1, thumbY + thumbH,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }
    }

    private int channelHeight(OverviewViewModel.StorageChannel ch, int rowH) {
        if (ch == null) return 0;
        // label + usage + types
        return rowH * 3 + 2;
    }

    private int drawChannel(GuiGraphics g, net.minecraft.client.gui.Font font,
                             OverviewViewModel.StorageChannel ch, int left, int right, int y) {
        if (ch == null) return y;
        int rowH = VanillaTheme.FONT_HEIGHT + 2;
        // 通道标签
        g.drawString(font, ch.label() == null ? "" : ch.label(),
                left + VanillaTheme.SPACING_S, y, withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
        y += rowH;
        // 用量
        String usage = blankToNa(ch.usageText());
        g.drawString(font, usage, left + VanillaTheme.SPACING_S, y,
                withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);
        y += rowH;
        // 类型
        String types = blankToNa(ch.typesText());
        g.drawString(font, types, left + VanillaTheme.SPACING_S, y,
                withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
        y += rowH + 2;
        return y;
    }

    private int drawLine(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        if (text == null || text.isBlank()) return y;
        g.drawString(font, text, x, y, color, false);
        return y + VanillaTheme.FONT_HEIGHT + 2;
    }

    private int drawDivider(GuiGraphics g, int left, int right, int y) {
        int dy = y + 2;
        g.fill(left, dy, right, dy + 1, withAlpha(VanillaTheme.COLOR_DIVIDER));
        return y + 6;
    }

    private static String blankToNa(String s) {
        return s == null || s.isBlank() ? "N/A" : s;
    }

    private static String boolLabel(boolean value) {
        return Component.translatable(value
                ? "message.resourceobserver.debug.bool.true"
                : "message.resourceobserver.debug.bool.false").getString();
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
