package com.yuyinrl.resourceobserver.client.screen;

import com.yuyinrl.resourceobserver.client.ui.modern.OverviewUiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ctrl+K 打开的 Overview 迁移预览页。
 * <p>
 * 本页以“完整 Overview 五大区块”为目标进行迁移验证：
 * Header / KPI / Chart / Watchlist / Table。
 */
public class OverviewConfigPreviewScreen extends Screen {

    private static final int BG = 0xCC0B1220;
    private static final int PANEL = 0xE6152336;
    private static final int PANEL_ALT = 0xD9162A3E;
    private static final int BORDER = 0xFF53E7FF;
    private static final int TEXT = 0xFFE8F7FF;
    private static final int DIM = 0xFF8FAFC2;
    private static final int ACCENT = 0xFF21D4FD;
    private static final int POSITIVE = 0xFF21D07A;
    private static final int NEGATIVE = 0xFFF16C75;

    private final Screen parent;
    private final OverviewUiConfig config;

    private final List<Hitbox> watchHitboxes = new ArrayList<>();
    private final List<Hitbox> groupHitboxes = new ArrayList<>();
    private final List<RowHitbox> rowHitboxes = new ArrayList<>();
    private Hitbox resetChartHitbox;
    private Hitbox prevPageHitbox;
    private Hitbox nextPageHitbox;

    private String selectedItemId = "";
    private int tablePage = 0;
    private final Map<String, Boolean> groupExpanded = new LinkedHashMap<>();

    public OverviewConfigPreviewScreen(Screen parent) {
        this(parent, OverviewUiConfig.DEFAULT);
    }

