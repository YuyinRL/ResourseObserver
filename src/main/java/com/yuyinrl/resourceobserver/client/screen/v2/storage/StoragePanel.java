package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ButtonWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Column;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.Row;
import com.yuyinrl.resourceobserver.client.screen.widget.ScrollView;
import com.yuyinrl.resourceobserver.client.screen.widget.TextField;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla V2 存储网络页，直接消费 StorageNetworkViewModel。
 */
public class StoragePanel extends BaseWidget {

    private static final int SUBTAB_H = 16;
    private static final int TOP_H = 20;
    private static final int KPI_H = 36;
    private static final int USAGE_H = 24;
    private static final int TOOLBAR_H = 20;
    private static final int HEADER_H = 12;
    private static final int ROW_H = 22;

    private enum SubTab { ITEMS, CRAFTING }

    private SubTab subTab = SubTab.ITEMS;
    private final CraftingSubPanel craftingSubPanel = new CraftingSubPanel();

    private final StorageNodeSelector nodeSelector = new StorageNodeSelector();
    private final StorageKpiCard kpiA = new StorageKpiCard();
    private final StorageKpiCard kpiB = new StorageKpiCard();
    private final StorageKpiCard kpiC = new StorageKpiCard();
    private final StorageUsageBar usageBar = new StorageUsageBar();
    private final TextField searchField = new TextField();
    private final ButtonWidget alertButton = new ButtonWidget(Component.literal("告警"), b -> toggleAlertFilter());
    private final ButtonWidget sortButton = new ButtonWidget(Component.literal("排序"), b -> cycleSortMode());
    private final ScrollView scrollView = new ScrollView();
    private final ScrollView pageScroll = new ScrollView();
    private final StorageContent pageContent = new StorageContent();

    private StorageNetworkViewModel viewModel;
    private List<StorageNetworkViewModel.ItemRow> displayItems = List.of();
    private Map<String, double[]> localBufferEma = new HashMap<>();
    private long providerVersion = -1L;
    private boolean dirty = true;

    private String selectedNodeId;
    private boolean alertFilterActive;
    private String searchQuery = "";
    private SortMode sortMode = SortMode.NAME;
    private int itemRowsContentHeight;
    private int kpiSectionH;
    private int columnsH;
    private int leftColW;
    private int rightColW;

    public StoragePanel() {
        searchField
                .setPlaceholder(Component.literal("搜索物品…"))
                .setMaxLength(80)
                .setOnChanged(value -> {
                    searchQuery = value == null ? "" : value;
                    dirty = true;
                });
        nodeSelector.setOnSelected(nodeId -> {
            selectedNodeId = nodeId;
            dirty = true;
        });
        updateButtonLabels();
    }

    @Override
    protected void onBoundsChanged() {
        layoutChildren();
    }

