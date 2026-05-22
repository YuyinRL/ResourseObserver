package com.yuyinrl.resourceobserver.world.block.entity.debug;

import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.entity.Ae2CellCapacityMetrics;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Observer 调试消息构建器 —— 把方块实体的运行时状态格式化成多行可读消息。
 *
 * <p>从 {@link ObserverBlockEntity} 抽出，仅依赖其只读 getter，不修改任何状态。
 * 用于调试终端 / 调试命令输出绑定网络、统计数据、AE2 容量探测结果等信息。
 */
public final class ObserverDebugMessageBuilder {

    private ObserverDebugMessageBuilder() {
    }

    /** 构建调试状态消息列表（每行一个 Component）。 */
    public static List<Component> build(ObserverBlockEntity be) {
        List<Component> lines = new ArrayList<>();
        List<BoundEntry> bindings = be.getBindings();
        if (bindings.isEmpty()) {
            lines.add(Component.translatable("message.resourceobserver.debug.none"));
            return lines;
        }

        lines.add(Component.translatable("message.resourceobserver.debug.header", bindings.size()));
        for (int i = 0; i < bindings.size(); i++) {
            BoundEntry entry = bindings.get(i);
            BindingStats stats = be.getStatsFor(entry.networkId());
            lines.add(Component.translatable(
                    "message.resourceobserver.debug.binding",
                    i + 1,
                    entry.networkType(),
                    entry.targetBlockId()
            ));
            lines.add(Component.translatable(
                    "message.resourceobserver.debug.stats",
                    stats.currentValue(),
                    stats.capacity(),
                    stats.totalProduced(),
                    stats.totalConsumed()
            ));
            if ("AE2_ITEMS".equals(entry.networkType())) {
                Ae2CellCapacityMetrics metrics = be.getAe2CellCapacityMetricsFor(entry.networkId());
                lines.add(Component.translatable(
                        "message.resourceobserver.debug.cells",
                        metrics.itemUsedBytes(),
                        metrics.itemTotalBytes(),
                        metrics.itemUsedTypes(),
                        metrics.itemTotalTypes(),
                        metrics.itemUsedUnits(),
                        metrics.itemMaxUnits(),
                        metrics.fluidUsedBytes(),
                        metrics.fluidTotalBytes(),
                        metrics.fluidUsedTypes(),
                        metrics.fluidTotalTypes(),
                        metrics.fluidUsedUnits(),
                        metrics.fluidMaxUnits(),
                        boolComponent(metrics.reliable()),
                        boolComponent(metrics.available())
                ));
                lines.add(Component.translatable(
                        "message.resourceobserver.debug.external",
                        metrics.externalItemUsedUnits(),
                        metrics.externalItemTotalUnits(),
                        metrics.externalFluidUsedUnits(),
                        metrics.externalFluidTotalUnits(),
                        boolComponent(metrics.externalReliable()),
                        boolComponent(metrics.externalAvailable())
                ));
            }
            appendFormattedProbeLines(lines, be.getDebugInfoFor(entry.networkId()));
        }
        return lines;
    }

    private static void appendFormattedProbeLines(List<Component> lines, String debugInfo) {
        Map<String, String> fields = parseProbeFields(debugInfo);
        if (fields.isEmpty()) {
            lines.add(Component.translatable("message.resourceobserver.debug.probe_none"));
            return;
        }

        String channel = probeField(fields, "channel");
        String status = probeField(fields, "status");
        lines.add(Component.translatable("message.resourceobserver.debug.probe_header", channel, status));

        if (!"ae2.cached_inventory".equals(channel)) {
            addWrappedDebugLine(
                    lines,
                    "        ",
                    Component.translatable("message.resourceobserver.debug.probe_raw", debugInfo)
            );
            return;
        }

        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_inventory",
                        probeField(fields, "types"),
                        probeField(fields, "item_types"),
                        probeField(fields, "fluid_types"),
                        probeField(fields, "cells"),
                        probeField(fields, "capacity_reliable")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_cells",
                        probeField(fields, "capacity_cells_total"),
                        probeField(fields, "capacity_cells_readable"),
                        probeField(fields, "capacity_item_cells_total"),
                        probeField(fields, "capacity_item_cells_readable"),
                        probeField(fields, "capacity_fluid_cells_total"),
                        probeField(fields, "capacity_fluid_cells_readable"),
                        probeField(fields, "capacity_cells_null"),
                        probeField(fields, "capacity_cells_null_unpowered"),
                        probeField(fields, "capacity_cell_status_probe_failed")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_external",
                        probeField(fields, "capacity_external_storage_bus_total"),
                        probeField(fields, "capacity_external_storage_bus_readable"),
                        probeField(fields, "capacity_external_item_used"),
                        probeField(fields, "capacity_external_item_total"),
                        probeField(fields, "capacity_external_fluid_used"),
                        probeField(fields, "capacity_external_fluid_total"),
                        probeField(fields, "capacity_external_reliable"),
                        probeField(fields, "capacity_external_available")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_devices",
                        probeField(fields, "capacity_devices_total"),
                        probeField(fields, "capacity_devices_powered"),
                        probeField(fields, "capacity_devices_from_iChestOrDrive"),
                        probeField(fields, "capacity_devices_from_storage_provider"),
                        probeField(fields, "capacity_devices_from_node_owner"),
                        probeField(fields, "capacity_devices_from_node_storage_service"),
                        probeField(fields, "capacity_nodes_scanned")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_classes",
                        probeField(fields, "capacity_device_classes"),
                        probeField(fields, "capacity_cells_readable_classes"),
                        probeField(fields, "capacity_item_cells_readable_classes"),
                        probeField(fields, "capacity_fluid_cells_readable_classes")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_slot_channels",
                        probeField(fields, "capacity_slot_cell_items"),
                        probeField(fields, "capacity_slot_cell_keytypes"),
                        probeField(fields, "capacity_slot_channel_item"),
                        probeField(fields, "capacity_slot_channel_fluid"),
                        probeField(fields, "capacity_slot_channel_unknown")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_failures",
                        probeField(fields, "capacity_cells_missing_methods"),
                        probeField(fields, "capacity_cells_invocation_failed")
                )
        );
    }

    private static Component boolComponent(boolean value) {
        return Component.translatable(
                value ? "message.resourceobserver.debug.bool.true" : "message.resourceobserver.debug.bool.false"
        );
    }

    private static Map<String, String> parseProbeFields(String debugInfo) {
        Map<String, String> fields = new HashMap<>();
        if (debugInfo == null || debugInfo.isBlank()) {
            return fields;
        }
        String[] tokens = debugInfo.split(";");
        for (String token : tokens) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                fields.put(trimmed, "");
                continue;
            }
            fields.put(trimmed.substring(0, equalsIndex), trimmed.substring(equalsIndex + 1));
        }
        return fields;
    }

    private static String probeField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        return value;
    }

    private static void addWrappedDebugLine(List<Component> lines, String indent, Component content) {
        if (content == null) {
            return;
        }
        lines.add(Component.literal(indent).append(content));
    }
}
