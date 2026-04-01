package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TableRenderer {
    private static final int ROW_HEIGHT = 12;
    private static final int GROUP_HEADER_HEIGHT = 12;

    private TableRenderer() {
    }

    public static int measureHeight(
            List<OverviewViewModel.TableGroup> groups,
            Map<String, Boolean> expandedState
    ) {
        int rows = flatten(groups, expandedState).size();
        return 62 + rows * ROW_HEIGHT;
    }

    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<OverviewViewModel.TableGroup> groups,
            Map<String, Boolean> expandedState,
            String selectedItemId,
            Set<String> multiSelectedItemIds,
            OverviewViewModel.UiState uiState,
            int mouseX,
            int mouseY,
            FilterPressState filterPressState
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(8);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.section.table"), content.x(), content.y(), UiThemeTokens.TEXT);

        int totalItems = groups.stream().mapToInt(group -> group.rows().size()).sum();
        gfx.drawString(
                font,
                Component.translatable("screen.resourceobserver.overview.table.items_active", totalItems),
                content.x(),
                content.y() + 11,
                UiThemeTokens.TEXT_MUTED
        );

        FilterControls controls = drawFilterControls(gfx, font, content, uiState, mouseX, mouseY, filterPressState);

        int tableTop = content.y() + 30;
        int tableHeight = Math.max(24, content.height() - 32);
        UiRect table = new UiRect(content.x(), tableTop, content.width(), tableHeight);
        gfx.fill(table.x(), table.y(), table.right(), table.bottom(), 0x5510182C);
        RenderUtils.drawBorder(gfx, table, UiThemeTokens.DIVIDER);

        int colStarX = table.x() + 5;
        int colNameX = table.x() + 16;
        int colProdX = table.x() + (int) (table.width() * 0.40f);
        int colConsX = table.x() + (int) (table.width() * 0.56f);
        int colNetX = table.x() + (int) (table.width() * 0.70f);
        int colInvX = table.x() + (int) (table.width() * 0.83f);

        int y = table.y() + 4;
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.table.col_node"), colNameX, y, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.table.col_production"), colProdX, y, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.table.col_consumption"), colConsX, y, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.table.col_net"), colNetX, y, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.table.col_inventory"), colInvX, y, UiThemeTokens.TEXT_MUTED);
        y += 10;
        gfx.fill(table.x() + 2, y, table.right() - 2, y + 1, UiThemeTokens.DIVIDER);
        y += 2;

        List<RowEntry> entries = flatten(groups, expandedState);
        List<GroupHitbox> groupHitboxes = new ArrayList<>();
        List<RowHitbox> rowHitboxes = new ArrayList<>();
        List<RowStarHitbox> rowStarHitboxes = new ArrayList<>();

        for (RowEntry entry : entries) {
            if (entry.group() != null) {
                UiRect hit = new UiRect(table.x() + 3, y, table.width() - 6, GROUP_HEADER_HEIGHT);
                gfx.fill(hit.x(), hit.y(), hit.right(), hit.bottom(), 0x33202A40);
                String marker = entry.groupExpanded() ? "[-] " : "[+] ";
                gfx.drawString(font, marker, hit.x() + 4, hit.y() + 2, UiThemeTokens.CYAN);
                gfx.drawString(font, entry.group().title(), hit.x() + 24, hit.y() + 2, UiThemeTokens.CYAN);
                groupHitboxes.add(new GroupHitbox(hit, entry.group().key()));
                y += GROUP_HEADER_HEIGHT;
                continue;
            }

            if (entry.row() == null) {
                continue;
            }
            OverviewViewModel.TableRow row = entry.row();
            UiRect rowRect = new UiRect(table.x() + 2, y, table.width() - 4, ROW_HEIGHT);
            boolean chartSelected = row.itemId().equals(selectedItemId);
            boolean multiSelected = multiSelectedItemIds.contains(row.itemId());
            if (chartSelected) {
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), 0x3322D3EE);
            } else if (multiSelected) {
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), 0x221F8AA8);
            }

            UiRect starRect = new UiRect(colStarX, y + 2, 8, 8);
            gfx.drawString(font, row.starred() ? "*" : "o", starRect.x(), starRect.y() - 1, row.starred() ? UiThemeTokens.AMBER : UiThemeTokens.TEXT_MUTED);

            RenderUtils.drawItemIconOrSprite(
                    gfx,
                    row.itemId(),
                    colNameX,
                    y,
                    8,
                    TerminalSprites.resolve(row.iconSprite(), TerminalSprites.TABLE_ITEM)
            );
            gfx.drawString(font, RenderUtils.ellipsis(font, row.displayName(), colProdX - colNameX - 14), colNameX + 10, y + 2, UiThemeTokens.TEXT);
            gfx.drawString(font, Long.toString(row.production()), colProdX, y + 2, UiThemeTokens.CYAN);
            gfx.drawString(font, Long.toString(row.consumption()), colConsX, y + 2, UiThemeTokens.AMBER);

            int netColor = row.net() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
            String netText = (row.net() >= 0 ? "+" : "") + row.net() + "/m";
            gfx.drawString(font, netText, colNetX, y + 2, netColor);
            if (row.critical()) {
                gfx.drawString(font, "!", colNetX - 7, y + 2, UiThemeTokens.ROSE);
            }

            double ratio = row.capacity() > 0 ? (double) row.stock() / (double) row.capacity() : 0.0;
            RenderUtils.drawProgressBar(gfx, colInvX, y + 4, 36, 3, ratio, 0xFF1E293B, UiThemeTokens.BLUE);
            rowHitboxes.add(new RowHitbox(rowRect, row.itemId(), row.groupKey()));
            rowStarHitboxes.add(new RowStarHitbox(starRect, row.itemId()));
            y += ROW_HEIGHT;
        }

        return new RenderResult(
                groupHitboxes,
                rowHitboxes,
                rowStarHitboxes,
                controls.groupButton(),
                controls.sortButton(),
                controls.statusButton(),
                controls.resetButton()
        );
    }

    private static FilterControls drawFilterControls(
            GuiGraphics gfx,
            Font font,
            UiRect content,
            OverviewViewModel.UiState uiState,
            int mouseX,
            int mouseY,
            FilterPressState filterPressState
    ) {
        int buttonH = 14;
        int buttonGap = 4;
        int buttonW = 78;
        int resetW = 44;
        int y = content.y();
        int resetX = content.right() - resetW;
        int statusX = resetX - buttonGap - buttonW;
        int sortX = statusX - buttonGap - buttonW;
        int groupX = sortX - buttonGap - buttonW;

        UiRect groupRect = new UiRect(groupX, y, buttonW, buttonH);
        UiRect sortRect = new UiRect(sortX, y, buttonW, buttonH);
        UiRect statusRect = new UiRect(statusX, y, buttonW, buttonH);
        UiRect resetRect = new UiRect(resetX, y, resetW, buttonH);

        drawFilterButton(
                gfx,
                font,
                groupRect,
                Component.translatable("screen.resourceobserver.overview.filter.group.button").getString(),
                uiState.groupNameByKey(uiState.groupFilterKey()),
                resolveButtonState(groupRect, mouseX, mouseY, filterPressState.groupPressed())
        );
        drawFilterButton(
                gfx,
                font,
                sortRect,
                Component.translatable("screen.resourceobserver.overview.filter.sort.button").getString(),
                Component.translatable(uiState.sortMode().translationKey()).getString() + (uiState.sortDesc() ? " v" : " ^"),
                resolveButtonState(sortRect, mouseX, mouseY, filterPressState.sortPressed())
        );
        drawFilterButton(
                gfx,
                font,
                statusRect,
                Component.translatable("screen.resourceobserver.overview.filter.status.button").getString(),
                Component.translatable(uiState.statusFilter().translationKey()).getString(),
                resolveButtonState(statusRect, mouseX, mouseY, filterPressState.statusPressed())
        );

        ButtonVisualState resetState = resolveButtonState(resetRect, mouseX, mouseY, filterPressState.resetPressed());
        int resetBg = switch (resetState) {
            case NORMAL -> 0x66152038;
            case HOVER -> 0x88303E5F;
            case PRESSED -> 0x992A3554;
        };
        int resetBorder = resetState == ButtonVisualState.PRESSED ? UiThemeTokens.CYAN : UiThemeTokens.DIVIDER;
        int resetText = resetState == ButtonVisualState.HOVER ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED;
        gfx.fill(resetRect.x(), resetRect.y(), resetRect.right(), resetRect.bottom(), resetBg);
        RenderUtils.drawBorder(gfx, resetRect, resetBorder);
        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.filter.reset"), resetRect.x() + 8, resetRect.y() + 3, resetText);

        return new FilterControls(
                new FilterButtonHitbox(groupRect, MenuType.GROUP),
                new FilterButtonHitbox(sortRect, MenuType.SORT),
                new FilterButtonHitbox(statusRect, MenuType.STATUS),
                new ResetHitbox(resetRect)
        );
    }

    private static void drawFilterButton(
            GuiGraphics gfx,
            Font font,
            UiRect rect,
            String titleText,
            String valueText,
            ButtonVisualState state
    ) {
        int bg = switch (state) {
            case NORMAL -> UiThemeTokens.TAB_INACTIVE;
            case HOVER -> 0xFF223652;
            case PRESSED -> 0xFF182B42;
        };
        int border = state == ButtonVisualState.PRESSED ? UiThemeTokens.CYAN : UiThemeTokens.DIVIDER;
        int textColor = state == ButtonVisualState.HOVER ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED;

        gfx.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        RenderUtils.drawBorder(gfx, rect, border);
        String text = titleText + ": " + valueText;
        gfx.drawString(font, RenderUtils.ellipsis(font, text, rect.width() - 8), rect.x() + 4, rect.y() + 3, textColor);
    }

    private static ButtonVisualState resolveButtonState(UiRect rect, int mouseX, int mouseY, boolean pressed) {
        if (pressed) {
            return ButtonVisualState.PRESSED;
        }
        if (rect.contains(mouseX, mouseY)) {
            return ButtonVisualState.HOVER;
        }
        return ButtonVisualState.NORMAL;
    }

    private static List<RowEntry> flatten(
            List<OverviewViewModel.TableGroup> groups,
            Map<String, Boolean> expandedState
    ) {
        List<RowEntry> result = new ArrayList<>();
        for (OverviewViewModel.TableGroup group : groups) {
            boolean expanded = expandedState.getOrDefault(group.key(), true);
            result.add(RowEntry.forGroup(group, expanded));
            if (expanded) {
                for (OverviewViewModel.TableRow row : group.rows()) {
                    result.add(RowEntry.forRow(row));
                }
            }
        }
        return result;
    }

    private record RowEntry(OverviewViewModel.TableGroup group, OverviewViewModel.TableRow row, boolean groupExpanded) {
        static RowEntry forGroup(OverviewViewModel.TableGroup group, boolean expanded) {
            return new RowEntry(group, null, expanded);
        }

        static RowEntry forRow(OverviewViewModel.TableRow row) {
            return new RowEntry(null, row, false);
        }
    }

    private record FilterControls(
            FilterButtonHitbox groupButton,
            FilterButtonHitbox sortButton,
            FilterButtonHitbox statusButton,
            ResetHitbox resetButton
    ) {
    }

    public enum MenuType {
        GROUP,
        SORT,
        STATUS
    }

    public enum ButtonVisualState {
        NORMAL,
        HOVER,
        PRESSED
    }

    public record FilterPressState(
            boolean groupPressed,
            boolean sortPressed,
            boolean statusPressed,
            boolean resetPressed
    ) {
        public static final FilterPressState NONE = new FilterPressState(false, false, false, false);
    }

    public record GroupHitbox(UiRect rect, String groupKey) {
    }

    public record RowHitbox(UiRect rect, String itemId, String groupKey) {
    }

    public record RowStarHitbox(UiRect rect, String itemId) {
    }

    public record FilterButtonHitbox(UiRect rect, MenuType menuType) {
    }

    public record ResetHitbox(UiRect rect) {
    }

    public record RenderResult(
            List<GroupHitbox> groupHitboxes,
            List<RowHitbox> rowHitboxes,
            List<RowStarHitbox> rowStarHitboxes,
            FilterButtonHitbox groupButtonHitbox,
            FilterButtonHitbox sortButtonHitbox,
            FilterButtonHitbox statusButtonHitbox,
            ResetHitbox resetHitbox
    ) {
    }
}
