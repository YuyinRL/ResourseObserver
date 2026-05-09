package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * G2.4-B 合成计划审核弹窗。
 *
 * <p>展示：状态行 + 最终产物 + 滚动 used / missing / emitted 列表。
 * 用户操作：Confirm（提交 planId） / Cancel（取消计划）。</p>
 *
 * <p>简化版，未实现 CPU 选择器与合成树跳转（属 G6 范畴）。</p>
 */
public class CraftPlanReviewDialog extends BasicDialog {

    /** 用户操作：CONFIRM / CANCEL / NONE（关闭未操作）。 */
    public enum Action { CONFIRM, CANCEL, NONE }

    private final CraftingPlanResultPayload plan;
    private final Consumer<Action> onAction;
    private Action pending = Action.NONE;
    private int scroll;

    public CraftPlanReviewDialog(Component title, CraftingPlanResultPayload plan, Consumer<Action> onAction) {
        super(title);
        this.plan = plan;
        this.onAction = onAction;

        boolean canConfirm = plan != null && plan.planId() != null && !plan.planId().isBlank()
                && plan.missingItems() != null && plan.missingItems().isEmpty();

        addFooterButton(Component.translatable("gui.cancel"), b -> { pending = Action.CANCEL; requestClose(); });
        if (plan != null && plan.treeRoot() != null) {
            addFooterButton(Component.translatable("screen.resourceobserver.crafting.tree_view_btn"), b -> {
                DialogHost host = host();
                if (host != null) {
                    Component treeTitle = Component.translatable(
                            "screen.resourceobserver.crafting.tree_title",
                            plan.finalOutputDisplayName() == null ? "" : plan.finalOutputDisplayName(),
                            plan.finalOutputAmount());
                    host.open(new CraftingTreeDialog(treeTitle, plan.treeRoot()));
                }
            });
        }
        var confirm = addFooterButton(Component.translatable("gui.ok"),
                b -> { pending = Action.CONFIRM; requestClose(); });
        confirm.setEnabled(canConfirm);
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_REVIEW_W, 320);
    }

    @Override
    protected void onClose() {
        if (onAction != null) onAction.accept(pending);
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int padding = VanillaTheme.SPACING_S;
        int x = body.x() + padding;
        int y = body.y() + padding;
        int innerW = body.width() - padding * 2;

        if (plan == null) {
            g.drawString(font, Component.literal("No plan."),
                    x, y, withAlpha(VanillaTheme.COLOR_TEXT_DISABLED), false);
            return;
        }

        // 状态 + 产物
        String status = plan.status() == null ? "?" : plan.status();
        int statusColor = "OK".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status)
                ? VanillaTheme.COLOR_STATUS_OK
                : ("MISSING".equalsIgnoreCase(status) ? VanillaTheme.COLOR_STATUS_WARN
                : VanillaTheme.COLOR_TEXT_PRIMARY);
        g.drawString(font, "Status: " + status, x, y, withAlpha(statusColor), false);
        y += VanillaTheme.FONT_HEIGHT + 2;

        if (plan.message() != null && !plan.message().isBlank()) {
            for (var line : font.split(Component.literal(plan.message()), innerW)) {
                g.drawString(font, line, x, y, withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
                y += VanillaTheme.FONT_HEIGHT + 1;
            }
            y += 2;
        }

        if (plan.finalOutputDisplayName() != null && !plan.finalOutputDisplayName().isBlank()) {
            String prod = String.format("%s × %,d",
                    plan.finalOutputDisplayName(), plan.finalOutputAmount());
            g.drawString(font, prod, x, y, withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);
            y += VanillaTheme.FONT_HEIGHT + 4;
        }
        if (plan.bytes() > 0L) {
            g.drawString(font, "Bytes: " + plan.bytes(), x, y,
                    withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
            y += VanillaTheme.FONT_HEIGHT + 2;
        }

        // 分隔线
        g.fill(x, y + 1, body.right() - padding, y + 2,
                withAlpha(VanillaTheme.COLOR_DIVIDER));
        y += 4;

        // 滚动列表区
        int listTop = y;
        int listBottom = body.bottom() - padding;
        if (listBottom <= listTop) return;

        g.enableScissor(body.x(), listTop, body.right(), listBottom);
        int curY = listTop - scroll;
        curY = drawSection(g, font, "Used", plan.usedItems(),
                VanillaTheme.COLOR_STATUS_OK, x, curY, innerW);
        curY = drawSection(g, font, "Missing", plan.missingItems(),
                VanillaTheme.COLOR_STATUS_ERROR, x, curY, innerW);
        curY = drawSection(g, font, "Emitted", plan.emittedItems(),
                VanillaTheme.COLOR_STATUS_INFO, x, curY, innerW);
        g.flush();
        g.disableScissor();

        // 计算最大滚动
        int contentH = curY - (listTop - scroll) - listTop + listTop;
        // 简化：就是 totalRendered = curY+scroll-listTop
        int totalRendered = (curY + scroll) - listTop;
        int viewH = listBottom - listTop;
        int maxScroll = Math.max(0, totalRendered - viewH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
        if (maxScroll > 0) {
            int trackH = viewH;
            int thumbH = Math.max(8, (int) ((float) viewH / totalRendered * trackH));
            int thumbY = listTop + (int) ((float) scroll / maxScroll * (trackH - thumbH));
            g.fill(body.right() - 3, thumbY, body.right() - 1, thumbY + thumbH,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }
    }

    /** 渲染一个分组（标题 + 三连扁平列表 [id, name, amount]）。 */
    private int drawSection(GuiGraphics g, Font font, String header, List<String> tripleList,
                            int titleColor, int x, int y, int innerW) {
        if (tripleList == null || tripleList.size() < 3) return y;
        g.drawString(font, header + " (" + (tripleList.size() / 3) + ")",
                x, y, withAlpha(titleColor), false);
        y += VanillaTheme.FONT_HEIGHT + 2;
        for (int i = 0; i + 2 < tripleList.size(); i += 3) {
            String name = tripleList.get(i + 1);
            String amount = tripleList.get(i + 2);
            String safeName = name == null || name.isEmpty() ? tripleList.get(i) : name;
            String row = "  " + safeName;
            if (amount != null && !amount.isEmpty()) row += " ×" + amount;
            int rowW = innerW - font.width(amount == null ? "" : amount) - 4;
            String trimmed = font.width(row) <= innerW ? row
                    : font.plainSubstrByWidth(row, Math.max(0, rowW - font.width("…"))) + "…";
            g.drawString(font, trimmed, x, y, withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);
            y += VanillaTheme.FONT_HEIGHT + 1;
        }
        return y + 4;
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
