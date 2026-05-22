package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ProgressBar;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Power 负载分类堆叠条。
 */
public class PowerLoadSegmentBar extends BaseWidget {

    private final ProgressBar bar = new ProgressBar();
    private List<PowerNetworkViewModel.LoadSegment> segments = List.of();

    public PowerLoadSegmentBar setSegments(List<PowerNetworkViewModel.LoadSegment> segments) {
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        List<ProgressBar.Segment> bars = new ArrayList<>();
        for (PowerNetworkViewModel.LoadSegment segment : this.segments) {
            bars.add(new ProgressBar.Segment(segment.percentage() / 100.0, segment.color()));
        }
        bar.setSegments(bars);
        return this;
    }

    @Override
    protected void onBoundsChanged() {
        Rect b = bounds();
        int barH = Math.min(10, Math.max(0, b.height() - VanillaTheme.FONT_HEIGHT - VanillaTheme.SPACING_XS));
        bar.setBounds(new Rect(b.x(), b.y() + 2, b.width(), barH));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        bar.render(g, mouseX, mouseY, partialTick);
        if (segments.isEmpty()) return;

        var font = Minecraft.getInstance().font;
        int x = bounds().x();
        int y = bounds().bottom() - VanillaTheme.FONT_HEIGHT;
        for (PowerNetworkViewModel.LoadSegment segment : segments) {
            String name = segment.displayName() == null ? "" : segment.displayName();
            String text = name + " " + String.format(Locale.ROOT, "%.1f%%", segment.percentage());
            int color = segment.color();
            if (x + font.width(text) > bounds().right()) break;
            g.drawString(font, text, x, y, color, true);
            x += font.width(text) + VanillaTheme.SPACING_M;
        }
    }
}
