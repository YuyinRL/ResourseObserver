package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储节点选择器，包含“全部节点”和每个网络节点 chip。
 */
public class StorageNodeSelector extends BaseWidget {

    /** 节点选择回调。 */
    @FunctionalInterface
    public interface SelectionHandler {
        void onSelected(String nodeId);
    }

    private List<StorageNetworkViewModel.NodeEntry> nodes = List.of();
    private String selectedNodeId;
    private SelectionHandler onSelected;
    private final List<Hit> hits = new ArrayList<>();

    public StorageNodeSelector setNodes(List<StorageNetworkViewModel.NodeEntry> nodes, String selectedNodeId) {
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.selectedNodeId = selectedNodeId;
        return this;
    }

    public StorageNodeSelector setOnSelected(SelectionHandler onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        rebuildHits();
        for (Hit hit : hits) {
            boolean selected = same(selectedNodeId, hit.nodeId());
            boolean hovered = hit.rect().contains(mouseX, mouseY);
            int fill = selected ? VanillaTheme.COLOR_STATUS_INFO
                    : (hovered ? VanillaTheme.COLOR_BUTTON_BG_HOVER : VanillaTheme.COLOR_BUTTON_BG);
            NinePatch.embossedFill(g, hit.rect(), fill, VanillaTheme.COLOR_BUTTON_HI, VanillaTheme.COLOR_BUTTON_LO);
            var font = Minecraft.getInstance().font;
            int tx = hit.rect().x() + (hit.rect().width() - font.width(hit.label())) / 2;
            int ty = hit.rect().y() + (hit.rect().height() - VanillaTheme.FONT_HEIGHT) / 2;
            g.drawString(font, hit.label(), tx, ty, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        rebuildHits();
        for (Hit hit : hits) {
            if (!hit.rect().contains(mouseX, mouseY)) continue;
            if (!same(selectedNodeId, hit.nodeId())) {
                selectedNodeId = hit.nodeId();
                if (onSelected != null) onSelected.onSelected(selectedNodeId);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return false;
    }

    private void rebuildHits() {
        hits.clear();
        Rect b = bounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        var font = Minecraft.getInstance().font;
        int x = b.x();
        addHit(null, Component.literal("全部节点").getString(), x, b, font.width("全部节点"));
        x = hits.isEmpty() ? b.x() : hits.get(hits.size() - 1).rect().right() + VanillaTheme.SPACING_S;
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            String label = node.displayName() == null || node.displayName().isBlank()
                    ? node.nodeId()
                    : node.displayName();
            String fitted = fit(label, 84);
            int w = font.width(fitted);
            if (x + w + 16 > b.right()) break;
            addHit(node.nodeId(), fitted, x, b, w);
            x = hits.get(hits.size() - 1).rect().right() + VanillaTheme.SPACING_S;
        }
    }

    private void addHit(String nodeId, String label, int x, Rect b, int textWidth) {
        int w = Math.max(54, Math.min(96, textWidth + 16));
        if (x + w > b.right()) return;
        hits.add(new Hit(new Rect(x, b.y(), w, Math.min(VanillaTheme.BUTTON_HEIGHT, b.height())), nodeId, label));
    }

    private static boolean same(String left, String right) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        return l.equals(r);
    }

    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private record Hit(Rect rect, String nodeId, String label) {
    }
}
