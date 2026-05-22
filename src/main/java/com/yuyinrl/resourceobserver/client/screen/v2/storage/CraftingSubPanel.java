package com.yuyinrl.resourceobserver.client.screen.v2.storage;

import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.v2.crafting.CraftOrderFlow;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Column;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.ScrollView;
import com.yuyinrl.resourceobserver.client.screen.widget.TextField;
import com.yuyinrl.resourceobserver.client.ui.CraftingViewModel;
import com.yuyinrl.resourceobserver.client.ui.CraftingViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Vanilla V2 Storage 页的合成子标签。
 *
 * <p>布局严格对齐 ModernUI：顶部 CPU 汇总 KPI，下面左列为 Active Jobs，右列为搜索栏和可合成物品列表。</p>
 */
public class CraftingSubPanel extends BaseWidget {

    private static final int KPI_H = 56;
    private static final int SEARCH_H = 22;
    private static final int TITLE_H = 14;
    private static final int JOB_ROW_H = 42;
    private static final int CRAFTABLE_ROW_H = 24;
    private static final int ORDER_W = 42;
    private static final int MAX_VISIBLE_CRAFTABLES = 120;

    private final ScrollView jobsScroll = new ScrollView();
    private final ScrollView craftablesScroll = new ScrollView();
    private final TextField searchField = new TextField();

    private CraftingViewModel viewModel;
    private long providerVersion = -1L;
    private boolean dirty = true;
    private String searchQuery = "";

    public CraftingSubPanel() {
        searchField
                .setPlaceholder(Component.literal("搜索可合成物品…"))
                .setMaxLength(80)
                .setOnChanged(value -> {
                    searchQuery = value == null ? "" : value;
                    dirty = true;
                });
    }

