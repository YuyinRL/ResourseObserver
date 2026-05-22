package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ButtonWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Column;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.ScrollView;
import com.yuyinrl.resourceobserver.client.screen.widget.TextField;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.service.snapshot.overview.TableOps;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Overview 物品表格区域，包含筛选工具条与滚动行列表。
 */
public class OverviewTableSection extends BaseWidget {

    private static final int TOOLBAR_H = 20;
    private static final int HEADER_H = 12;
    private static final int GROUP_H = 14;
    private static final int ROW_H = 22;

    private final TextField searchField = new TextField();
    private final ButtonWidget groupButton = new ButtonWidget(Component.literal("分组"), b -> cycleGroup());
    private final ButtonWidget sortButton = new ButtonWidget(Component.literal("排序"), b -> cycleSort());
    private final ButtonWidget statusButton = new ButtonWidget(Component.literal("状态"), b -> cycleStatus());
    private final ScrollView scrollView = new ScrollView();

    private OverviewViewModel viewModel;
    private String groupFilterKey = TableOps.GROUP_FILTER_ALL;
    private TableSortMode sortMode = TableSortMode.NET;
    private boolean sortDesc = true;
    private TableStatusFilter statusFilter = TableStatusFilter.ALL;
    private String searchQuery = "";
    private boolean initialized;
    private int rowsContentHeight = ROW_H;

    public OverviewTableSection() {
        searchField
                .setPlaceholder(Component.literal("搜索物品…"))
                .setMaxLength(80)
                .setOnChanged(value -> {
                    searchQuery = value == null ? "" : value;
                    rebuildRows();
                });
        updateButtonLabels();
    }

    public OverviewTableSection setViewModel(OverviewViewModel viewModel) {
        this.viewModel = viewModel;
        if (!initialized && viewModel != null && viewModel.uiState() != null) {
            OverviewViewModel.UiState ui = viewModel.uiState();
            groupFilterKey = ui.groupFilterKey() == null ? TableOps.GROUP_FILTER_ALL : ui.groupFilterKey();
            sortMode = ui.sortMode() == null ? TableSortMode.NET : ui.sortMode();
            sortDesc = ui.sortDesc();
            statusFilter = ui.statusFilter() == null ? TableStatusFilter.ALL : ui.statusFilter();
            initialized = true;
        }
        rebuildRows();
        return this;
    }

