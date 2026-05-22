package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.PowerExternalSelectionCache;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * G6.2 接口列表弹窗 —— 选择哪些外储分组纳入 Power 视图统计。
 *
 * <p>对齐 ModernUI {@code PowerExternalConsolePage}：以复选框列表展示
 * {@link PowerNetworkViewModel.ExternalGroup}，应用后将选中项持久化到
 * {@link PowerExternalSelectionCache}。</p>
 *
 * <p>注意：V2 当前未在主 KPI 中实时应用过滤（保持与 ModernUI 主页面一致），
 * 选择结果用于跨会话缓存与未来扩展点。</p>
 */
public class InterfaceListDialog extends BasicDialog {

    private static final int ROW_H = 16;
    private static final int CHECK_SIZE = 11;

    private final BlockPos observerPos;
    private final List<PowerNetworkViewModel.ExternalGroup> groups;
    private final LinkedHashSet<String> selected;
    private int scroll;

    public InterfaceListDialog(BlockPos observerPos) {
        super(Component.translatable("screen.resourceobserver.power.external_console.title"));
        this.observerPos = observerPos;
        this.groups = loadGroups();
        String key = PowerExternalSelectionCache.buildKey(observerPos);
        this.selected = key.isBlank()
                ? new LinkedHashSet<>()
                : PowerExternalSelectionCache.loadSelection(key);

        addFooterButton(Component.translatable("gui.cancel"), b -> requestClose());
        addFooterButton(Component.translatable("gui.ok"), b -> {
            if (!key.isBlank()) {
                PowerExternalSelectionCache.saveSelection(key, selected);
            }
            requestClose();
        });
    }

    private static List<PowerNetworkViewModel.ExternalGroup> loadGroups() {
        ObserverDataPayload payload = LatestSnapshotProvider.getPayload();
        if (payload == null) return List.of();
        try {
            PowerNetworkViewModel vm = PowerNetworkViewModelMapper.fromPayload(payload);
            PowerNetworkViewModel.DebugSnapshot debug = vm.debugSnapshot();
            if (debug == null || debug.externalGroups() == null) return List.of();
            return debug.externalGroups();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Size preferredSize() {
        return new Size(VanillaTheme.DIALOG_KPI_W, VanillaTheme.DIALOG_KPI_H);
    }

    @Override
    protected void renderBody(GuiGraphics g, Rect body, int mouseX, int mouseY, float partialTick) {
        if (body.width() <= 0 || body.height() <= 0) return;
        var font = Minecraft.getInstance().font;
        int innerLeft = body.x() + VanillaTheme.SPACING_M;
        int innerRight = body.right() - VanillaTheme.SPACING_M;
        int viewTop = body.y() + VanillaTheme.SPACING_S;
        int viewH = body.height() - VanillaTheme.SPACING_S * 2;

        // 顶部摘要行
        String summary = Component.translatable(
                "screen.resourceobserver.power.external_console.metric.selected").getString()
                + ": " + selected.size() + " / " + groups.size();
        g.drawString(font, summary, innerLeft, viewTop,
                withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);

        int listTop = viewTop + VanillaTheme.FONT_HEIGHT + 4;
        int listH = body.bottom() - VanillaTheme.SPACING_S - listTop;
        if (listH <= 0) return;

        if (groups.isEmpty()) {
            g.drawString(font,
                    Component.translatable("screen.resourceobserver.power.external_console.empty.groups").getString(),
                    innerLeft, listTop + 4,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED), false);
            return;
        }

        int contentH = groups.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        g.enableScissor(body.x(), listTop, body.right(), listTop + listH);
        int y = listTop - scroll;
        for (PowerNetworkViewModel.ExternalGroup grp : groups) {
            if (y + ROW_H >= listTop && y < listTop + listH) {
                drawRow(g, font, grp, innerLeft, innerRight, y, mouseX, mouseY);
            }
            y += ROW_H;
        }
        g.flush();
        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int trackH = listH;
            int thumbH = Math.max(8, (int) ((float) listH / contentH * trackH));
            int thumbY = listTop + (int) ((float) scroll / maxScroll * (trackH - thumbH));
            g.fill(body.right() - 3, thumbY, body.right() - 1, thumbY + thumbH,
                    withAlpha(VanillaTheme.COLOR_TEXT_DISABLED));
        }
    }

