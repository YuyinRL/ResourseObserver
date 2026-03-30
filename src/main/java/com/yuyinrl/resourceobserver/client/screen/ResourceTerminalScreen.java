package com.yuyinrl.resourceobserver.client.screen;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverRefreshRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resource Terminal GUI with tabbed pages and real-time refresh.
 */
public class ResourceTerminalScreen extends Screen {
    private static final int PANEL_WIDTH = 350;
    private static final int PANEL_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int MARGIN = 10;
    private static final int LINE_HEIGHT = 12;
    private static final int PAGE_SIZE = 12;
    private static final int REFRESH_INTERVAL_TICKS = 10;

    private static final int COLOR_PANEL_BG = 0xE0101020;
    private static final int COLOR_PANEL_BORDER = 0xFF4060C0;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_HEADER = 0xFFFFD700;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_DIM = 0xFF888888;
    private static final int COLOR_BOUND = 0xFF55FF55;
    private static final int COLOR_UNBOUND = 0xFFFF5555;
    private static final int COLOR_DIVIDER = 0xFF404060;
    private static final int COLOR_TAB_ACTIVE = 0xFF2A4AA0;
    private static final int COLOR_TAB_INACTIVE = 0xFF202040;
    private static final int COLOR_POSITIVE = 0xFF55FF55;
    private static final int COLOR_NEGATIVE = 0xFFFF7777;

    private enum Tab {
        OVERVIEW,
        ITEMS,
        DEBUG
    }

    private ObserverDataPayload data;
    private Tab currentTab;
    private int panelLeft;
    private int panelTop;
    private int itemPage = 0;
    private int refreshTickCounter = 0;

    private Button closeButton;
    private Button overviewTabButton;
    private Button itemsTabButton;
    private Button debugTabButton;
    private Button prevPageButton;
    private Button nextPageButton;

    public ResourceTerminalScreen(ObserverDataPayload data) {
        super(Component.translatable("screen.resourceobserver.terminal_title"));
        this.data = data;
        this.currentTab = data.debugPreferred() ? Tab.DEBUG : Tab.OVERVIEW;
    }

    public boolean matchesObserver(net.minecraft.core.BlockPos observerPos) {
        return this.data.observerPos().equals(observerPos);
    }