    @Override
    protected void onBoundsChanged() {
        layoutChildren();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        NinePatch.framedFill(g, b, 0xFF_20_20_20, VanillaTheme.COLOR_DIVIDER);
        searchField.render(g, mouseX, mouseY, partialTick);
        groupButton.render(g, mouseX, mouseY, partialTick);
        sortButton.render(g, mouseX, mouseY, partialTick);
        statusButton.render(g, mouseX, mouseY, partialTick);
        renderHeader(g);
        scrollView.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        if (!searchField.isMouseOver(mouseX, mouseY)) searchField.setFocused(false);
        if (searchField.mouseClicked(mouseX, mouseY, button)) return true;
        if (groupButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (sortButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (statusButton.mouseClicked(mouseX, mouseY, button)) return true;
        return scrollView.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollView.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return searchField.charTyped(codePoint, modifiers);
    }

    private void layoutChildren() {
        Rect b = bounds().shrink(1);
        int groupW = 62;
        int sortW = 68;
        int statusW = 58;
        int availW = Math.max(0, b.width() - VanillaTheme.SCROLLBAR_WIDTH - VanillaTheme.SPACING_S);
        int searchW = Math.max(40, availW - groupW - sortW - statusW - VanillaTheme.SPACING_S * 3);
        searchField.setBounds(new Rect(b.x(), b.y(), searchW, TOOLBAR_H));
        groupButton.setBounds(new Rect(b.x() + searchW + VanillaTheme.SPACING_S, b.y(), groupW, TOOLBAR_H));
        sortButton.setBounds(new Rect(groupButton.bounds().right() + VanillaTheme.SPACING_S, b.y(), sortW, TOOLBAR_H));
        statusButton.setBounds(new Rect(b.x() + availW - statusW, b.y(), statusW, TOOLBAR_H));
        int tableY = b.y() + TOOLBAR_H + VanillaTheme.SPACING_XS + HEADER_H;
        scrollView.setBounds(new Rect(b.x(), tableY, b.width(), Math.max(0, b.bottom() - tableY)));
    }

    public int preferredHeight() {
        return TOOLBAR_H + VanillaTheme.SPACING_XS + HEADER_H + rowsContentHeight + 2;
    }

    private void renderHeader(GuiGraphics g) {
        Rect s = scrollView.bounds();
        int y = s.y() - HEADER_H;
        g.fill(s.x(), y, s.right(), y + HEADER_H, 0xFF_26_26_26);
        var font = Minecraft.getInstance().font;
        g.drawString(font, "物品", s.x() + 26, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "生产", s.right() - 204, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "消耗", s.right() - 152, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "净值", s.right() - 100, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, "库存", s.right() - 46, y + 2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void rebuildRows() {
        List<OverviewViewModel.TableRow> rows = flattenRows();
        Predicate<OverviewViewModel.TableRow> predicate = searchPredicate();
        List<OverviewViewModel.TableRow> filtered = applyFiltersAndSort(rows, predicate);
        List<OverviewViewModel.GroupOption> options = viewModel == null || viewModel.uiState() == null
                ? List.of()
                : viewModel.uiState().groups();
        options = ensureGroupOptions(options, filtered);
        List<OverviewViewModel.TableGroup> groups = groupRows(filtered, groupFilterKey, options);
        final List<OverviewViewModel.GroupOption> menuGroups = options;

        Column column = new Column(VanillaTheme.SPACING_XS);
        int height = 0;
        for (OverviewViewModel.TableGroup group : groups) {
            column.addFixed(new GroupHeader(group.key(), group.title(), group.rows().size(), menuGroups), GROUP_H);
            height += GROUP_H;
            for (OverviewViewModel.TableRow row : group.rows()) {
                column.addFixed(new OverviewTableRow(row, menuGroups), ROW_H);
                height += ROW_H + VanillaTheme.SPACING_XS;
            }
            height += VanillaTheme.SPACING_XS;
        }
        if (height == 0) {
            column.addFixed(new EmptyRow(), ROW_H);
            height = ROW_H;
        }
        rowsContentHeight = height;
        scrollView.setChild(column, height);
        layoutChildren();
        updateButtonLabels();
    }

    private List<OverviewViewModel.TableRow> flattenRows() {
        if (viewModel == null || viewModel.tableGroups().isEmpty()) return List.of();
        List<OverviewViewModel.TableRow> rows = new ArrayList<>();
        for (OverviewViewModel.TableGroup group : viewModel.tableGroups()) {
            rows.addAll(group.rows());
        }
        return rows;
    }

    private List<OverviewViewModel.TableRow> applyFiltersAndSort(
            List<OverviewViewModel.TableRow> rows,
            Predicate<OverviewViewModel.TableRow> searchPredicate
    ) {
        List<OverviewViewModel.TableRow> filtered = new ArrayList<>();
        for (OverviewViewModel.TableRow row : rows) {
            if (!matchesStatus(row) || !matchesGroup(row)) {
                continue;
            }
            if (searchPredicate != null && !searchPredicate.test(row)) {
                continue;
            }
            filtered.add(row);
        }
        filtered.sort(comparatorFor());
        return filtered;
    }

    private boolean matchesStatus(OverviewViewModel.TableRow row) {
        return switch (statusFilter) {
            case ALL -> true;
            case SURPLUS -> row.net() > 0L;
            case DEFICIT -> row.net() < 0L;
            case CRITICAL -> row.critical();
        };
    }

    private boolean matchesGroup(OverviewViewModel.TableRow row) {
        if (groupFilterKey == null || groupFilterKey.isBlank() || TableOps.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            return true;
        }
        return groupFilterKey.equals(row.groupKey());
    }

    private Comparator<OverviewViewModel.TableRow> comparatorFor() {
        Comparator<OverviewViewModel.TableRow> comparator = switch (sortMode) {
            case NET_ABS, NET -> Comparator.comparingLong(OverviewViewModel.TableRow::net);
            case PRODUCTION -> Comparator.comparingLong(OverviewViewModel.TableRow::production);
            case CONSUMPTION -> Comparator.comparingLong(OverviewViewModel.TableRow::consumption);
            case STOCK -> Comparator.comparingLong(OverviewViewModel.TableRow::stock);
        };
        if (sortDesc) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(OverviewViewModel.TableRow::displayName);
    }

    private List<OverviewViewModel.TableGroup> groupRows(
            List<OverviewViewModel.TableRow> rows,
            String groupFilterKey,
            List<OverviewViewModel.GroupOption> groupOptions
    ) {
        LinkedHashMap<String, List<OverviewViewModel.TableRow>> grouped = new LinkedHashMap<>();
        for (OverviewViewModel.GroupOption option : groupOptions) {
            grouped.put(option.key(), new ArrayList<>());
        }
        for (OverviewViewModel.TableRow row : rows) {
            String key = TableOps.normalizeGroupKey(row.groupKey(), TableOps.GROUP_UNGROUPED);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        List<OverviewViewModel.TableGroup> result = new ArrayList<>();
        if (TableOps.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            for (OverviewViewModel.GroupOption option : groupOptions) {
                List<OverviewViewModel.TableRow> bucket = grouped.getOrDefault(option.key(), List.of());
                if (!bucket.isEmpty()) {
                    result.add(new OverviewViewModel.TableGroup(option.key(), option.displayName(), bucket));
                }
            }
            if (result.isEmpty()) {
                String title = findGroupDisplayName(groupOptions, TableOps.GROUP_UNGROUPED, TableOps.GROUP_UNGROUPED);
                result.add(new OverviewViewModel.TableGroup(TableOps.GROUP_UNGROUPED, title, List.of()));
            }
            return result;
        }

        String key = TableOps.normalizeGroupKey(groupFilterKey, TableOps.GROUP_FILTER_ALL);
        String title = findGroupDisplayName(groupOptions, key, key);
        result.add(new OverviewViewModel.TableGroup(key, title, grouped.getOrDefault(key, List.of())));
        return result;
    }

    private String findGroupDisplayName(List<OverviewViewModel.GroupOption> options, String key, String fallback) {
        for (OverviewViewModel.GroupOption option : options) {
            if (option.key().equals(key)) {
                return option.displayName();
            }
        }
        return fallback;
    }

    private List<OverviewViewModel.GroupOption> ensureGroupOptions(
            List<OverviewViewModel.GroupOption> options,
            List<OverviewViewModel.TableRow> rows
    ) {
        if (options != null && !options.isEmpty()) return options;
        List<OverviewViewModel.GroupOption> fallback = new ArrayList<>();
        fallback.add(new OverviewViewModel.GroupOption(TableOps.GROUP_FILTER_ALL, "全部", true));
        for (OverviewViewModel.TableRow row : rows) {
            String key = TableOps.normalizeGroupKey(row.groupKey(), TableOps.GROUP_UNGROUPED);
            boolean exists = false;
            for (OverviewViewModel.GroupOption option : fallback) {
                if (option.key().equals(key)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) fallback.add(new OverviewViewModel.GroupOption(key, key, false));
        }
        return fallback;
    }

    private Predicate<OverviewViewModel.TableRow> searchPredicate() {
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return null;
        return row -> contains(row.displayName(), query) || contains(row.itemId(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void cycleGroup() {
        List<OverviewViewModel.GroupOption> groups = viewModel == null || viewModel.uiState() == null
                ? List.of()
                : viewModel.uiState().groups();
        if (groups.isEmpty()) {
            groupFilterKey = TableOps.GROUP_FILTER_ALL;
            rebuildRows();
            return;
        }
        int index = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).key().equals(groupFilterKey)) {
                index = i;
                break;
            }
        }
        groupFilterKey = groups.get((index + 1) % groups.size()).key();
        rebuildRows();
    }

    private void cycleSort() {
        TableSortMode[] values = {TableSortMode.NET, TableSortMode.PRODUCTION, TableSortMode.CONSUMPTION, TableSortMode.STOCK};
        for (int i = 0; i < values.length; i++) {
            if (values[i] == sortMode) {
                if (sortDesc) {
                    sortDesc = false;
                } else {
                    sortMode = values[(i + 1) % values.length];
                    sortDesc = true;
                }
                rebuildRows();
                return;
            }
        }
        sortMode = TableSortMode.NET;
        sortDesc = true;
        rebuildRows();
    }

    private void cycleStatus() {
        TableStatusFilter[] values = TableStatusFilter.values();
        statusFilter = values[(statusFilter.ordinal() + 1) % values.length];
        rebuildRows();
    }

    private void updateButtonLabels() {
        groupButton.setLabel(Component.literal(shortGroupName()));
        sortButton.setLabel(Component.literal(sortLabel()));
        statusButton.setLabel(Component.translatable(statusFilter.translationKey()));
        statusButton.setTextColor(statusFilter == TableStatusFilter.ALL
                ? VanillaTheme.COLOR_TEXT_PRIMARY
                : VanillaTheme.COLOR_STATUS_WARN);
    }

    private String shortGroupName() {
        if (viewModel != null && viewModel.uiState() != null) {
            for (OverviewViewModel.GroupOption group : viewModel.uiState().groups()) {
                if (group.key().equals(groupFilterKey)) return fit(group.displayName(), 34);
            }
        }
        return "全部";
    }

    private String sortLabel() {
        String base = Component.translatable(sortMode.translationKey()).getString();
        return fit(base, 32) + (sortDesc ? "↓" : "↑");
    }

    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static final class GroupHeader extends BaseWidget {
        private final String key;
        private final String title;
        private final int count;
        private final List<OverviewViewModel.GroupOption> groups;

        private GroupHeader(String key, String title, int count, List<OverviewViewModel.GroupOption> groups) {
            this.key = key;
            this.title = title == null ? "" : title;
            this.count = count;
            this.groups = groups == null ? List.of() : groups;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Rect b = bounds();
            g.fill(b.x(), b.y(), b.right(), b.bottom(), 0xFF_30_30_30);
            var font = Minecraft.getInstance().font;
            String text = title + " · " + count;
            g.drawString(font, fit(text, b.width() - 8), b.x() + 4, b.y() + 3,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
            if (button == 1) {
                OverviewMenuBuilder.openGroupHeaderMenu((int) mouseX, (int) mouseY, key, title, groups);
                return true;
            }
            return false;
        }
    }

    private static final class EmptyRow extends BaseWidget {
        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Rect b = bounds();
            var font = Minecraft.getInstance().font;
            String text = "无匹配物品";
            int x = b.x() + (b.width() - font.width(text)) / 2;
            int y = b.y() + (b.height() - VanillaTheme.FONT_HEIGHT) / 2;
            g.drawString(font, text, x, y, VanillaTheme.COLOR_TEXT_DISABLED, false);
        }
    }
}
