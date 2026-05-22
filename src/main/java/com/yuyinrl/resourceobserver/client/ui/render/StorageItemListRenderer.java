package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全局库存健康表格渲染器 —— 绘制 Global Inventory Health 表格。
 * <p>
 * 列布局：图标 | Item Identity | Global Stock | Burn Rate | Estimated Buffer (时间条 + 状态点)
 * 顶部：状态图例行（Stable / Warning / Critical）+ 筛选按钮
 * 底部：紧急物品计数 + "Recalculate" 按钮
 */
public final class StorageItemListRenderer {
    private static final int ROW_HEIGHT = 16;
    private static final int HEADER_HEIGHT = 14;
    private static final int ICON_SIZE = 10;
    private static final int BUFFER_BAR_HEIGHT = 5;
    private static final int FOOTER_HEIGHT = 18;

    private StorageItemListRenderer() {
    }

    /** 计算表格总高度 */
    public static int measureHeight(List<StorageNetworkViewModel.ItemRow> items) {
        return 50 + HEADER_HEIGHT + items.size() * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    /**
     * 渲染 Global Inventory Health 表格。
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.ItemRow> items,
            boolean alertFilterActive,
            int mouseX,
            int mouseY,
            int criticalItemCount
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        // Title
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.section.inventory_health"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        // Subtitle
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.inventory_health.subtitle"),
                content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        // Filter button (right side): "All Status" / "Deficits Only"
        String filterLabel = alertFilterActive
                ? Component.translatable("screen.resourceobserver.storage.filter.all_status").getString()
                : Component.translatable("screen.resourceobserver.storage.filter.deficits_only").getString();
        int filterBtnW = Math.max(60, font.width(filterLabel) + 8);
        int filterBtnX = content.right() - filterBtnW;
        int filterBtnY = content.y();
        UiRect filterBtn = new UiRect(filterBtnX, filterBtnY, filterBtnW, 12);
        boolean filterHovered = filterBtn.contains(mouseX, mouseY);
        int filterBg = filterHovered ? 0xAA21456A : (alertFilterActive ? UiThemeTokens.TAB_ACTIVE : UiThemeTokens.TAB_INACTIVE);
        gfx.fill(filterBtn.x(), filterBtn.y(), filterBtn.right(), filterBtn.bottom(), filterBg);
        RenderUtils.drawBorder(gfx, filterBtn, UiThemeTokens.DIVIDER);
        gfx.drawString(font, filterLabel, filterBtn.x() + 4, filterBtn.y() + 2, UiThemeTokens.TEXT);

        // Status legend row
        int legendY = content.y() + 24;
        int legendX = content.x();
        // Stable dot
        gfx.fill(legendX, legendY + 2, legendX + 5, legendY + 7, UiThemeTokens.EMERALD);
        String stableLabel = Component.translatable("screen.resourceobserver.storage.legend.stable").getString();
        gfx.drawString(font, stableLabel, legendX + 7, legendY, UiThemeTokens.TEXT_MUTED);
        legendX += font.width(stableLabel) + 16;
        // Warning dot
        gfx.fill(legendX, legendY + 2, legendX + 5, legendY + 7, UiThemeTokens.AMBER);
        String warningLabel = Component.translatable("screen.resourceobserver.storage.legend.warning").getString();
        gfx.drawString(font, warningLabel, legendX + 7, legendY, UiThemeTokens.TEXT_MUTED);
        legendX += font.width(warningLabel) + 16;
        // Critical dot
        gfx.fill(legendX, legendY + 2, legendX + 5, legendY + 7, UiThemeTokens.ROSE);
        String criticalLabel = Component.translatable("screen.resourceobserver.storage.legend.critical").getString();
        gfx.drawString(font, criticalLabel, legendX + 7, legendY, UiThemeTokens.TEXT_MUTED);

        // Table area
        int tableTop = legendY + 14;
        int tableW = content.width();
        int tableH = Math.max(HEADER_HEIGHT + ROW_HEIGHT, content.height() - (tableTop - content.y()) - FOOTER_HEIGHT);
        UiRect table = new UiRect(content.x(), tableTop, tableW, tableH);
        gfx.fill(table.x(), table.y(), table.right(), table.bottom(), 0x5510182C);
        RenderUtils.drawBorder(gfx, table, UiThemeTokens.DIVIDER);

        // Column positions
        int colIconX = table.x() + 4;
        int colNameX = table.x() + 16;
        int colStockX = table.x() + (int) (table.width() * 0.32f);
        int colBurnX = table.x() + (int) (table.width() * 0.48f);
        int colBufferX = table.x() + (int) (table.width() * 0.64f);

        // Column headers
        int headerY = table.y() + 2;
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.col.identity").getString(),
                colNameX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.col.stock").getString(),
                colStockX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.col.burn_rate").getString(),
                colBurnX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.storage.col.buffer").getString(),
                colBufferX, headerY, UiThemeTokens.TEXT_MUTED);

        // Header divider
        int dividerY = headerY + HEADER_HEIGHT;
        gfx.fill(table.x() + 2, dividerY, table.right() - 2, dividerY + 1, UiThemeTokens.DIVIDER);

        // Data rows
        int y = dividerY + 2;
        List<ItemRowHitbox> rowHitboxes = new ArrayList<>();

        if (items.isEmpty()) {
            gfx.drawString(font,
                    Component.translatable("screen.resourceobserver.storage.no_items").getString(),
                    colNameX, y + 2, UiThemeTokens.TEXT_MUTED);
        } else {
            for (StorageNetworkViewModel.ItemRow item : items) {
                if (y + ROW_HEIGHT > table.bottom()) {
                    break;
                }

                UiRect rowRect = new UiRect(table.x() + 1, y, table.width() - 2, ROW_HEIGHT);
                boolean rowHovered = rowRect.contains(mouseX, mouseY);
                // 行背景着色：YELLOW/RED 行用淡色高亮，让危险物品在列表中一眼可见
                if (rowHovered) {
                    gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), 0x44214560);
                } else {
                    int rowTint = switch (item.alertLevel()) {
                        case RED    -> 0x2AFB7185; // 淡玫瑰（16% 不透明度）
                        case YELLOW -> 0x1AF59E0B; // 淡琥珀（10% 不透明度）
                        case GREEN  -> 0;
                    };
                    if (rowTint != 0) {
                        gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), rowTint);
                    }
                }

                // Item icon (resolve custom sprite, matching Overview's TableRenderer pattern)
                ResourceLocation fallbackSprite = TerminalSprites.resolve(item.iconSprite(), TerminalSprites.TABLE_ITEM);
                RenderUtils.drawItemIconOrSprite(gfx, item.itemId(), colIconX, y + 2, ICON_SIZE, fallbackSprite);

                // Item name
                int nameWidth = colStockX - colNameX - 4;
                gfx.drawString(font,
                        RenderUtils.ellipsis(font, item.displayName(), nameWidth),
                        colNameX, y + 3, UiThemeTokens.TITLE);

                // Global Stock
                gfx.drawString(font, formatCompact(item.globalAmount()),
                        colStockX, y + 3, UiThemeTokens.TEXT);

                // Burn Rate (/m)
                String burnText = item.burnRatePerMin() > 0
                        ? "-" + formatCompact(Math.round(item.burnRatePerMin())) + "/m"
                        : "0/m";
                gfx.drawString(font, burnText, colBurnX, y + 3, UiThemeTokens.TEXT);

                // Estimated Buffer: time text（颜色跟随状态，直接传达紧急程度）+ bar
                int bufferTextW = colBufferX + (int) ((table.right() - colBufferX) * 0.4f) - colBufferX;
                int bufferTextColor = bufferTextColor(item.alertLevel());
                gfx.drawString(font,
                        RenderUtils.ellipsis(font, item.estimatedBufferText(), bufferTextW),
                        colBufferX, y + 3, bufferTextColor);

                // Buffer progress bar（颜色根据 bufferRatio 连续映射，与刻度含义一致）
                // < 30% 对应危险区（红），30%~70% 对应警告区（琥珀），> 70% 对应安全区（绿）
                int barStartX = colBufferX + bufferTextW + 4;
                int barW = Math.max(16, table.right() - barStartX - 4);
                int barY = y + 6;
                int barColor = ratioBarColor(item.bufferRatio());
                RenderUtils.drawProgressBar(gfx, barStartX, barY, barW, BUFFER_BAR_HEIGHT,
                        item.bufferRatio(), 0x44334455, barColor);
                // 阈值刻度线：帮助用户理解 30s（30%处）和 5min（70%处）两条分界线
                drawThresholdTick(gfx, barStartX, barY, barW, 0.30);
                drawThresholdTick(gfx, barStartX, barY, barW, 0.70);
                // 状态点已移除（信息由文字颜色和行背景共同承载，减少视觉冗余）

                rowHitboxes.add(new ItemRowHitbox(rowRect, item.itemId()));
                y += ROW_HEIGHT;
            }
        }

        // Footer: critical item count + recalculate
        int footerY = table.bottom() + 4;
        if (criticalItemCount > 0) {
            String footerText = Component.translatable("screen.resourceobserver.storage.below_threshold", criticalItemCount).getString();
            gfx.drawString(font, footerText, content.x() + 2, footerY, UiThemeTokens.AMBER);
        }
        String recalcLabel = Component.translatable("screen.resourceobserver.storage.recalculate").getString();
        int recalcW = font.width(recalcLabel) + 8;
        gfx.drawString(font, recalcLabel, content.right() - recalcW, footerY, UiThemeTokens.CYAN);

        return new RenderResult(rowHitboxes, filterBtn);
    }

    /**
     * 渲染物品列表（兼容旧接口，无 criticalItemCount）。
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<StorageNetworkViewModel.ItemRow> items,
            boolean alertFilterActive,
            int mouseX,
            int mouseY
    ) {
        return render(gfx, font, area, items, alertFilterActive, mouseX, mouseY, 0);
    }

    /**
     * 进度条颜色 —— 根据 bufferRatio 连续映射，与三段非线性刻度的区间一致。
     * < 0.30 (危险区, 0~30s)  → ROSE
     * 0.30~0.70 (警告区, 30s~5min) → AMBER
     * > 0.70 (安全区, 5min+)   → EMERALD
     */
    private static int ratioBarColor(double ratio) {
        if (ratio < 0.30) return UiThemeTokens.ROSE;
        if (ratio < 0.70) return UiThemeTokens.AMBER;
        return UiThemeTokens.EMERALD;
    }

    /**
     * 缓冲时间文字颜色 —— 让时间数字本身传达紧急程度。
     * RED   → ROSE（醒目红色）
     * YELLOW → AMBER（警告琥珀）
     * GREEN  → TEXT_MUTED（次要色，不抢视觉焦点）
     */
    private static int bufferTextColor(StorageNetworkViewModel.AlertLevel level) {
        return switch (level) {
            case RED    -> UiThemeTokens.ROSE;
            case YELLOW -> UiThemeTokens.AMBER;
            case GREEN  -> UiThemeTokens.TEXT_MUTED;
        };
    }

    /**
     * 绘制阈值刻度线 —— 在进度条指定比例处画一条半透明白色竖线，
     * 帮助用户理解进度条的分区含义。
     */
    private static void drawThresholdTick(
            GuiGraphics gfx, int barX, int barY, int barW, double ratio
    ) {
        int tickX = barX + (int) Math.round(barW * ratio);
        // 刻度线比进度条高出 1px（上下各 1px），颜色为半透明白色
        gfx.fill(tickX, barY - 1, tickX + 1, barY + BUFFER_BAR_HEIGHT + 1, 0x66FFFFFF);
    }

    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    /** 渲染结果 */
    public record RenderResult(List<ItemRowHitbox> rowHitboxes, UiRect alertFilterButton) {
    }

    /** 物品行热区 */
    public record ItemRowHitbox(UiRect rect, String itemId) {
    }
}