    public void applyPayload(ObserverDataPayload payload) {
        this.data = payload;
        clampItemPage();
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        closeButton = addRenderableWidget(Button.builder(Component.literal("X"), btn -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 22, panelTop + 4, 18, 16)
                .build());

        int tabsY = panelTop + HEADER_HEIGHT + 6;
        overviewTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.resourceobserver.tab_overview"),
                        btn -> currentTab = Tab.OVERVIEW)
                .bounds(panelLeft + MARGIN, tabsY, 90, 18)
                .build());
        itemsTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.resourceobserver.tab_items"),
                        btn -> currentTab = Tab.ITEMS)
                .bounds(panelLeft + MARGIN + 95, tabsY, 90, 18)
                .build());
        debugTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.resourceobserver.tab_debug"),
                        btn -> currentTab = Tab.DEBUG)
                .bounds(panelLeft + MARGIN + 190, tabsY, 90, 18)
                .build());

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        btn -> {
                            if (itemPage > 0) {
                                itemPage--;
                            }
                        })
                .bounds(panelLeft + PANEL_WIDTH - 88, panelTop + PANEL_HEIGHT - 24, 18, 16)
                .build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        btn -> {
                            if (itemPage < getItemPageCount() - 1) {
                                itemPage++;
                            }
                        })
                .bounds(panelLeft + PANEL_WIDTH - 44, panelTop + PANEL_HEIGHT - 24, 18, 16)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        refreshTickCounter++;
        if (refreshTickCounter >= REFRESH_INTERVAL_TICKS) {
            refreshTickCounter = 0;
            PacketDistributor.sendToServer(new ObserverRefreshRequestPayload(data.observerPos()));
        }
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int left = panelLeft;
        int top = panelTop;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        gfx.fill(left, top, right, bottom, COLOR_PANEL_BG);
        renderBorder(gfx, left, top, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL_BORDER);
        gfx.fill(left + 1, top + 1, right - 1, top + HEADER_HEIGHT, 0xFF151530);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, top + 8, COLOR_TITLE);
        gfx.fill(left + 1, top + HEADER_HEIGHT, right - 1, top + HEADER_HEIGHT + 1, COLOR_DIVIDER);

        int contentLeft = left + MARGIN;
        int contentRight = right - MARGIN;
        int y = top + HEADER_HEIGHT + 30;

        drawTabBackground(gfx, overviewTabButton, currentTab == Tab.OVERVIEW);
        drawTabBackground(gfx, itemsTabButton, currentTab == Tab.ITEMS);
        drawTabBackground(gfx, debugTabButton, currentTab == Tab.DEBUG);

        gfx.drawString(this.font,
                Component.translatable("screen.resourceobserver.observer_position",
                        data.observerPos().getX(), data.observerPos().getY(), data.observerPos().getZ()),
                contentLeft, y, COLOR_TEXT);
        y += LINE_HEIGHT;

        Component statusText = data.isBound()
                ? Component.translatable("screen.resourceobserver.status_bound")
                : Component.translatable("screen.resourceobserver.status_unbound");
        gfx.drawString(this.font, Component.translatable("screen.resourceobserver.status_label"), contentLeft, y, COLOR_TEXT);
        gfx.drawString(this.font, statusText,
                contentLeft + this.font.width(Component.translatable("screen.resourceobserver.status_label")) + 4,
                y, data.isBound() ? COLOR_BOUND : COLOR_UNBOUND);
        y += LINE_HEIGHT + 2;
        gfx.fill(contentLeft, y, contentRight, y + 1, COLOR_DIVIDER);
        y += 6;

        if (currentTab == Tab.OVERVIEW) {
            renderOverviewTab(gfx, contentLeft, contentRight, y);
        } else if (currentTab == Tab.ITEMS) {
            renderItemsTab(gfx, contentLeft, contentRight, y);
        } else {
            renderDebugTab(gfx, contentLeft, contentRight, y);
        }

        // Avoid super.render() here, because it calls renderBackground() again and
        // causes a second translucent overlay pass that blurs the panel content.
        overviewTabButton.render(gfx, mouseX, mouseY, partialTick);
        itemsTabButton.render(gfx, mouseX, mouseY, partialTick);
        debugTabButton.render(gfx, mouseX, mouseY, partialTick);
        prevPageButton.render(gfx, mouseX, mouseY, partialTick);
        nextPageButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderOverviewTab(GuiGraphics gfx, int contentLeft, int contentRight, int y) {
        gfx.drawString(this.font, Component.translatable("screen.resourceobserver.bindings_header"), contentLeft, y, COLOR_HEADER);
        y += LINE_HEIGHT + 2;

        List<ObserverDataPayload.BindingEntry> bindings = data.bindings();
        if (bindings.isEmpty()) {
            gfx.drawString(this.font, Component.translatable("screen.resourceobserver.no_bindings"), contentLeft, y, COLOR_DIM);
            return;
        }

        int visible = Math.min(bindings.size(), 10);
        for (int i = 0; i < visible; i++) {
            ObserverDataPayload.BindingEntry b = bindings.get(i);
            int color = b.networkType().contains("AE2") ? 0xFF55CCFF : 0xFFFF8844;
            String line = "#" + (i + 1) + " [" + b.networkType() + "] value=" + b.currentValue() + " +"
                    + b.totalProduced() + " -" + b.totalConsumed();
            gfx.drawString(this.font, truncate(line, contentRight - contentLeft), contentLeft, y, color);
            y += LINE_HEIGHT;
        }
        if (bindings.size() > visible) {
            gfx.drawString(this.font,
                    Component.translatable("screen.resourceobserver.more_bindings", bindings.size() - visible),
                    contentLeft, y, COLOR_DIM);
        }
    }

    private void renderItemsTab(GuiGraphics gfx, int contentLeft, int contentRight, int y) {
        List<ItemRow> rows = buildItemRows();
        if (rows.isEmpty()) {
            gfx.drawString(this.font, Component.translatable("screen.resourceobserver.items_no_data"), contentLeft, y, COLOR_DIM);
            return;
        }

        int start = itemPage * PAGE_SIZE;
        int end = Math.min(rows.size(), start + PAGE_SIZE);

        gfx.drawString(this.font, Component.translatable("screen.resourceobserver.items_header"), contentLeft, y, COLOR_HEADER);
        y += LINE_HEIGHT + 2;
        gfx.drawString(this.font, "Item ID", contentLeft, y, COLOR_TEXT);
        gfx.drawString(this.font, "Amount", contentLeft + 180, y, COLOR_TEXT);
        gfx.drawString(this.font, "Delta", contentLeft + 250, y, COLOR_TEXT);
        y += LINE_HEIGHT;
        gfx.fill(contentLeft, y - 1, contentRight, y, COLOR_DIVIDER);

        for (int i = start; i < end; i++) {
            ItemRow row = rows.get(i);
            String id = truncate(row.itemId(), 170);
            gfx.drawString(this.font, id, contentLeft, y, COLOR_TEXT);
            gfx.drawString(this.font, String.valueOf(row.amount()), contentLeft + 180, y, COLOR_TEXT);
            long delta = row.delta();
            int deltaColor = delta > 0 ? COLOR_POSITIVE : (delta < 0 ? COLOR_NEGATIVE : COLOR_DIM);
            String deltaText = (delta > 0 ? "+" : "") + delta;
            gfx.drawString(this.font, deltaText, contentLeft + 250, y, deltaColor);
            y += LINE_HEIGHT;
        }
    }

    private void renderDebugTab(GuiGraphics gfx, int contentLeft, int contentRight, int y) {
        gfx.drawString(this.font, Component.translatable("screen.resourceobserver.debug_header"), contentLeft, y, COLOR_HEADER);
        y += LINE_HEIGHT + 2;

        List<ObserverDataPayload.BindingEntry> bindings = data.bindings();
        if (bindings.isEmpty()) {
            gfx.drawString(this.font, Component.translatable("screen.resourceobserver.no_bindings"), contentLeft, y, COLOR_DIM);
            return;
        }

        int maxLines = 14;
        int used = 0;
        for (int i = 0; i < bindings.size() && used < maxLines; i++) {
            ObserverDataPayload.BindingEntry b = bindings.get(i);
            gfx.drawString(this.font, "#" + (i + 1) + " " + b.networkType(), contentLeft, y, COLOR_TEXT);
            y += LINE_HEIGHT;
            used++;
            if (used >= maxLines) {
                break;
            }
            gfx.drawString(this.font, truncate(b.debugInfo(), contentRight - contentLeft), contentLeft + 8, y, COLOR_DIM);
            y += LINE_HEIGHT;
            used++;
        }
    }

    private void updateButtonStates() {
        overviewTabButton.active = currentTab != Tab.OVERVIEW;
        itemsTabButton.active = currentTab != Tab.ITEMS;
        debugTabButton.active = currentTab != Tab.DEBUG;

        int pageCount = getItemPageCount();
        prevPageButton.visible = currentTab == Tab.ITEMS;
        nextPageButton.visible = currentTab == Tab.ITEMS;
        prevPageButton.active = currentTab == Tab.ITEMS && itemPage > 0;
        nextPageButton.active = currentTab == Tab.ITEMS && itemPage < pageCount - 1;
    }

    private int getItemPageCount() {
        int size = buildItemRows().size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void clampItemPage() {
        itemPage = Math.max(0, Math.min(itemPage, getItemPageCount() - 1));
    }

    private List<ItemRow> buildItemRows() {
        List<ItemRow> rows = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : data.bindings()) {
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                rows.add(new ItemRow(item.itemId(), item.amount(), item.delta()));
            }
        }
        rows.sort(Comparator.comparing(ItemRow::itemId));
        return rows;
    }

    private void drawTabBackground(GuiGraphics gfx, Button button, boolean active) {
        int color = active ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE;
        gfx.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), color);
    }

    private void renderBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String truncate(String text, int maxPixelWidth) {
        if (this.font.width(text) <= maxPixelWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (this.font.width(sb.toString()) + ellipsisWidth > maxPixelWidth) {
                return sb.substring(0, sb.length() - 1) + ellipsis;
            }
        }
        return text;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ItemRow(String itemId, long amount, long delta) {
    }
}