    @Override
    protected void onBoundsChanged() {
        layoutChildren();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        refreshIfNeeded();
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        renderSummary(g, b);
        if (viewModel == null || !viewModel.hasData()) {
            renderEmpty(g, new Rect(b.x(), b.y() + KPI_H + 6, b.width(), Math.max(0, b.height() - KPI_H - 6)),
                    Component.translatable("screen.resourceobserver.crafting.empty").getString());
            return;
        }
        renderColumnShells(g);
        searchField.render(g, mouseX, mouseY, partialTick);
        jobsScroll.render(g, mouseX, mouseY, partialTick);
        craftablesScroll.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || !isMouseOver(mx, my)) return false;
        if (!searchField.isMouseOver(mx, my)) {
            searchField.setFocused(false);
        }
        if (searchField.mouseClicked(mx, my, button)) return true;
        if (jobsScroll.mouseClicked(mx, my, button)) return true;
        return craftablesScroll.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (craftablesScroll.isMouseOver(mx, my)) {
            return craftablesScroll.mouseScrolled(mx, my, sx, sy);
        }
        return jobsScroll.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return searchField.charTyped(codePoint, modifiers);
    }

    /** 标记数据需重建。 */
    public void invalidate() {
        dirty = true;
    }

    private void refreshIfNeeded() {
        LatestSnapshotProvider.State state = LatestSnapshotProvider.get();
        ObserverDataPayload payload = state.payload();
        if (payload == null) {
            viewModel = null;
            providerVersion = state.version();
            dirty = false;
            rebuildLists();
            return;
        }
        if (providerVersion != state.version()) {
            providerVersion = state.version();
            dirty = true;
        }
        if (!dirty) return;
        viewModel = CraftingViewModelMapper.fromPayload(payload, searchQuery);
        rebuildLists();
        dirty = false;
    }

    private void rebuildLists() {
        Column jobRows = new Column(VanillaTheme.SPACING_XS);
        Column craftRows = new Column(VanillaTheme.SPACING_XS);
        int jobCount = 0;
        int craftCount = 0;
        if (viewModel != null) {
            if (viewModel.jobs().isEmpty()) {
                jobRows.addFixed(new MessageRow(Component.translatable(
                        "screen.resourceobserver.crafting.jobs_empty").getString()), CRAFTABLE_ROW_H);
                jobCount++;
            } else {
                for (CraftingViewModel.JobRow job : viewModel.jobs()) {
                    jobRows.addFixed(new JobRowWidget(job), JOB_ROW_H);
                    jobCount++;
                }
            }
            List<CraftingViewModel.CraftableRow> rows = viewModel.craftables();
            if (rows.isEmpty()) {
                String key = searchQuery.isBlank()
                        ? "screen.resourceobserver.crafting.craftables_empty"
                        : "screen.resourceobserver.crafting.craftables_no_match";
                craftRows.addFixed(new MessageRow(Component.translatable(key).getString()), CRAFTABLE_ROW_H);
                craftCount++;
            } else {
                int renderCount = Math.min(rows.size(), MAX_VISIBLE_CRAFTABLES);
                for (int i = 0; i < renderCount; i++) {
                    craftRows.addFixed(new CraftableRowWidget(rows.get(i)), CRAFTABLE_ROW_H);
                    craftCount++;
                }
                if (rows.size() > renderCount) {
                    craftRows.addFixed(new TruncatedRow(rows.size() - renderCount), CRAFTABLE_ROW_H);
                    craftCount++;
                }
            }
        }
        jobsScroll.setChild(jobRows, contentHeight(jobCount, JOB_ROW_H));
        craftablesScroll.setChild(craftRows, contentHeight(craftCount, CRAFTABLE_ROW_H));
        layoutChildren();
    }

    private static int contentHeight(int rows, int rowH) {
        return rows <= 0 ? 0 : rows * rowH + (rows - 1) * VanillaTheme.SPACING_XS;
    }

    private void layoutChildren() {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        int y = b.y() + KPI_H + 6;
        int columnsH = Math.max(0, b.bottom() - y);
        int colGap = 6;
        int leftW = Math.max(80, Math.round(b.width() * 0.33f));
        int rightW = Math.max(80, b.width() - leftW - colGap);
        Rect left = new Rect(b.x(), y, leftW, columnsH);
        Rect right = new Rect(left.right() + colGap, y, rightW, columnsH);
        jobsScroll.setBounds(new Rect(left.x() + 6, left.y() + TITLE_H + 6,
                Math.max(0, left.width() - 12), Math.max(0, left.height() - TITLE_H - 12)));
        searchField.setBounds(new Rect(right.x() + Math.max(0, right.width() - 128 - 6),
                right.y() + 4, Math.min(128, Math.max(0, right.width() - 12)), SEARCH_H));
        craftablesScroll.setBounds(new Rect(right.x() + 6, right.y() + TITLE_H + SEARCH_H + 10,
                Math.max(0, right.width() - 12), Math.max(0, right.height() - TITLE_H - SEARCH_H - 16)));
    }

    private void renderSummary(GuiGraphics g, Rect b) {
        var font = Minecraft.getInstance().font;
        Rect card = new Rect(b.x(), b.y(), b.width(), KPI_H);
        NinePatch.framedFill(g, card, VanillaTheme.COLOR_PANEL, VanillaTheme.COLOR_DIVIDER);
        CraftingViewModel.StorageSummary s = viewModel == null
                ? CraftingViewModel.StorageSummary.empty()
                : viewModel.storageSummary();
        int cellW = Math.max(1, (card.width() - 20) / 4);
        renderKpiCell(g, font, card.x() + 10, card.y() + 9, cellW,
                Component.translatable("screen.resourceobserver.crafting.kpi.cpu_total").getString(),
                String.valueOf(s.cpuCount()));
        renderKpiCell(g, font, card.x() + 10 + cellW, card.y() + 9, cellW,
                Component.translatable("screen.resourceobserver.crafting.kpi.cpu_busy").getString(),
                s.busyCpuCount() + " / " + s.cpuCount());
        renderKpiCell(g, font, card.x() + 10 + cellW * 2, card.y() + 9, cellW,
                Component.translatable("screen.resourceobserver.crafting.kpi.storage_bytes").getString(),
                SnapshotFormatters.formatCompact(s.totalStorageBytes()) + " B");
        renderKpiCell(g, font, card.x() + 10 + cellW * 3, card.y() + 9, cellW,
                Component.translatable("screen.resourceobserver.crafting.kpi.coprocessors").getString(),
                String.valueOf(s.totalCoProcessors()));
    }

    private void renderKpiCell(GuiGraphics g, net.minecraft.client.gui.Font font,
                               int x, int y, int w, String label, String value) {
        g.drawString(font, fit(label, w - 4), x, y, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, fit(value, w - 4), x, y + 16, VanillaTheme.COLOR_TEXT_PRIMARY, true);
    }

    private void renderColumnShells(GuiGraphics g) {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        int y = b.y() + KPI_H + 6;
        int colGap = 6;
        int leftW = Math.max(80, Math.round(b.width() * 0.33f));
        Rect left = new Rect(b.x(), y, leftW, Math.max(0, b.bottom() - y));
        Rect right = new Rect(left.right() + colGap, y, Math.max(80, b.width() - leftW - colGap),
                Math.max(0, b.bottom() - y));
        var font = Minecraft.getInstance().font;
        NinePatch.framedFill(g, left, VanillaTheme.COLOR_PANEL, VanillaTheme.COLOR_DIVIDER);
        NinePatch.framedFill(g, right, VanillaTheme.COLOR_PANEL, VanillaTheme.COLOR_DIVIDER);
        g.drawString(font, Component.translatable("screen.resourceobserver.crafting.jobs_title").getString(),
                left.x() + 6, left.y() + 5, VanillaTheme.COLOR_TEXT_PRIMARY, false);
        String count = viewModel == null ? "" : Component.translatable(
                "screen.resourceobserver.crafting.craftables_count",
                viewModel.craftables().size(), viewModel.totalCraftables()).getString();
        g.drawString(font, count, right.x() + 6, right.y() + 5, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, Component.translatable("screen.resourceobserver.crafting.craftables_title").getString(),
                right.x() + 6, right.y() + 21, VanillaTheme.COLOR_TEXT_PRIMARY, false);
    }

    private void renderEmpty(GuiGraphics g, Rect rect, String text) {
        var font = Minecraft.getInstance().font;
        int tx = rect.x() + (rect.width() - font.width(text)) / 2;
        int ty = rect.y() + (rect.height() - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, text, tx, ty, VanillaTheme.COLOR_TEXT_SECONDARY, true);
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }

    private static ItemStack resolveStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static class JobRowWidget extends BaseWidget {
        private final CraftingViewModel.JobRow job;

        JobRowWidget(CraftingViewModel.JobRow job) {
            this.job = job;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            if (!isVisible()) return;
            Rect b = bounds();
            NinePatch.framedFill(g, b,
                    job.busy() ? VanillaTheme.COLOR_BUTTON_BG_HOVER : VanillaTheme.COLOR_PANEL,
                    VanillaTheme.COLOR_DIVIDER);
            var font = Minecraft.getInstance().font;
            int x = b.x() + 5;
            String outId = job.outputItemId();
            if (outId != null && !outId.isBlank()) {
                RenderUtils.drawItemIconOrSprite(g, outId, x, b.y() + 5, VanillaTheme.ICON_SIZE, TerminalSprites.TABLE_ITEM);
                x += VanillaTheme.ICON_SIZE + 4;
            }
            String cpu = job.cpuName() == null || job.cpuName().isBlank() ? "CPU" : job.cpuName();
            g.drawString(font, fit(cpu, Math.max(20, b.right() - x - 8)), x, b.y() + 5,
                    job.busy() ? VanillaTheme.COLOR_STATUS_INFO : VanillaTheme.COLOR_TEXT_SECONDARY, false);
            String name = job.busy() ? job.outputDisplayName() : Component.translatable(
                    "screen.resourceobserver.crafting.jobs_idle").getString();
            g.drawString(font, fit(name, Math.max(20, b.width() - 10)), b.x() + 5, b.y() + 18,
                    VanillaTheme.COLOR_TEXT_PRIMARY, false);
            if (job.busy()) {
                int pct = (int) Math.round(job.progress() * 100.0);
                String right = pct + "% · " + SnapshotFormatters.formatCompact(job.remaining());
                g.drawString(font, right, b.right() - 5 - font.width(right), b.y() + 5,
                        VanillaTheme.COLOR_TEXT_SECONDARY, false);
                int fillW = (int) Math.round((b.width() - 10) * Math.max(0.0, Math.min(1.0, job.progress())));
                g.fill(b.x() + 5, b.bottom() - 5, b.right() - 5, b.bottom() - 2, 0xFF_1E_1E_1E);
                g.fill(b.x() + 5, b.bottom() - 5, b.x() + 5 + fillW, b.bottom() - 2,
                        VanillaTheme.COLOR_STATUS_INFO);
            }
        }
    }

    private static class CraftableRowWidget extends BaseWidget {
        private final CraftingViewModel.CraftableRow row;
        private Rect orderRect = Rect.EMPTY;

        CraftableRowWidget(CraftingViewModel.CraftableRow row) {
            this.row = row;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isVisible() || button != 0 || !isMouseOver(mx, my)) return false;
            if (orderRect.contains(mx, my)) {
                String name = row.displayName() == null || row.displayName().isBlank()
                        ? row.itemId()
                        : row.displayName();
                CraftOrderFlow.start(row.itemId(), name, row.networkId());
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            if (!isVisible()) return;
            Rect b = bounds();
            boolean hovered = b.contains(mx, my);
            g.fill(b.x(), b.y(), b.right(), b.bottom(), hovered ? 0x30_FF_FF_FF : 0x18_FF_FF_FF);
            int iconX = b.x() + 4;
            if (row.itemId() != null && !row.itemId().isBlank()) {
                RenderUtils.drawItemIconOrSprite(g, row.itemId(), iconX, b.y() + 4, VanillaTheme.ICON_SIZE, TerminalSprites.TABLE_ITEM);
            }
            var font = Minecraft.getInstance().font;
            int nameX = iconX + VanillaTheme.ICON_SIZE + 4;
            orderRect = new Rect(b.right() - ORDER_W - 4, b.y() + 3, ORDER_W, b.height() - 6);
            int nameW = Math.max(20, orderRect.x() - nameX - 6);
            String name = row.displayName() == null || row.displayName().isBlank() ? row.itemId() : row.displayName();
            g.drawString(font, fit(name, nameW), nameX, b.y() + 4, VanillaTheme.COLOR_TEXT_PRIMARY, false);
            g.drawString(font, fit(row.itemId(), nameW), nameX, b.y() + 14, VanillaTheme.COLOR_TEXT_SECONDARY, false);
            NinePatch.embossedFill(g, orderRect,
                    orderRect.contains(mx, my) ? VanillaTheme.COLOR_BUTTON_BG_HOVER : VanillaTheme.COLOR_BUTTON_BG,
                    VanillaTheme.COLOR_BUTTON_HI,
                    VanillaTheme.COLOR_BUTTON_LO);
            String label = Component.translatable("screen.resourceobserver.crafting.order").getString();
            g.drawString(font, label, orderRect.x() + (orderRect.width() - font.width(label)) / 2,
                    orderRect.y() + (orderRect.height() - VanillaTheme.FONT_HEIGHT) / 2,
                    VanillaTheme.COLOR_STATUS_INFO, true);
        }
    }

    private static class TruncatedRow extends BaseWidget {
        private final int hidden;

        TruncatedRow(int hidden) {
            this.hidden = hidden;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            if (!isVisible()) return;
            String text = Component.translatable("screen.resourceobserver.crafting.craftables_truncated", hidden)
                    .getString();
            g.drawString(Minecraft.getInstance().font, text, bounds().x() + 4, bounds().y() + 7,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
        }
    }

    private static class MessageRow extends BaseWidget {
        private final String text;

        MessageRow(String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            if (!isVisible()) return;
            g.drawString(Minecraft.getInstance().font, text, bounds().x() + 4, bounds().y() + 7,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
        }
    }
}
