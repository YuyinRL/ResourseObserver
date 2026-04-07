package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * External Storage Console renderer on power page.
 */
public final class PowerDebugPanelRenderer {
    private PowerDebugPanelRenderer() {
    }

    private static final int PAD = 6;
    private static final int TITLE_H = 12;
    private static final int LINE_H = 10;
    private static final int SEP_H = 6;
    private static final int ROW_H = 11;

    private static final int PANEL_BG = 0xCC0A1324;
    private static final int PANEL_BORDER = 0xFF284364;
    private static final int ROW_SELECTED_BG = 0x3A1F4E82;
    private static final int ROW_SELECTED_BORDER = 0xFF55D4FF;
    private static final int ROW_HOVER_BG = 0x2A244563;

    public record DeviceRowHitbox(UiRect rect, String interfaceKey, List<String> relatedExternalGroupIds) {
    }

    public record RenderResult(List<DeviceRowHitbox> deviceRowHitboxes) {
    }

    public static int measureHeight(
            PowerNetworkViewModel.DebugSnapshot snapshot,
            Set<String> selectedExternalGroupIds
    ) {
        PowerNetworkViewModel.DebugSnapshot safe = snapshot == null
                ? PowerNetworkViewModel.DebugSnapshot.empty()
                : snapshot;
        Set<String> selected = selectedExternalGroupIds == null ? Set.of() : selectedExternalGroupIds;

        int h = PAD * 2;
        h += TITLE_H;
        h += LINE_H * 2;
        h += SEP_H;

        int interfaceCount = safe.inputDevices().size() + safe.outputDevices().size();
        h += LINE_H;
        h += Math.max(1, interfaceCount) * ROW_H;

        h += SEP_H;
        h += LINE_H;

        List<PowerNetworkViewModel.ExternalGroup> selectedGroups = selectedGroups(safe, selected);
        if (selectedGroups.isEmpty()) {
            h += LINE_H;
        } else {
            h += LINE_H;
            h += selectedGroups.size() * ROW_H;
        }

        return h;
    }

