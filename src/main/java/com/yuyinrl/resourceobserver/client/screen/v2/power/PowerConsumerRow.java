package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ProgressBar;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Power 用电器表格单行。
 */
public class PowerConsumerRow extends BaseWidget {

    private final PowerNetworkViewModel.ConsumerEntry consumer;
    private final ProgressBar percentageBar = new ProgressBar();

    public PowerConsumerRow(PowerNetworkViewModel.ConsumerEntry consumer) {
        this.consumer = consumer;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible() || consumer == null) return;
        Rect b = bounds();
        boolean hovered = b.contains(mouseX, mouseY);
        int color = consumer.color();
        g.fill(b.x(), b.y(), b.right(), b.bottom(), hovered ? 0x30_FF_FF_FF : 0x18_FF_FF_FF);
        g.fill(b.x(), b.y(), b.x() + 3, b.bottom(), color);

        var font = Minecraft.getInstance().font;
        int y = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
        int x = b.x() + 7;
        int countW = 34;
        int barW = 70;
        int energyW = 70;
        int modW = 58;
        int gap = VanillaTheme.SPACING_S;
        int nameW = Math.max(34, b.right() - x - countW - barW - energyW - modW - gap * 4);

        g.drawString(font, fit(consumer.deviceName(), nameW), x, y, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        x += nameW + gap;
        g.drawString(font, fit(consumer.modName(), modW), x, y, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        x += modW + gap;
        g.drawString(font, energyText(consumer.consumptionPerTick()), x, y, VanillaTheme.COLOR_STATUS_WARN, false);
        x += energyW + gap;

        percentageBar.setProgress(consumer.percentage() / 100.0).setFillColor(color);
        percentageBar.setBounds(new Rect(x, b.y() + 5, barW, 6));
        percentageBar.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, percentText(consumer.percentage()), x, b.y() + 14, VanillaTheme.COLOR_TEXT_DISABLED, false);
        x += barW + gap;
        renderCountBadge(g, new Rect(x, b.y() + 5, countW, 12), consumer.count());
    }

    private static String energyText(long value) {
        return FormatUtils.formatCompact(value) + " FE/t";
    }

    private static String percentText(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static void renderCountBadge(GuiGraphics g, Rect rect, int count) {
        var font = Minecraft.getInstance().font;
        String text = "×" + count;
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xFF_1B_1B_1B);
        g.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), VanillaTheme.COLOR_STATUS_INFO);
        int tx = rect.x() + Math.max(3, (rect.width() - font.width(text)) / 2);
        g.drawString(font, text, tx, rect.y() + 2, VanillaTheme.COLOR_STATUS_INFO, true);
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }
}