    public OverviewConfigPreviewScreen(Screen parent, OverviewUiConfig config) {
        super(Component.translatable("screen.resourceobserver.modern_preview.title"));
        this.parent = parent;
        this.config = config;
        groupExpanded.put("raw", true);
        groupExpanded.put("intermediate", true);
        groupExpanded.put("finished", true);
        groupExpanded.put("common", true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        watchHitboxes.clear();
        groupHitboxes.clear();
        rowHitboxes.clear();
        resetChartHitbox = null;
        prevPageHitbox = null;
        nextPageHitbox = null;

        graphics.fill(0, 0, width, height, BG);

        int panelW = Math.min(config.contentWidth(), width - 28);
        int panelH = Math.min(config.contentHeight(), height - 28);
        int x0 = (width - panelW) / 2;
        int y0 = (height - panelH) / 2;
        int x1 = x0 + panelW;
        int y1 = y0 + panelH;

        graphics.fill(x0, y0, x1, y1, PANEL);
        drawBorder(graphics, x0, y0, x1, y1, config.borderStroke(), BORDER);

        int pad = config.safeInnerPadding();
        int gap = config.sectionGap();
        int cx0 = x0 + pad;
        int cx1 = x1 - pad;
        int cy = y0 + pad;

        cy = renderHeader(graphics, cx0, cx1, cy);
        cy += gap;
        cy = renderKpiRow(graphics, cx0, cx1, cy);
        cy += gap;
        cy = renderChart(graphics, cx0, cx1, cy, mouseX, mouseY);
        cy += gap;
        cy = renderWatchlist(graphics, cx0, cx1, cy, mouseX, mouseY);
        cy += gap;
        renderTable(graphics, cx0, cx1, cy, y1 - pad, mouseX, mouseY);
    }

    private int renderHeader(GuiGraphics g, int x0, int x1, int y0) {
        int h = 56;
        int y1 = y0 + h;
        g.fill(x0, y0, x1, y1, PANEL_ALT);
        drawBorder(g, x0, y0, x1, y1, 1, 0xFF3FA8C4);
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.header.title"), x0 + 10, y0 + 10, TEXT, false);
        g.drawString(font, Component.translatable("screen.resourceobserver.modern_preview.hint"), x0 + 10, y0 + 25, DIM, false);
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.scope.global"), x1 - 70, y0 + 10, ACCENT, false);
        return y1;
    }

    private int renderKpiRow(GuiGraphics g, int x0, int x1, int y0) {
        int h = 68;
        int y1 = y0 + h;
        int gap = 8;
        int cardW = (x1 - x0 - gap * 3) / 4;
        String[] titles = {
                "screen.resourceobserver.overview.section.kpi_production",
                "screen.resourceobserver.overview.section.kpi_consumption",
                "screen.resourceobserver.overview.section.kpi_storage",
                "screen.resourceobserver.overview.section.kpi_balance"
        };
        String[] values = {"+31.4k/m", "-24.7k/m", "78%", "+6.7k/m"};
        int[] colors = {POSITIVE, NEGATIVE, ACCENT, POSITIVE};

        for (int i = 0; i < 4; i++) {
            int lx = x0 + i * (cardW + gap);
            int rx = lx + cardW;
            g.fill(lx, y0, rx, y1, PANEL_ALT);
            drawBorder(g, lx, y0, rx, y1, 1, 0xFF2F8AA8);
            g.drawString(font, Component.translatable(titles[i]), lx + 8, y0 + 8, DIM, false);
            g.drawString(font, values[i], lx + 8, y0 + 28, colors[i], false);
        }
        return y1;
    }

    private int renderChart(GuiGraphics g, int x0, int x1, int y0, int mouseX, int mouseY) {
        int h = 180;
        int y1 = y0 + h;
        g.fill(x0, y0, x1, y1, PANEL_ALT);
        drawBorder(g, x0, y0, x1, y1, 1, 0xFF2F8AA8);

        g.drawString(font, Component.translatable("screen.resourceobserver.overview.section.chart"), x0 + 8, y0 + 8, TEXT, false);
        String scope = selectedItemId.isBlank() ? Component.translatable("screen.resourceobserver.overview.chart.scope.global").getString() : selectedItemId;
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.subtitle.throughput", scope, "24H/5m"), x0 + 8, y0 + 22, DIM, false);

        int rx0 = x0 + 10;
        int rx1 = x1 - 10;
        int ry0 = y0 + 40;
        int ry1 = y1 - 12;
        g.fill(rx0, ry0, rx1, ry1, 0x66102539);

        drawWave(g, rx0, rx1, ry0, ry1, POSITIVE, 0.65f);
        drawWave(g, rx0, rx1, ry0, ry1, NEGATIVE, 0.38f);

        int bW = 108;
        int bx0 = x1 - bW - 10;
        int by0 = y0 + 8;
        int bx1 = bx0 + bW;
        int by1 = by0 + 16;
        resetChartHitbox = new Hitbox(bx0, by0, bx1, by1);
        boolean hovered = containsWithTolerance(resetChartHitbox, mouseX, mouseY);
        g.fill(bx0, by0, bx1, by1, hovered ? 0xCC1CA3CD : 0xAA197999);
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.reset"), bx0 + 4, by0 + 4, TEXT, false);
        return y1;
    }

    private int renderWatchlist(GuiGraphics g, int x0, int x1, int y0, int mouseX, int mouseY) {
        int h = 88;
        int y1 = y0 + h;
        g.fill(x0, y0, x1, y1, PANEL_ALT);
        drawBorder(g, x0, y0, x1, y1, 1, 0xFF2F8AA8);
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.section.watchlist"), x0 + 8, y0 + 8, TEXT, false);

        String[] items = {"minecraft:iron_ingot", "minecraft:redstone", "ae2:certus_quartz_crystal", "mekanism:steel_ingot"};
        int cellGap = 8;
        int cellW = (x1 - x0 - 16 - cellGap * 3) / 4;
        int cy = y0 + 26;
        for (int i = 0; i < items.length; i++) {
            int lx = x0 + 8 + i * (cellW + cellGap);
            int rx = lx + cellW;
            int by = y1 - 10;
            Hitbox hitbox = new Hitbox(lx, cy, rx, by);
            watchHitboxes.add(hitbox.withId(items[i]));
            boolean selected = items[i].equals(selectedItemId);
            boolean hovered = containsWithTolerance(hitbox, mouseX, mouseY);
            g.fill(lx, cy, rx, by, selected ? 0xCC1F6E91 : hovered ? 0xAA275067 : 0x88314558);
            g.drawString(font, trim(items[i]), lx + 4, cy + 6, TEXT, false);
            g.drawString(font, Component.translatable("screen.resourceobserver.overview.watchlist.net", i % 2 == 0 ? "+1.2k/m" : "-0.7k/m"), lx + 4, cy + 20, DIM, false);
        }
        return y1;
    }

    private void renderTable(GuiGraphics g, int x0, int x1, int y0, int y1, int mouseX, int mouseY) {
        g.fill(x0, y0, x1, y1, PANEL_ALT);
        drawBorder(g, x0, y0, x1, y1, 1, 0xFF2F8AA8);
        g.drawString(font, Component.translatable("screen.resourceobserver.overview.section.table"), x0 + 8, y0 + 8, TEXT, false);

        int contentTop = y0 + 24;
        int contentBottom = y1 - 8;
        int rowHeight = 14;
        int rowsPerPage = Math.max(1, (contentBottom - contentTop - 24) / rowHeight);

        List<RowData> allRows = buildRows();
        int maxPage = Math.max(0, (allRows.size() - 1) / rowsPerPage);
        tablePage = Mth.clamp(tablePage, 0, maxPage);
        int start = tablePage * rowsPerPage;
        int end = Math.min(allRows.size(), start + rowsPerPage);

        int y = contentTop;
        for (int i = start; i < end; i++) {
            RowData row = allRows.get(i);
            if (row.groupHeader) {
                Hitbox groupHit = new Hitbox(x0 + 8, y, x1 - 8, y + rowHeight - 1).withId(row.id);
                groupHitboxes.add(groupHit);
                boolean hovered = containsWithTolerance(groupHit, mouseX, mouseY);
                g.fill(groupHit.x0, groupHit.y0, groupHit.x1, groupHit.y1, hovered ? 0xAA2A5D78 : 0x88435A63);
                boolean expanded = groupExpanded.getOrDefault(row.id, true);
                g.drawString(font, (expanded ? "[-] " : "[+] ") + row.display, groupHit.x0 + 4, y + 3, TEXT, false);
            } else {
                RowHitbox rowHit = new RowHitbox(x0 + 8, y, x1 - 8, y + rowHeight - 1, row.id);
                rowHitboxes.add(rowHit);
                boolean selected = row.id.equals(selectedItemId);
                boolean hovered = containsWithTolerance(rowHit, mouseX, mouseY);
                g.fill(rowHit.x0, rowHit.y0, rowHit.x1, rowHit.y1, selected ? 0xAA155A74 : hovered ? 0x77273F53 : 0x55303A45);
                g.drawString(font, trim(row.display), rowHit.x0 + 4, y + 3, TEXT, false);
            }
            y += rowHeight;
        }

        int footerY0 = y1 - 20;
        int footerY1 = y1 - 6;
        int btnW = 40;
        prevPageHitbox = new Hitbox(x1 - 110, footerY0, x1 - 110 + btnW, footerY1);
        nextPageHitbox = new Hitbox(x1 - 60, footerY0, x1 - 60 + btnW, footerY1);

        boolean prevHover = containsWithTolerance(prevPageHitbox, mouseX, mouseY);
        boolean nextHover = containsWithTolerance(nextPageHitbox, mouseX, mouseY);
        g.fill(prevPageHitbox.x0, prevPageHitbox.y0, prevPageHitbox.x1, prevPageHitbox.y1, prevHover ? 0xAA1A97BF : 0x88306479);
        g.fill(nextPageHitbox.x0, nextPageHitbox.y0, nextPageHitbox.x1, nextPageHitbox.y1, nextHover ? 0xAA1A97BF : 0x88306479);
        g.drawString(font, "<", prevPageHitbox.x0 + 16, prevPageHitbox.y0 + 3, TEXT, false);
        g.drawString(font, ">", nextPageHitbox.x0 + 16, nextPageHitbox.y0 + 3, TEXT, false);
        g.drawString(font, (tablePage + 1) + "/" + (maxPage + 1), x1 - 140, footerY0 + 3, DIM, false);
    }

    private List<RowData> buildRows() {
        List<RowData> rows = new ArrayList<>();
        addGroup(rows, "raw", Component.translatable("screen.resourceobserver.overview.group.raw").getString(),
                List.of("minecraft:iron_ore", "minecraft:gold_ore", "minecraft:redstone"));
        addGroup(rows, "intermediate", Component.translatable("screen.resourceobserver.overview.group.intermediate").getString(),
                List.of("minecraft:iron_ingot", "mekanism:steel_ingot", "ae2:logic_processor"));
        addGroup(rows, "finished", Component.translatable("screen.resourceobserver.overview.group.finished").getString(),
                List.of("minecraft:piston", "minecraft:hopper", "ae2:controller"));
        addGroup(rows, "common", Component.translatable("screen.resourceobserver.overview.group.common_parts").getString(),
                List.of("minecraft:glass", "minecraft:copper_ingot", "minecraft:quartz"));
        return rows;
    }

    private void addGroup(List<RowData> rows, String key, String name, List<String> items) {
        rows.add(RowData.group(key, name));
        if (!groupExpanded.getOrDefault(key, true)) {
            return;
        }
        for (String item : items) {
            rows.add(RowData.item(item, item));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (resetChartHitbox != null && containsWithTolerance(resetChartHitbox, mouseX, mouseY)) {
            selectedItemId = "";
            return true;
        }

        for (Hitbox hitbox : watchHitboxes) {
            if (containsWithTolerance(hitbox, mouseX, mouseY)) {
                selectedItemId = hitbox.id;
                return true;
            }
        }

        for (Hitbox hitbox : groupHitboxes) {
            if (containsWithTolerance(hitbox, mouseX, mouseY)) {
                groupExpanded.put(hitbox.id, !groupExpanded.getOrDefault(hitbox.id, true));
                return true;
            }
        }

        for (RowHitbox rowHitbox : rowHitboxes) {
            if (containsWithTolerance(rowHitbox, mouseX, mouseY)) {
                selectedItemId = rowHitbox.itemId;
                return true;
            }
        }

        if (prevPageHitbox != null && containsWithTolerance(prevPageHitbox, mouseX, mouseY)) {
            tablePage = Math.max(0, tablePage - 1);
            return true;
        }
        if (nextPageHitbox != null && containsWithTolerance(nextPageHitbox, mouseX, mouseY)) {
            tablePage++;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 69) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean containsWithTolerance(Hitbox hitbox, double x, double y) {
        int slop = config.effectiveHitSlop(Minecraft.getInstance().getWindow().getGuiScale());
        return x >= hitbox.x0 - slop && x <= hitbox.x1 + slop && y >= hitbox.y0 - slop && y <= hitbox.y1 + slop;
    }

    private boolean containsWithTolerance(RowHitbox hitbox, double x, double y) {
        int slop = config.effectiveHitSlop(Minecraft.getInstance().getWindow().getGuiScale());
        return x >= hitbox.x0 - slop && x <= hitbox.x1 + slop && y >= hitbox.y0 - slop && y <= hitbox.y1 + slop;
    }

    private static void drawWave(GuiGraphics g, int x0, int x1, int y0, int y1, int color, float amplitudeScale) {
        int h = y1 - y0;
        int baseline = y0 + h / 2;
        int amp = (int) (h * 0.32f * amplitudeScale);
        for (int x = x0; x < x1; x += 4) {
            float t = (x - x0) / 22.0f;
            int y = baseline + (int) (Math.sin(t) * amp);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private String trim(String value) {
        if (font.width(value) <= 160) {
            return value;
        }
        String result = value;
        while (!result.isEmpty() && font.width(result + "…") > 160) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static void drawBorder(GuiGraphics graphics, int x0, int y0, int x1, int y1, int thickness, int color) {
        int t = Mth.clamp(thickness, 1, 4);
        graphics.fill(x0, y0, x1, y0 + t, color);
        graphics.fill(x0, y1 - t, x1, y1, color);
        graphics.fill(x0, y0, x0 + t, y1, color);
        graphics.fill(x1 - t, y0, x1, y1, color);
    }

    private record Hitbox(int x0, int y0, int x1, int y1, String id) {
        private Hitbox(int x0, int y0, int x1, int y1) {
            this(x0, y0, x1, y1, "");
        }

        private Hitbox withId(String id) {
            return new Hitbox(x0, y0, x1, y1, id);
        }
    }

    private record RowHitbox(int x0, int y0, int x1, int y1, String itemId) {
    }

    private record RowData(boolean groupHeader, String id, String display) {
        private static RowData group(String id, String display) {
            return new RowData(true, id, display);
        }

        private static RowData item(String id, String display) {
            return new RowData(false, id, display);
        }
    }
}