    @Override
    public void tick() {
        refreshSnapshotIfNeeded();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        refreshSnapshotIfNeeded();
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        renderSubTabBar(g, b);
        if (subTab == SubTab.CRAFTING) {
            craftingSubPanel.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (viewModel == null) {
            renderEmpty(g, b, "暂无数据，请先在 ModernUI 终端打开 Observer");
            return;
        }

        pageScroll.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        if (button == 0 && handleSubTabClick(mouseX, mouseY)) return true;
        if (subTab == SubTab.CRAFTING) {
            return craftingSubPanel.mouseClicked(mouseX, mouseY, button);
        }
        return pageScroll.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (subTab == SubTab.CRAFTING) {
            return craftingSubPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return pageScroll.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (subTab == SubTab.CRAFTING) {
            return craftingSubPanel.keyPressed(keyCode, scanCode, modifiers);
        }
        return searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (subTab == SubTab.CRAFTING) {
            return craftingSubPanel.charTyped(codePoint, modifiers);
        }
        return searchField.charTyped(codePoint, modifiers);
    }

    private void refreshSnapshotIfNeeded() {
        LatestSnapshotProvider.State state = LatestSnapshotProvider.get();
        ObserverDataPayload payload = state.payload();
        if (payload == null) {
            viewModel = null;
            displayItems = List.of();
            providerVersion = state.version();
            dirty = false;
            return;
        }
        if (providerVersion != state.version()) {
            providerVersion = state.version();
            localBufferEma = state.copyBufferEma();
            dirty = true;
        }
        if (!dirty) return;
        validateSelectedNode(payload);
        viewModel = StorageNetworkViewModelMapper.fromPayload(
                payload,
                selectedNodeId,
                alertFilterActive,
                localBufferEma,
                searchQuery
        );
        displayItems = sorted(viewModel.items());
        nodeSelector.setNodes(viewModel.nodes(), selectedNodeId);
        usageBar.setSegments(viewModel.usageSegments());
        applyKpis(viewModel.kpiCards());
        rebuildRows();
        updateButtonLabels();
        dirty = false;
    }

    private void validateSelectedNode(ObserverDataPayload payload) {
        if (selectedNodeId == null || selectedNodeId.isBlank()) return;
        boolean found = false;
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if (selectedNodeId.equals(binding.networkId())) {
                found = true;
                break;
            }
        }
        if (!found) selectedNodeId = null;
    }

    private List<StorageNetworkViewModel.ItemRow> sorted(List<StorageNetworkViewModel.ItemRow> source) {
        List<StorageNetworkViewModel.ItemRow> rows = new ArrayList<>(source);
        Comparator<StorageNetworkViewModel.ItemRow> comparator = switch (sortMode) {
            case AMOUNT -> Comparator.comparingLong(StorageNetworkViewModel.ItemRow::localAmount).reversed();
            case BURN_RATE -> Comparator.comparingDouble(StorageNetworkViewModel.ItemRow::burnRatePerMin).reversed();
            case BUFFER_ASC -> Comparator.comparingDouble(StorageNetworkViewModel.ItemRow::bufferRatio);
            case NAME -> Comparator.comparing(
                    StorageNetworkViewModel.ItemRow::displayName,
                    String.CASE_INSENSITIVE_ORDER);
        };
        rows.sort(comparator.thenComparing(
                StorageNetworkViewModel.ItemRow::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private void applyKpis(List<StorageNetworkViewModel.StorageKpi> kpis) {
        kpiA.setKpi(kpis.size() > 0 ? kpis.get(0) : null);
        kpiB.setKpi(kpis.size() > 1 ? kpis.get(1) : null);
        kpiC.setKpi(kpis.size() > 2 ? kpis.get(2) : null);
    }

    private java.util.Set<String> collectStarredIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        var overview = LatestSnapshotProvider.getOverviewSnapshot();
        if (overview != null && overview.tableGroups() != null) {
            for (var grp : overview.tableGroups()) {
                if (grp.rows() == null) continue;
                for (var row : grp.rows()) {
                    if (row.starred()) ids.add(row.itemId());
                }
            }
        }
        return ids;
    }

    private void rebuildRows() {
        Column rows = new Column(VanillaTheme.SPACING_XS);
        java.util.Set<String> starredIds = collectStarredIds();
        for (StorageNetworkViewModel.ItemRow item : displayItems) {
            rows.addFixed(new StorageItemRow(item, starredIds.contains(item.itemId())), ROW_H);
        }
        itemRowsContentHeight = displayItems.isEmpty()
                ? 0
                : displayItems.size() * ROW_H + (displayItems.size() - 1) * VanillaTheme.SPACING_XS;
        scrollView.setChild(rows, itemRowsContentHeight);
        layoutChildren();
    }

    private void layoutChildren() {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        int y = b.y() + SUBTAB_H + VanillaTheme.SPACING_XS;
        int contentH = Math.max(0, b.bottom() - y);
        Rect contentRect = new Rect(b.x(), y, b.width(), contentH);
        craftingSubPanel.setBounds(contentRect);
        pageScroll.setBounds(contentRect);
        kpiSectionH = clamp(Math.round(contentRect.height() * 0.15f), 54, 100);
        int colGap = 6;
        leftColW = Math.max(80, Math.round(contentRect.width() * 0.33f));
        rightColW = Math.max(80, contentRect.width() - leftColW - colGap);
        int leftH = TOP_H + VanillaTheme.SPACING_S + 80;
        int rightH = TOOLBAR_H + VanillaTheme.SPACING_XS + HEADER_H
                + Math.max(ROW_H, itemRowsContentHeight);
        columnsH = Math.max(leftH, rightH);
        int pageH = kpiSectionH + VanillaTheme.SPACING_S + columnsH;
        pageScroll.setChild(pageContent, pageH);
    }

    private void layoutPage(Rect contentRect) {
        int y = contentRect.y();
        new Row(8)
                .addFlex(kpiA, 1)
                .addFlex(kpiB, 1)
                .addFlex(kpiC, 1)
                .setBounds(new Rect(contentRect.x(), y, contentRect.width(), kpiSectionH));
        y += kpiSectionH + VanillaTheme.SPACING_S;

        int sortW = 74;
        int alertW = 58;
        int colGap = 6;
        int leftX = contentRect.x();
        int rightX = leftX + leftColW + colGap;
        int colY = y;
        nodeSelector.setBounds(new Rect(leftX + 6, colY + 20, Math.max(0, leftColW - 12), TOP_H));
        usageBar.setBounds(new Rect(leftX + 6, colY + 20 + TOP_H + VanillaTheme.SPACING_S,
                Math.max(0, leftColW - 12), 80));

        int toolbarY = colY + 6;
        int searchW = Math.max(70, rightColW - sortW - alertW - VanillaTheme.SCROLLBAR_WIDTH
                - VanillaTheme.SPACING_S * 3 - 12);
        searchField.setBounds(new Rect(rightX + 6, toolbarY, searchW, TOOLBAR_H));
        alertButton.setBounds(new Rect(searchField.bounds().right() + VanillaTheme.SPACING_S,
                toolbarY, alertW, TOOLBAR_H));
        sortButton.setBounds(new Rect(rightX + rightColW - sortW - VanillaTheme.SCROLLBAR_WIDTH - 6,
                toolbarY, sortW, TOOLBAR_H));
        int tableY = toolbarY + TOOLBAR_H + VanillaTheme.SPACING_XS + HEADER_H;
        scrollView.setBounds(new Rect(rightX + 6, tableY, Math.max(0, rightColW - 12),
                Math.max(ROW_H, itemRowsContentHeight)));
    }

    private void renderSubTabBar(GuiGraphics g, Rect b) {
        var font = Minecraft.getInstance().font;
        int half = b.width() / 2;
        Rect itemsTab = new Rect(b.x(), b.y(), half, SUBTAB_H);
        Rect craftingTab = new Rect(b.x() + half, b.y(), b.width() - half, SUBTAB_H);
        renderSubTab(g, font, itemsTab, "物品", subTab == SubTab.ITEMS);
        renderSubTab(g, font, craftingTab, "合成", subTab == SubTab.CRAFTING);
    }

    private void renderSubTab(GuiGraphics g, net.minecraft.client.gui.Font font, Rect r, String label, boolean active) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
                active ? VanillaTheme.COLOR_PANEL : VanillaTheme.COLOR_BG_DIM);
        if (active) {
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), VanillaTheme.COLOR_STATUS_INFO);
        } else {
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), VanillaTheme.COLOR_DIVIDER);
        }
        int tx = r.x() + (r.width() - font.width(label)) / 2;
        int ty = r.y() + (r.height() - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, label, tx, ty,
                active ? VanillaTheme.COLOR_TEXT_PRIMARY : VanillaTheme.COLOR_TEXT_SECONDARY,
                false);
    }

    private boolean handleSubTabClick(double mx, double my) {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        if (my < b.y() || my >= b.y() + SUBTAB_H || mx < b.x() || mx >= b.right()) return false;
        int half = b.width() / 2;
        SubTab clicked = (mx < b.x() + half) ? SubTab.ITEMS : SubTab.CRAFTING;
        if (clicked != subTab) {
            subTab = clicked;
            if (subTab == SubTab.CRAFTING) craftingSubPanel.invalidate();
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    private void renderTopBar(GuiGraphics g, Rect b) {
        var font = Minecraft.getInstance().font;
        String title = "存储网络";
        String count = viewModel.nodes().size() + " 节点 · " + viewModel.totalItemCount() + " 物品";
        g.drawString(font, title, b.x(), b.y() + 1, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        g.drawString(font, count, b.x(), b.y() + 11, VanillaTheme.COLOR_TEXT_SECONDARY, true);
    }

    private void renderTableHeader(GuiGraphics g) {
        Rect s = scrollView.bounds();
        int y = s.y() - HEADER_H;
        NinePatch.framedFill(g, new Rect(s.x(), y, s.width(), HEADER_H), 0xFF_22_22_22, VanillaTheme.COLOR_DIVIDER);
        var font = Minecraft.getInstance().font;
        int x = s.x() + 26;
        g.drawString(font, "物品", x, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "本地", s.right() - 232, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "全局", s.right() - 160, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "消耗/m", s.right() - 88, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "缓冲", s.right() - 42, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void renderEmpty(GuiGraphics g, Rect rect, String text) {
        var font = Minecraft.getInstance().font;
        int tx = rect.x() + (rect.width() - font.width(text)) / 2;
        int ty = rect.y() + (rect.height() - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, text, tx, ty, VanillaTheme.COLOR_TEXT_SECONDARY, true);
    }

    private void toggleAlertFilter() {
        alertFilterActive = !alertFilterActive;
        dirty = true;
    }

    private void cycleSortMode() {
        sortMode = sortMode.next();
        dirty = true;
    }

    private void updateButtonLabels() {
        alertButton
                .setLabel(Component.literal(alertFilterActive ? "仅告警" : "全部"))
                .setTextColor(alertFilterActive ? VanillaTheme.COLOR_STATUS_WARN : VanillaTheme.COLOR_TEXT_PRIMARY);
        sortButton.setLabel(Component.literal(sortMode.label));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private class StorageContent extends BaseWidget {
        @Override
        protected void onBoundsChanged() {
            layoutPage(bounds());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;
            Rect b = bounds();
            int y = b.y() + kpiSectionH + VanillaTheme.SPACING_S;
            int colGap = 6;
            Rect left = new Rect(b.x(), y, leftColW, columnsH);
            Rect right = new Rect(left.right() + colGap, y, rightColW, columnsH);
            NinePatch.framedFill(g, left, 0xFF_20_20_20, VanillaTheme.COLOR_DIVIDER);
            NinePatch.framedFill(g, right, 0xFF_20_20_20, VanillaTheme.COLOR_DIVIDER);
            var font = Minecraft.getInstance().font;
            g.drawString(font, "节点与用量", left.x() + 6, left.y() + 6,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
            g.drawString(font, "物品健康", right.x() + 6, right.y() + 6,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
            kpiA.render(g, mouseX, mouseY, partialTick);
            kpiB.render(g, mouseX, mouseY, partialTick);
            kpiC.render(g, mouseX, mouseY, partialTick);
            nodeSelector.render(g, mouseX, mouseY, partialTick);
            usageBar.render(g, mouseX, mouseY, partialTick);
            searchField.render(g, mouseX, mouseY, partialTick);
            alertButton.render(g, mouseX, mouseY, partialTick);
            sortButton.render(g, mouseX, mouseY, partialTick);
            renderTableHeader(g);
            if (displayItems.isEmpty()) {
                renderEmpty(g, scrollView.bounds(), "无匹配物品");
            } else {
                scrollView.render(g, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(false);
            }
            if (searchField.mouseClicked(mouseX, mouseY, button)) return true;
            if (alertButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (sortButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (nodeSelector.mouseClicked(mouseX, mouseY, button)) return true;
            return scrollView.mouseClicked(mouseX, mouseY, button);
        }
    }

    private enum SortMode {
        AMOUNT("数量"),
        BURN_RATE("消耗"),
        BUFFER_ASC("缓冲"),
        NAME("名称");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