    private void drawRow(GuiGraphics g, net.minecraft.client.gui.Font font,
                          PowerNetworkViewModel.ExternalGroup grp,
                          int left, int right, int y, int mx, int my) {
        boolean checked = selected.contains(grp.extId());
        boolean hover = mx >= left && mx <= right && my >= y && my < y + ROW_H;
        if (hover) {
            g.fill(left, y, right, y + ROW_H, withAlpha(VanillaTheme.COLOR_BUTTON_BG_HOVER));
        }

        // 复选框
        int boxX = left + 2;
        int boxY = y + (ROW_H - CHECK_SIZE) / 2;
        int border = withAlpha(checked
                ? VanillaTheme.COLOR_STATUS_OK
                : VanillaTheme.COLOR_TEXT_SECONDARY);
        g.fill(boxX, boxY, boxX + CHECK_SIZE, boxY + 1, border);
        g.fill(boxX, boxY + CHECK_SIZE - 1, boxX + CHECK_SIZE, boxY + CHECK_SIZE, border);
        g.fill(boxX, boxY, boxX + 1, boxY + CHECK_SIZE, border);
        g.fill(boxX + CHECK_SIZE - 1, boxY, boxX + CHECK_SIZE, boxY + CHECK_SIZE, border);
        if (checked) {
            g.fill(boxX + 2, boxY + 2, boxX + CHECK_SIZE - 2, boxY + CHECK_SIZE - 2,
                    withAlpha(VanillaTheme.COLOR_STATUS_OK));
        }

        // 名称
        String name = grp.displayName() == null || grp.displayName().isBlank()
                ? grp.extId()
                : grp.displayName();
        int textX = boxX + CHECK_SIZE + 6;
        int textY = y + (ROW_H - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, name, textX, textY,
                withAlpha(VanillaTheme.COLOR_TEXT_PRIMARY), false);

        // 容量信息
        String cap = formatStored(grp.stored(), grp.capacity());
        int capW = font.width(cap);
        g.drawString(font, cap, right - capW - 2, textY,
                withAlpha(VanillaTheme.COLOR_TEXT_SECONDARY), false);
    }

    private static String formatStored(long stored, long capacity) {
        if (capacity <= 0) return Long.toString(stored);
        return stored + " / " + capacity;
    }

    @Override
    protected boolean onBodyMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || groups.isEmpty()) return false;
        Rect body = bodyRect();
        if (!body.contains(mouseX, mouseY)) return false;

        int innerLeft = body.x() + VanillaTheme.SPACING_M;
        int innerRight = body.right() - VanillaTheme.SPACING_M;
        int viewTop = body.y() + VanillaTheme.SPACING_S;
        int listTop = viewTop + VanillaTheme.FONT_HEIGHT + 4;
        int listH = body.bottom() - VanillaTheme.SPACING_S - listTop;
        if (listH <= 0) return false;
        if (mouseY < listTop || mouseY >= listTop + listH) return false;
        if (mouseX < innerLeft || mouseX >= innerRight) return false;

        int relY = (int) (mouseY - listTop) + scroll;
        int idx = relY / ROW_H;
        if (idx < 0 || idx >= groups.size()) return false;
        String extId = groups.get(idx).extId();
        if (!selected.remove(extId)) {
            selected.add(extId);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bodyRect().contains(mouseX, mouseY)) {
            scroll -= (int) (scrollY * 12);
            return true;
        }
        return false;
    }

    /** 暴露当前选中（测试与外部观察）。 */
    public Set<String> selectedSnapshot() {
        return new LinkedHashSet<>(selected);
    }
}
