package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.integration.CraftingTreeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * G6.1 合成树查看弹窗（720×480dp 等价）。
 *
 * <p>对齐 ModernUI {@code CraftingSubTabBuilder} 的合成树视图：
 * <ul>
 *   <li>双向滚动（左右上下）</li>
 *   <li>Ctrl+滚轮缩放 0.4x ~ 2.5x</li>
 *   <li>节点卡片边框颜色：缺料 ROSE / depth=0 AMBER / 非叶 EMERALD / 叶 CARD_BORDER</li>
 *   <li>水平树展开（左→右），子节点上下排列</li>
 * </ul>
 */
public class CraftingTreeDialog extends BasicDialog {

    private static final int NODE_W = 170;
    private static final int NODE_H = 38;
    private static final int H_GAP = 14;     // 父子之间水平间隙
    private static final int V_GAP = 6;      // 兄弟节点垂直间隙
    private static final int PADDING = 12;
    private static final int LINE_COLOR = VanillaTheme.COLOR_DIVIDER;

    private final CraftingTreeNode root;
    private float zoom = 1.0f;
    private int scrollX;
    private int scrollY;

    public CraftingTreeDialog(Component title, CraftingTreeNode root) {
        super(title);
        this.root = root;
        addFooterButton(Component.translatable("gui.ok"), b -> requestClose());
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_XL_W, VanillaTheme.DIALOG_XL_H);
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        if (body.width() <= 0 || body.height() <= 0) return;
        if (root == null) {
            Font font = Minecraft.getInstance().font;
            g.drawString(font, "(no tree)", body.x() + PADDING, body.y() + PADDING,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED), false);
            return;
        }

        Font font = Minecraft.getInstance().font;
        int viewLeft = body.x() + PADDING;
        int viewTop = body.y() + PADDING;
        int viewRight = body.right() - PADDING;
        int viewBottom = body.bottom() - PADDING;

        int[] totalH = {0};
        Layout layout = computeLayout(root, NODE_W, NODE_H, H_GAP, V_GAP, totalH);
        int contentW = Math.round((layout.maxRight - layout.left) * zoom);
        int contentH = Math.round(totalH[0] * zoom);
        int viewW = viewRight - viewLeft;
        int viewH = viewBottom - viewTop;
        int maxX = Math.max(0, contentW - viewW);
        int maxY = Math.max(0, contentH - viewH);
        if (scrollX > maxX) scrollX = maxX;
        if (scrollY > maxY) scrollY = maxY;
        if (scrollX < 0) scrollX = 0;
        if (scrollY < 0) scrollY = 0;

        g.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(viewLeft - scrollX, viewTop - scrollY, 0);
            pose.scale(zoom, zoom, 1.0f);
            renderNode(g, font, root, 0, 0, layout, NODE_W, NODE_H, H_GAP, V_GAP, 0);
            g.flush();
        } finally {
            pose.popPose();
            g.flush();
            g.disableScissor();
        }

        // 滚动条提示
        if (maxY > 0) {
            int trackH = viewH;
            int thumbH = Math.max(8, (int) ((float) viewH / contentH * trackH));
            int thumbY = viewTop + (int) ((float) scrollY / maxY * (trackH - thumbH));
            g.fill(viewRight + 2, thumbY, viewRight + 4, thumbY + thumbH,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }
        if (maxX > 0) {
            int trackW = viewW;
            int thumbW = Math.max(8, (int) ((float) viewW / contentW * trackW));
            int thumbX = viewLeft + (int) ((float) scrollX / maxX * (trackW - thumbW));
            g.fill(thumbX, viewBottom + 2, thumbX + thumbW, viewBottom + 4,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }

        // 缩放提示
        String hint = String.format("Zoom %.0f%% (Ctrl+wheel)", zoom * 100f);
        int hw = font.width(hint);
        g.drawString(font, hint, viewRight - hw, viewTop, withAlpha(VanillaTheme.COLOR_TEXT_DISABLED), false);
    }

    /** 计算节点子树的总高度，用于垂直布局。 */
    private static class Layout {
        int height;     // 子树总高度
        int left;       // 起始 X（相对父节点根）
        int maxRight;   // 子树覆盖的最右 X
    }

    private Layout computeLayout(CraftingTreeNode node, int nodeW, int nodeH, int hGap, int vGap, int[] totalH) {
        Layout l = new Layout();
        l.left = 0;
        l.maxRight = nodeW;
        if (node == null || node.children() == null || node.children().isEmpty()) {
            l.height = nodeH;
        } else {
            int sum = 0;
            int maxR = nodeW;
            List<CraftingTreeNode> children = node.children();
            for (int i = 0; i < children.size(); i++) {
                int[] sub = new int[]{0};
                Layout childL = computeLayout(children.get(i), nodeW, nodeH, hGap, vGap, sub);
                sum += childL.height + (i > 0 ? vGap : 0);
                maxR = Math.max(maxR, nodeW + hGap + childL.maxRight);
            }
            l.height = Math.max(nodeH, sum);
            l.maxRight = maxR;
        }
        if (totalH != null) totalH[0] = Math.max(totalH[0], l.height);
        return l;
    }

    private void renderNode(GuiGraphics g, Font font, CraftingTreeNode node, int x, int y,
                             Layout layout, int nodeW, int nodeH, int hGap, int vGap, int depth) {
        int border = nodeBorderColor(node, depth);
        int bg = withAlpha(0xFF_22_22_22);
        // 绘制卡片
        g.fill(x, y, x + nodeW, y + nodeH, bg);
        g.fill(x, y, x + nodeW, y + 1, withAlpha(border));
        g.fill(x, y + nodeH - 1, x + nodeW, y + nodeH, withAlpha(border));
        g.fill(x, y, x + 1, y + nodeH, withAlpha(border));
        g.fill(x + nodeW - 1, y, x + nodeW, y + nodeH, withAlpha(border));

        String name = node.displayName() == null || node.displayName().isBlank()
                ? safeId(node) : node.displayName();
        int textPad = 4;
        String fitted = fitWidth(font, name, nodeW - textPad * 2);
        g.drawString(font, fitted, x + textPad, y + 3,
                withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);

        String idLine = safeId(node);
        if (idLine != null && !idLine.isBlank()) {
            String idFitted = fitWidth(font, idLine, nodeW - textPad * 2);
            g.drawString(font, idFitted, x + textPad, y + 13,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED), false);
        }

        String amt = "×" + node.requiredAmount();
        if (!node.isLeaf() && node.timesExecuted() > 0L) {
            amt += String.format(" (1→%d × %d)", node.perExecOutAmount(), node.timesExecuted());
        }
        String amtFitted = fitWidth(font, amt, nodeW - textPad * 2);
        g.drawString(font, amtFitted, x + textPad, y + 23,
                withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);

        // 子节点
        List<CraftingTreeNode> children = node.children();
        if (children == null || children.isEmpty()) return;

        int childX = x + nodeW + hGap;
        int totalChildH = 0;
        int[] heights = new int[children.size()];
        for (int i = 0; i < children.size(); i++) {
            int[] hb = new int[]{0};
            Layout cl = computeLayout(children.get(i), nodeW, nodeH, hGap, vGap, hb);
            heights[i] = cl.height;
            totalChildH += cl.height + (i > 0 ? vGap : 0);
        }
        int startY = y + nodeH / 2 - totalChildH / 2;

        // 父节点出口短线
        int midY = y + nodeH / 2;
        g.fill(x + nodeW, midY, x + nodeW + hGap / 2, midY + 1, withAlpha(LINE_COLOR));

        int curY = startY;
        for (int i = 0; i < children.size(); i++) {
            int childY = curY;
            int childCenterY = childY + nodeH / 2;
            // 入口短线
            g.fill(childX - hGap / 2, childCenterY, childX, childCenterY + 1, withAlpha(LINE_COLOR));
            // 垂直连线（从 mid 到 childCenter）
            int yA = Math.min(midY, childCenterY);
            int yB = Math.max(midY, childCenterY);
            g.fill(x + nodeW + hGap / 2, yA, x + nodeW + hGap / 2 + 1, yB + 1, withAlpha(LINE_COLOR));

            renderNode(g, font, children.get(i), childX, childY, layout, nodeW, nodeH, hGap, vGap, depth + 1);
            curY += heights[i] + vGap;
        }
    }

    private int nodeBorderColor(CraftingTreeNode node, int depth) {
        if (node.isMissing() || node.isLoop() || node.truncated()) {
            return VanillaTheme.COLOR_STATUS_ERROR;
        }
        if (depth == 0) return VanillaTheme.COLOR_STATUS_WARN;
        if (!node.isLeaf()) return VanillaTheme.COLOR_STATUS_OK;
        return VanillaTheme.COLOR_DIVIDER;
    }

    private static String safeId(CraftingTreeNode node) {
        if (node.itemId() == null) return "";
        int colon = node.itemId().indexOf(':');
        return colon >= 0 ? node.itemId().substring(colon + 1) : node.itemId();
    }

    private static String fitWidth(Font font, String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXAmt, double scrollYAmt) {
        if (!bodyRect().contains(mouseX, mouseY)) return false;
        if (Screen.hasControlDown()) {
            float old = zoom;
            zoom = scrollYAmt > 0 ? Math.min(2.5f, zoom * 1.1f) : Math.max(0.4f, zoom / 1.1f);
            if (Math.abs(zoom - old) > 0.001f) return true;
        }
        if (Screen.hasShiftDown()) {
            scrollX -= (int) (scrollYAmt * 16);
        } else {
            scrollY -= (int) (scrollYAmt * 16);
            scrollX -= (int) (scrollXAmt * 16);
        }
        if (scrollX < 0) scrollX = 0;
        if (scrollY < 0) scrollY = 0;
        return true;
    }
}
