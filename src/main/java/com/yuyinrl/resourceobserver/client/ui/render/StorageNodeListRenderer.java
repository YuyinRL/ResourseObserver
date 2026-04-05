package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 存储节点列表渲染器 —— 绘制垂直排列的存储节点卡片列表。
 * 每张卡片显示：节点名称、网络类型、已用/总容量、容量进度条 + 百分比、状态徽章。
 * 点击卡片可选中该节点，筛选物品列表。
 */
public final class StorageNodeListRenderer {
    private static final int CARD_HEIGHT = 52;
    private static final int CARD_GAP = 4;
    private static final int PROGRESS_BAR_HEIGHT = 4;

    private StorageNodeListRenderer() {
    }

    /** 计算节点列表区域所需高度 */
    public static int measureHeight(List<StorageNetworkViewModel.NodeEntry> nodes) {
        // title + "All Nodes" card + each node card
        return 16 + (1 + nodes.size()) * (CARD_HEIGHT + CARD_GAP);
    }

    /**
     * 渲染节点列表区域（垂直排列）。
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.NodeEntry> nodes,
            String selectedNodeId,
            int mouseX,
            int mouseY
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.section.nodes"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        int y = content.y() + 14;
        int cardW = content.width();
        boolean noSelection = selectedNodeId == null || selectedNodeId.isBlank();

        List<NodeHitbox> hitboxes = new ArrayList<>();

        // "All Nodes" pseudo card
        UiRect allCard = new UiRect(content.x(), y, cardW, CARD_HEIGHT);
        boolean allHovered = allCard.contains(mouseX, mouseY);
        drawNodeCardVertical(gfx, font, allCard,
                Component.translatable("screen.resourceobserver.storage.node.all").getString(),
                "", -1, -1.0, noSelection, allHovered,
                "", "", "", false);
        hitboxes.add(new NodeHitbox(allCard, null));
        y += CARD_HEIGHT + CARD_GAP;

        // Each binding node
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            if (y + CARD_HEIGHT > content.bottom()) {
                break;
            }
            UiRect card = new UiRect(content.x(), y, cardW, CARD_HEIGHT);
            boolean hovered = card.contains(mouseX, mouseY);
            drawNodeCardVertical(gfx, font, card,
                    node.displayName(),
                    node.networkType(),
                    node.itemCount(),
                    node.capacityRatio(),
                    node.selected(),
                    hovered,
                    node.usedFormatted(),
                    node.totalFormatted(),
                    node.statusLabel(),
                    node.statusAlert());
            hitboxes.add(new NodeHitbox(card, node.nodeId()));
            y += CARD_HEIGHT + CARD_GAP;
        }

        return new RenderResult(hitboxes);
    }

    private static void drawNodeCardVertical(
            GuiGraphics gfx,
            Font font,
            UiRect card,
            String name,
            String networkType,
            int itemCount,
            double capacityRatio,
            boolean selected,
            boolean hovered,
            String usedFormatted,
            String totalFormatted,
            String statusLabel,
            boolean statusAlert
    ) {
        int bg = hovered ? 0xCC1A2E4A : UiThemeTokens.CARD_BG;
        int border = selected ? UiThemeTokens.TAB_ACTIVE : (hovered ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER);
        RenderUtils.fillPanel(gfx, card, bg, border);

        int textX = card.x() + 6;
        int textY = card.y() + 4;
        int maxTextW = card.width() - 12;

        // Node name
        gfx.drawString(font,
                RenderUtils.ellipsis(font, name, maxTextW),
                textX, textY, selected ? UiThemeTokens.CYAN : UiThemeTokens.TITLE);

        // Network type
        if (networkType != null && !networkType.isBlank()) {
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, networkType, maxTextW),
                    textX, textY + 11, UiThemeTokens.TEXT_MUTED);
        }

        // Used / Total capacity text
        if (usedFormatted != null && !usedFormatted.isBlank() && totalFormatted != null && !totalFormatted.isBlank()) {
            String capacityText = usedFormatted + " / " + totalFormatted;
            gfx.drawString(font,
                    RenderUtils.ellipsis(font, capacityText, maxTextW - 60),
                    textX, textY + 22, UiThemeTokens.TEXT);
        }

        // Capacity progress bar + percentage
        if (capacityRatio >= 0.0) {
            int barX = card.x() + 6;
            int barY = card.bottom() - PROGRESS_BAR_HEIGHT - 10;
            int barW = card.width() - 70;
            int fgColor = capacityRatio >= 0.9 ? UiThemeTokens.ROSE
                    : (capacityRatio >= 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
            RenderUtils.drawProgressBar(gfx, barX, barY, barW, PROGRESS_BAR_HEIGHT,
                    capacityRatio, 0x44334455, fgColor);

            // Percentage text
            String pctText = String.format(Locale.ROOT, "%.1f%%", capacityRatio * 100.0);
            gfx.drawString(font, pctText, barX + barW + 4, barY - 1, UiThemeTokens.TEXT);
        }

        // Status badge (right side)
        if (statusLabel != null && !statusLabel.isBlank()) {
            int badgeColor = statusAlert ? UiThemeTokens.ROSE : UiThemeTokens.EMERALD;
            int badgeBg = statusAlert ? 0x44FB7185 : 0x4434D399;
            int badgeW = Math.min(50, font.width(statusLabel) + 8);
            int badgeX = card.right() - badgeW - 6;
            int badgeY = card.y() + 4;
            gfx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, badgeBg);
            gfx.drawString(font, statusLabel, badgeX + 4, badgeY + 2, badgeColor);
        }
    }

    /** 渲染结果 */
    public record RenderResult(List<NodeHitbox> nodeHitboxes) {
    }

    /** 节点热区 —— nodeId 为 null 表示 "All Nodes" */
    public record NodeHitbox(UiRect rect, String nodeId) {
    }
}