    public static int measureHeight(PowerNetworkViewModel.DebugSnapshot snapshot) {
        return measureHeight(snapshot, Set.of());
    }

    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            PowerNetworkViewModel.DebugSnapshot snapshot,
            Set<String> selectedExternalGroupIds,
            int mouseX,
            int mouseY
    ) {
        PowerNetworkViewModel.DebugSnapshot safe = snapshot == null
                ? PowerNetworkViewModel.DebugSnapshot.empty()
                : snapshot;
        Set<String> selected = selectedExternalGroupIds == null ? Set.of() : selectedExternalGroupIds;

        List<DeviceRowHitbox> hitboxes = new ArrayList<>();

        RenderUtils.fillPanel(gfx, area, PANEL_BG, PANEL_BORDER);

        int x = area.x() + PAD;
        int y = area.y() + PAD;
        int maxW = Math.max(40, area.width() - PAD * 2);

        String title = Component.translatable("screen.resourceobserver.power.external_console.title").getString();
        gfx.drawString(font, RenderUtils.ellipsis(font, title, maxW), x, y, UiThemeTokens.CYAN);
        y += TITLE_H;

        String metrics1 = String.format(
                Locale.ROOT,
                "%s %s  %s %s  %s %s",
                Component.translatable("screen.resourceobserver.power.external_console.metric.input").getString(),
                formatFEt(safe.inputRate()),
                Component.translatable("screen.resourceobserver.power.external_console.metric.output").getString(),
                formatFEt(safe.outputRate()),
                Component.translatable("screen.resourceobserver.power.external_console.metric.buffer").getString(),
                formatFE(safe.totalBufferCapacity())
        );
        gfx.drawString(font, RenderUtils.ellipsis(font, metrics1, maxW), x, y, UiThemeTokens.TEXT_MUTED);
        y += LINE_H;

        String metrics2 = String.format(
                Locale.ROOT,
                "P:%d  Pt:%d  S:%d  C:%d  %s:%d",
                safe.plugCount(),
                safe.pointCount(),
                safe.storageCount(),
                safe.controllerCount(),
                Component.translatable("screen.resourceobserver.power.external_console.metric.selected").getString(),
                selected.size()
        );
        gfx.drawString(font, RenderUtils.ellipsis(font, metrics2, maxW), x, y, UiThemeTokens.TEXT_MUTED);
        y += LINE_H;

        y = drawSeparator(gfx, x, y, maxW);

        gfx.drawString(
                font,
                Component.translatable("screen.resourceobserver.power.external_console.section.interfaces").getString(),
                x,
                y,
                UiThemeTokens.TEXT
        );
        y += LINE_H;

        List<PowerNetworkViewModel.DeviceDebugEntry> interfaces = new ArrayList<>(safe.inputDevices().size() + safe.outputDevices().size());
        interfaces.addAll(safe.inputDevices());
        interfaces.addAll(safe.outputDevices());
        if (interfaces.isEmpty()) {
            gfx.drawString(
                    font,
                    Component.translatable("screen.resourceobserver.power.external_console.empty.interfaces").getString(),
                    x,
                    y,
                    UiThemeTokens.TEXT_MUTED
            );
            y += ROW_H;
        } else {
            for (PowerNetworkViewModel.DeviceDebugEntry entry : interfaces) {
                List<String> extIds = collectExtIds(entry);
                boolean rowSelected = intersects(selected, extIds);
                UiRect rowRect = new UiRect(x - 2, y - 1, maxW + 4, ROW_H);
                if (rowSelected) {
                    gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), ROW_SELECTED_BG);
                    RenderUtils.drawBorder(gfx, rowRect, ROW_SELECTED_BORDER);
                } else if (rowRect.contains(mouseX, mouseY)) {
                    gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), ROW_HOVER_BG);
                }

                String dirLabel = "plug".equals(entry.deviceType()) ? "IN" : "OUT";
                int dirColor = "plug".equals(entry.deviceType()) ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
                gfx.drawString(font, dirLabel, x, y, dirColor);

                String rightText = formatFEt(Math.abs(entry.transferRate())) + "  E:" + extIds.size();
                if (rowSelected) {
                    rightText += "  [SELECTED]";
                }
                int rightX = x + maxW - font.width(rightText);
                gfx.drawString(font, rightText, rightX, y, rowSelected ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED);

                int nameX = x + 20;
                int nameW = Math.max(24, rightX - nameX - 4);
                gfx.drawString(
                        font,
                        RenderUtils.ellipsis(font, entry.deviceName(), nameW),
                        nameX,
                        y,
                        rowSelected ? UiThemeTokens.TEXT : UiThemeTokens.TITLE
                );

                hitboxes.add(new DeviceRowHitbox(rowRect, entry.interfaceKey(), List.copyOf(extIds)));
                y += ROW_H;
            }
        }

        y = drawSeparator(gfx, x, y, maxW);

        gfx.drawString(
                font,
                Component.translatable("screen.resourceobserver.power.external_console.section.external").getString(),
                x,
                y,
                UiThemeTokens.TEXT
        );
        y += LINE_H;

        List<PowerNetworkViewModel.ExternalGroup> selectedGroups = selectedGroups(safe, selected);
        if (selectedGroups.isEmpty()) {
            gfx.drawString(
                    font,
                    Component.translatable("screen.resourceobserver.power.external_console.empty.selected").getString(),
                    x,
                    y,
                    UiThemeTokens.TEXT_MUTED
            );
        } else {
            String colHeader = Component.translatable("screen.resourceobserver.power.external_console.table.header").getString();
            gfx.drawString(font, RenderUtils.ellipsis(font, colHeader, maxW), x, y, UiThemeTokens.TEXT_MUTED);
            y += LINE_H;

            for (PowerNetworkViewModel.ExternalGroup group : selectedGroups) {
                String rightText = String.format(
                        Locale.ROOT,
                        "%s  %s  %d",
                        formatFE(group.stored()) + "/" + formatFE(group.capacity()),
                        formatPercent(group.stored(), group.capacity()),
                        group.interfaceKeys().size()
                );
                int rightX = x + maxW - font.width(rightText);
                gfx.drawString(font, rightText, rightX, y, UiThemeTokens.TEXT_MUTED);

                int nameW = Math.max(24, rightX - x - 4);
                gfx.drawString(font, RenderUtils.ellipsis(font, group.displayName(), nameW), x, y, UiThemeTokens.TEXT);
                y += ROW_H;
            }
        }

        return new RenderResult(List.copyOf(hitboxes));
    }

    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            PowerNetworkViewModel.DebugSnapshot snapshot
    ) {
        render(gfx, font, area, snapshot, Set.of(), Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private static int drawSeparator(GuiGraphics gfx, int x, int y, int maxW) {
        gfx.fill(x, y + 1, x + maxW, y + 2, UiThemeTokens.DIVIDER);
        return y + SEP_H;
    }

    private static List<String> collectExtIds(PowerNetworkViewModel.DeviceDebugEntry entry) {
        if (entry == null || entry.externalRefs() == null || entry.externalRefs().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> extIds = new LinkedHashSet<>();
        for (PowerNetworkViewModel.ExternalRef ref : entry.externalRefs()) {
            if (ref.extId() != null && !ref.extId().isBlank()) {
                extIds.add(ref.extId());
            }
        }
        return List.copyOf(extIds);
    }

    private static boolean intersects(Set<String> selected, List<String> extIds) {
        if (selected == null || selected.isEmpty() || extIds == null || extIds.isEmpty()) {
            return false;
        }
        for (String extId : extIds) {
            if (selected.contains(extId)) {
                return true;
            }
        }
        return false;
    }

    private static List<PowerNetworkViewModel.ExternalGroup> selectedGroups(
            PowerNetworkViewModel.DebugSnapshot snapshot,
            Set<String> selectedExternalGroupIds
    ) {
        if (snapshot == null || snapshot.externalGroups().isEmpty() || selectedExternalGroupIds == null || selectedExternalGroupIds.isEmpty()) {
            return List.of();
        }
        List<PowerNetworkViewModel.ExternalGroup> selectedGroups = new ArrayList<>();
        for (PowerNetworkViewModel.ExternalGroup group : snapshot.externalGroups()) {
            if (selectedExternalGroupIds.contains(group.extId())) {
                selectedGroups.add(group);
            }
        }
        selectedGroups.sort(Comparator
                .comparingLong(PowerNetworkViewModel.ExternalGroup::capacity)
                .reversed()
                .thenComparing(PowerNetworkViewModel.ExternalGroup::displayName));
        return selectedGroups;
    }

    private static String formatPercent(long stored, long capacity) {
        double ratio = capacity > 0 ? (stored * 100.0 / capacity) : 0.0;
        return String.format(Locale.ROOT, "%.1f%%", ratio);
    }

    private static String formatFEt(long value) {
        return formatCompact(value) + " FE/t";
    }

    private static String formatFE(long value) {
        return formatCompact(value) + " FE";
    }

    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
