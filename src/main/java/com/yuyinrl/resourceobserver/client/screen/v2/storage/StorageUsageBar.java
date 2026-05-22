package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ProgressBar;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储类型占比的多段水平条。
 */
public class StorageUsageBar extends BaseWidget {

    private final ProgressBar bar = new ProgressBar();
    private List<StorageNetworkViewModel.UsageSegment> segments = List.of();

    public StorageUsageBar setSegments(List<StorageNetworkViewModel.UsageSegment> segments) {
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        List<ProgressBar.Segment> bars = new ArrayList<>();
        for (StorageNetworkViewModel.UsageSegment segment : this.segments) {
            bars.add(new ProgressBar.Segment(segment.percentage() / 100.0, segment.color()));
        }
        bar.setSegments(bars);
        return this;
    }

    @Override
    protected void onBoundsChanged() {
        Rect b = bounds();
        bar.setBounds(new Rect(b.x(), b.y() + 2, b.width(), Math.min(10, Math.max(0, b.height() - 12))));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        bar.render(g, mouseX, mouseY, partialTick);
        if (segments.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int x = bounds().x();
        int y = bounds().bottom() - VanillaTheme.FONT_HEIGHT;
        for (StorageNetworkViewModel.UsageSegment segment : segments) {
            String text = segment.displayName() + " " + Math.round(segment.percentage()) + "%";
            int color = segment.color();
            if (x + font.width(text) > bounds().right()) break;
            g.drawString(font, text, x, y, color, true);
            x += font.width(text) + VanillaTheme.SPACING_M;
        }
    }
}
