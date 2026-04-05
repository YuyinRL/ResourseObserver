package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生产线能耗列表渲染器 —— 绘制 Production Line Consumption 表格。
 * 列布局：Production Line | Assigned Item | Device Count | Consumption | Capacity Utilization | Status
 * 标题行含过载计数徽章，底部含电网状态行。
 */
public final class PowerDeviceListRenderer {
    private static final int ROW_HEIGHT = 16;
    private static final int HEADER_HEIGHT = 14;
    private static final int PROGRESS_BAR_HEIGHT = 4;
    private static final int FOOTER_HEIGHT = 18;

    private PowerDeviceListRenderer() {
    }

    /** 计算设备列表区域总高度 */
    public static int measureHeight(List<PowerNetworkViewModel.DeviceEntry> devices) {
        return 32 + HEADER_HEIGHT + devices.size() * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    /**
     * 渲染生产线能耗表格。
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<PowerNetworkViewModel.DeviceEntry> devices,
            int mouseX,
            int mouseY,
            int overloadCount
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(6);

        // Title
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.section.line_consumption"),
                content.x(), content.y(), UiThemeTokens.TEXT);

        // Subtitle
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.line_consumption.subtitle"),
                content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        // Overload count badge (right side of title)
        if (overloadCount > 0) {
            String badgeText = Component.translatable("screen.resourceobserver.power.overload_badge", overloadCount).getString();
            int badgeW = font.width(badgeText) + 8;
            int badgeX = content.right() - badgeW;
            int badgeY = content.y();
            gfx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, 0x44FB7185);
            gfx.drawString(font, badgeText, badgeX + 4, badgeY + 2, UiThemeTokens.ROSE);
        }

        // Table area
        int tableTop = content.y() + 24;
        int tableW = content.width();
        int tableH = Math.max(HEADER_HEIGHT + ROW_HEIGHT, content.height() - 26 - FOOTER_HEIGHT);
        UiRect table = new UiRect(content.x(), tableTop, tableW, tableH);
        gfx.fill(table.x(), table.y(), table.right(), table.bottom(), 0x5510182C);
        RenderUtils.drawBorder(gfx, table, UiThemeTokens.DIVIDER);

        // Column positions (6 columns)
        int colNameX = table.x() + 6;
        int colItemX = table.x() + (int) (table.width() * 0.25f);
        int colDevicesX = table.x() + (int) (table.width() * 0.42f);
        int colEnergyX = table.x() + (int) (table.width() * 0.55f);
        int colCapX = table.x() + (int) (table.width() * 0.70f);
        int colStatusX = table.x() + (int) (table.width() * 0.86f);

        // Column headers
        int headerY = table.y() + 2;
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.line").getString(),
                colNameX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.assigned_item").getString(),
                colItemX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.devices").getString(),
                colDevicesX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.consumption").getString(),
                colEnergyX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.cap_util").getString(),
                colCapX, headerY, UiThemeTokens.TEXT_MUTED);
        gfx.drawString(font,
                Component.translatable("screen.resourceobserver.power.col.status").getString(),
                colStatusX, headerY, UiThemeTokens.TEXT_MUTED);

        // Header divider
        int dividerY = headerY + HEADER_HEIGHT;
        gfx.fill(table.x() + 2, dividerY, table.right() - 2, dividerY + 1, UiThemeTokens.DIVIDER);

        // Data rows
        int y = dividerY + 2;
        List<DeviceRowHitbox> rowHitboxes = new ArrayList<>();

        if (devices.isEmpty()) {
            gfx.drawString(font,
                    Component.translatable("screen.resourceobserver.power.no_devices").getString(),
                    colNameX, y + 2, UiThemeTokens.TEXT_MUTED);
        } else {
            for (PowerNetworkViewModel.DeviceEntry device : devices) {
                if (y + ROW_HEIGHT > table.bottom()) {
                    break;
                }

                UiRect rowRect = new UiRect(table.x() + 1, y, table.width() - 2, ROW_HEIGHT);
                boolean rowHovered = rowRect.contains(mouseX, mouseY);
                boolean isOverload = device.alertLevel() == PowerNetworkViewModel.AlertLevel.CRITICAL;

                // Overload row background highlight
                if (isOverload) {
                    gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), 0x22FB7185);
                } else if (rowHovered) {
                    gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), 0x44214560);
                }

                // Production Line name
                int nameWidth = colItemX - colNameX - 4;
                gfx.drawString(font,
                        RenderUtils.ellipsis(font, device.displayName(), nameWidth),
                        colNameX, y + 3, UiThemeTokens.TITLE);

                // Assigned Item (derived from category for now)
                int itemWidth = colDevicesX - colItemX - 4;
                String catDisplay = categoryShortName(device.category());
                gfx.drawString(font,
                        RenderUtils.ellipsis(font, catDisplay, itemWidth),
                        colItemX, y + 3, categoryTextColor(device.category()));

                // Device Count: "X units"
                String devCountText = "—"; // No device count in current VM, show dash
                gfx.drawString(font, devCountText, colDevicesX, y + 3, UiThemeTokens.TEXT);

                // Consumption FE/t
                String energyText = formatCompact(device.energyPerTick()) + " FE/t";
                gfx.drawString(font,
                        RenderUtils.ellipsis(font, energyText, colCapX - colEnergyX - 4),
                        colEnergyX, y + 3, UiThemeTokens.TEXT);

                // Capacity Utilization (percentage + bar)
                String capText = String.format(Locale.ROOT, "%.0f%%", device.usageRatio() * 100.0);
                gfx.drawString(font, capText, colCapX, y + 3, UiThemeTokens.TEXT);
                int barX = colCapX + font.width(capText) + 4;
                int barW = Math.max(8, colStatusX - barX - 4);
                int barY = y + 5;
                int fgColor = device.usageRatio() >= 0.9 ? UiThemeTokens.ROSE
                        : (device.usageRatio() >= 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
                RenderUtils.drawProgressBar(gfx, barX, barY, barW, PROGRESS_BAR_HEIGHT,
                        device.usageRatio(), 0x44334455, fgColor);

                // Status
                int alertColor = alertColor(device.alertLevel());
                String alertText = alertText(device.alertLevel());
                gfx.drawString(font, alertText, colStatusX, y + 3, alertColor);

                rowHitboxes.add(new DeviceRowHitbox(rowRect, device.nodeId()));
                y += ROW_HEIGHT;
            }
        }

        // Footer status bar
        int footerY = table.bottom() + 4;
        String gridStatus = Component.translatable("screen.resourceobserver.power.main_grid").getString();
        gfx.drawString(font, gridStatus, content.x() + 2, footerY, UiThemeTokens.EMERALD);

        String reserves = Component.translatable("screen.resourceobserver.power.reserves").getString();
        int reservesX = content.x() + font.width(gridStatus) + 16;
        gfx.drawString(font, reserves, reservesX, footerY, UiThemeTokens.BLUE);

        String optimizeLabel = Component.translatable("screen.resourceobserver.power.optimize_load").getString();
        int optimizeW = font.width(optimizeLabel);
        gfx.drawString(font, optimizeLabel, content.right() - optimizeW, footerY, UiThemeTokens.CYAN);

        return new RenderResult(rowHitboxes);
    }

    /**
     * 渲染设备列表（兼容旧接口）。
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<PowerNetworkViewModel.DeviceEntry> devices,
            int mouseX,
            int mouseY
    ) {
        int overloadCount = 0;
        for (PowerNetworkViewModel.DeviceEntry d : devices) {
            if (d.alertLevel() != PowerNetworkViewModel.AlertLevel.NORMAL) {
                overloadCount++;
            }
        }
        return render(gfx, font, area, devices, mouseX, mouseY, overloadCount);
    }

    private static String categoryShortName(String category) {
        return switch (category == null ? "other" : category.toLowerCase(Locale.ROOT)) {
            case "mining" -> Component.translatable("screen.resourceobserver.power.category.mining").getString();
            case "assembly" -> Component.translatable("screen.resourceobserver.power.category.assembly").getString();
            case "logistics" -> Component.translatable("screen.resourceobserver.power.category.logistics").getString();
            default -> Component.translatable("screen.resourceobserver.power.category.other").getString();
        };
    }

    private static int categoryTextColor(String category) {
        return switch (category == null ? "other" : category.toLowerCase(Locale.ROOT)) {
            case "mining" -> UiThemeTokens.CYAN;
            case "assembly" -> UiThemeTokens.AMBER;
            case "logistics" -> UiThemeTokens.EMERALD;
            default -> UiThemeTokens.TEXT_MUTED;
        };
    }

    private static int alertColor(PowerNetworkViewModel.AlertLevel level) {
        return switch (level) {
            case NORMAL -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case CRITICAL -> UiThemeTokens.ROSE;
        };
    }

    private static String alertText(PowerNetworkViewModel.AlertLevel level) {
        return switch (level) {
            case NORMAL -> Component.translatable("screen.resourceobserver.power.status.normal").getString();
            case WARNING -> Component.translatable("screen.resourceobserver.power.status.warning").getString();
            case CRITICAL -> Component.translatable("screen.resourceobserver.power.status.critical").getString();
        };
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
    public record RenderResult(List<DeviceRowHitbox> rowHitboxes) {
    }

    /** 设备行热区 */
    public record DeviceRowHitbox(UiRect rect, String nodeId) {
    }
}

