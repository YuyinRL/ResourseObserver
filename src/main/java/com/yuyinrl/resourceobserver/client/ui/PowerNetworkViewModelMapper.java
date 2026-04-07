package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps payload data to the power network page view model.
 */
public final class PowerNetworkViewModelMapper {
    private PowerNetworkViewModelMapper() {
    }

    private static final double OVERLOAD_CRITICAL_THRESHOLD = 0.95;
    private static final double OVERLOAD_WARNING_THRESHOLD = 0.80;

    public static PowerNetworkViewModel fromPayload(ObserverDataPayload payload) {
        List<ObserverDataPayload.BindingEntry> fluxBindings = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if ("FLUX_ENERGY".equals(binding.networkType())) {
                fluxBindings.add(binding);
            }
        }

        List<PowerNetworkViewModel.DeviceEntry> devices = new ArrayList<>();
        long totalInputPerTick = 0L;
        long totalOutputPerTick = 0L;
        long totalStored = 0L;
        long totalCapacity = 0L;

        long debugInputRate = 0L;
        long debugOutputRate = 0L;
        long debugTotalBuffer = 0L;
        int debugPlugCount = 0;
        int debugPointCount = 0;
        int debugStorageCount = 0;
        int debugControllerCount = 0;

        Map<String, DeviceDebugPartial> debugDeviceBaseMap = new LinkedHashMap<>();
        Map<String, long[]> debugDeviceLegacyExtMap = new LinkedHashMap<>();
        Map<String, Map<String, long[]>> debugDeviceRefMetricMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> debugDeviceRefNameMap = new LinkedHashMap<>();
        List<String> debugInputDeviceKeys = new ArrayList<>();
        List<String> debugOutputDeviceKeys = new ArrayList<>();

        for (ObserverDataPayload.BindingEntry binding : fluxBindings) {
            long stored = binding.currentValue();
            long capacity = Math.max(1L, binding.capacity());

            long inputPerTick = computeEnergyPerTick(binding, true);
            long outputPerTick = computeEnergyPerTick(binding, false);

            totalInputPerTick = saturatingAdd(totalInputPerTick, inputPerTick);
            totalOutputPerTick = saturatingAdd(totalOutputPerTick, outputPerTick);
            totalStored = saturatingAdd(totalStored, stored);
            totalCapacity = saturatingAdd(totalCapacity, capacity);

            if (binding.itemDeltas() != null) {
                for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                    switch (item.itemId()) {
                        case "flux.input_per_tick" -> debugInputRate = saturatingAdd(debugInputRate, Math.abs(item.amount()));
                        case "flux.output_per_tick" -> debugOutputRate = saturatingAdd(debugOutputRate, Math.abs(item.amount()));
                        case "flux.max_energy_storage" -> debugTotalBuffer = saturatingAdd(debugTotalBuffer, Math.abs(item.amount()));
                        case "flux.plug_count" -> debugPlugCount += (int) item.amount();
                        case "flux.point_count" -> debugPointCount += (int) item.amount();
                        case "flux.storage_count" -> debugStorageCount += (int) item.amount();
                        case "flux.controller_count" -> debugControllerCount += (int) item.amount();
                        default -> parseDebugDeviceItem(
                                binding,
                                item,
                                debugDeviceBaseMap,
                                debugDeviceLegacyExtMap,
                                debugDeviceRefMetricMap,
                                debugDeviceRefNameMap,
                                debugInputDeviceKeys,
                                debugOutputDeviceKeys
                        );
                    }
                }
            }

            double usageRatio = Math.min(1.0, (double) stored / capacity);
            String category = inferCategory(binding.targetBlockId());
            PowerNetworkViewModel.AlertLevel alertLevel = computeAlertLevel(usageRatio, inputPerTick, outputPerTick);

            devices.add(new PowerNetworkViewModel.DeviceEntry(
                    binding.networkId(),
                    binding.displayName(),
                    category,
                    outputPerTick > 0 ? outputPerTick : inputPerTick,
                    stored,
                    capacity,
                    usageRatio,
                    alertLevel
            ));
        }

        devices.sort(Comparator.comparingLong(PowerNetworkViewModel.DeviceEntry::energyPerTick).reversed());

        List<PowerNetworkViewModel.LoadSegment> loadSegments = buildLoadSegments(devices);
        List<PowerNetworkViewModel.OverloadAlert> overloadAlerts = buildOverloadAlerts(devices);
        List<PowerNetworkViewModel.PowerKpi> kpiCards = buildKpiCards(
                totalInputPerTick, totalOutputPerTick, totalStored, totalCapacity
        );

        double headroomPercent = totalInputPerTick > 0
                ? Math.max(0.0, (totalInputPerTick - totalOutputPerTick) * 100.0 / totalInputPerTick)
                : 0.0;
        long reservePerTick = Math.max(0, totalInputPerTick - totalOutputPerTick);
        PowerNetworkViewModel.OverloadInfo overloadInfo = new PowerNetworkViewModel.OverloadInfo(
                headroomPercent, reservePerTick
        );

        List<PowerNetworkViewModel.DeviceDebugEntry> debugInputDevices = new ArrayList<>();
        for (String key : debugInputDeviceKeys) {
            debugInputDevices.add(buildDeviceWithExt(
                    key,
                    debugDeviceBaseMap.get(key),
                    debugDeviceLegacyExtMap.get(key),
                    debugDeviceRefMetricMap.get(key),
                    debugDeviceRefNameMap.get(key)
            ));
        }

        List<PowerNetworkViewModel.DeviceDebugEntry> debugOutputDevices = new ArrayList<>();
        for (String key : debugOutputDeviceKeys) {
            debugOutputDevices.add(buildDeviceWithExt(
                    key,
                    debugDeviceBaseMap.get(key),
                    debugDeviceLegacyExtMap.get(key),
                    debugDeviceRefMetricMap.get(key),
                    debugDeviceRefNameMap.get(key)
            ));
        }

        List<PowerNetworkViewModel.ExternalGroup> externalGroups = buildExternalGroups(debugInputDevices, debugOutputDevices);

        PowerNetworkViewModel.DebugSnapshot debugSnapshot = new PowerNetworkViewModel.DebugSnapshot(
                debugInputRate,
                debugOutputRate,
                debugTotalBuffer,
                debugPlugCount,
                debugPointCount,
                debugStorageCount,
                debugControllerCount,
                List.copyOf(debugInputDevices),
                List.copyOf(debugOutputDevices),
                externalGroups
        );

        return new PowerNetworkViewModel(
                devices,
                kpiCards,
                loadSegments,
                overloadAlerts,
                overloadInfo,
                totalInputPerTick,
                totalOutputPerTick,
                totalStored,
                totalCapacity,
                totalOutputPerTick,
                debugSnapshot
        );
    }

    private static void parseDebugDeviceItem(
            ObserverDataPayload.BindingEntry binding,
            ObserverDataPayload.ItemDeltaEntry item,
            Map<String, DeviceDebugPartial> baseMap,
            Map<String, long[]> legacyExtMap,
            Map<String, Map<String, long[]>> refMetricMap,
            Map<String, Map<String, String>> refNameMap,
            List<String> inputKeys,
            List<String> outputKeys
    ) {
        if (item.itemId() == null || !item.itemId().startsWith("flux.device.")) {
            return;
        }

        String suffix = item.itemId().substring("flux.device.".length());
        int dotPos = suffix.indexOf('.');
        if (dotPos < 0) {
            return;
        }

        String idxStr = suffix.substring(0, dotPos);
        String tail = suffix.substring(dotPos + 1);
        String deviceKey = binding.networkId() + ":" + idxStr;

        if ("ext_stored".equals(tail)) {
            legacyExtMap.computeIfAbsent(deviceKey, k -> new long[2])[0] = item.amount();
            return;
        }
        if ("ext_cap".equals(tail)) {
            legacyExtMap.computeIfAbsent(deviceKey, k -> new long[2])[1] = item.amount();
            return;
        }

        ExtRefMetricToken extToken = parseExtToken(tail);
        if (extToken != null) {
            Map<String, long[]> metricsByExtId = refMetricMap.computeIfAbsent(deviceKey, k -> new LinkedHashMap<>());
            long[] metrics = metricsByExtId.computeIfAbsent(extToken.extId(), k -> new long[2]);
            if ("stored".equals(extToken.metric())) {
                metrics[0] = item.amount();
            } else {
                metrics[1] = item.amount();
            }
            String name = item.displayName() == null || item.displayName().isBlank()
                    ? extToken.extId()
                    : item.displayName();
            refNameMap.computeIfAbsent(deviceKey, k -> new LinkedHashMap<>()).put(extToken.extId(), name);
            return;
        }

        String deviceType = tail;
        String interfaceKey = deviceKey + ":" + deviceType;
        baseMap.put(deviceKey, new DeviceDebugPartial(
                interfaceKey,
                item.displayName(),
                deviceType,
                item.delta(),
                item.amount()
        ));
        if ("plug".equals(deviceType)) {
            addKeyOnce(inputKeys, deviceKey);
        } else if ("point".equals(deviceType)) {
            addKeyOnce(outputKeys, deviceKey);
        }
    }

    private static ExtRefMetricToken parseExtToken(String tail) {
        if (tail == null || !tail.startsWith("ext.")) {
            return null;
        }
        String payload = tail.substring("ext.".length());
        int split = payload.lastIndexOf('.');
        if (split <= 0 || split >= payload.length() - 1) {
            return null;
        }
        String extId = payload.substring(0, split);
        String metric = payload.substring(split + 1);
        if (!"stored".equals(metric) && !"cap".equals(metric)) {
            return null;
        }
        return new ExtRefMetricToken(extId, metric);
    }

    private static void addKeyOnce(List<String> keys, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (!keys.contains(key)) {
            keys.add(key);
        }
    }

    private static PowerNetworkViewModel.DeviceDebugEntry buildDeviceWithExt(
            String deviceKey,
            DeviceDebugPartial base,
            long[] legacyExt,
            Map<String, long[]> refMetricByExtId,
            Map<String, String> refNameByExtId
    ) {
        if (base == null) {
            return new PowerNetworkViewModel.DeviceDebugEntry(
                    deviceKey,
                    "?",
                    "unknown",
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of()
            );
        }

        List<PowerNetworkViewModel.ExternalRef> refs = new ArrayList<>();
        long aggregatedStored = 0L;
        long aggregatedCap = 0L;

        if (refMetricByExtId != null && !refMetricByExtId.isEmpty()) {
            List<Map.Entry<String, long[]>> entries = new ArrayList<>(refMetricByExtId.entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, long[]> entry : entries) {
                String extId = entry.getKey();
                long[] metrics = entry.getValue();
                long stored = metrics != null ? metrics[0] : 0L;
                long cap = metrics != null ? metrics[1] : 0L;
                String name = extId;
                if (refNameByExtId != null && refNameByExtId.containsKey(extId)) {
                    String n = refNameByExtId.get(extId);
                    if (n != null && !n.isBlank()) {
                        name = n;
                    }
                }
                refs.add(new PowerNetworkViewModel.ExternalRef(extId, name, stored, cap));
                aggregatedStored = saturatingAdd(aggregatedStored, Math.max(0L, stored));
                aggregatedCap = saturatingAdd(aggregatedCap, Math.max(0L, cap));
            }
        }

        if (refs.isEmpty() && legacyExt != null) {
            aggregatedStored = Math.max(0L, legacyExt[0]);
            aggregatedCap = Math.max(0L, legacyExt[1]);
        }

        return new PowerNetworkViewModel.DeviceDebugEntry(
                base.interfaceKey(),
                base.deviceName(),
                base.deviceType(),
                base.transferRate(),
                base.bufferStored(),
                aggregatedStored,
                aggregatedCap,
                List.copyOf(refs)
        );
    }

    private static List<PowerNetworkViewModel.ExternalGroup> buildExternalGroups(
            List<PowerNetworkViewModel.DeviceDebugEntry> inputDevices,
            List<PowerNetworkViewModel.DeviceDebugEntry> outputDevices
    ) {
        Map<String, ExternalGroupAccumulator> grouped = new LinkedHashMap<>();

        List<PowerNetworkViewModel.DeviceDebugEntry> all = new ArrayList<>(inputDevices.size() + outputDevices.size());
        all.addAll(inputDevices);
        all.addAll(outputDevices);

        for (PowerNetworkViewModel.DeviceDebugEntry device : all) {
            if (device.externalRefs() == null || device.externalRefs().isEmpty()) {
                continue;
            }
            for (PowerNetworkViewModel.ExternalRef ref : device.externalRefs()) {
                if (ref.extId() == null || ref.extId().isBlank()) {
                    continue;
                }
                ExternalGroupAccumulator acc = grouped.computeIfAbsent(
                        ref.extId(),
                        k -> new ExternalGroupAccumulator(ref.extId())
                );
                acc.displayName = (ref.displayName() == null || ref.displayName().isBlank())
                        ? acc.displayName
                        : ref.displayName();
                acc.stored = Math.max(acc.stored, Math.max(0L, ref.stored()));
                acc.capacity = Math.max(acc.capacity, Math.max(0L, ref.capacity()));
                if (device.interfaceKey() != null && !device.interfaceKey().isBlank()) {
                    acc.interfaceKeys.add(device.interfaceKey());
                }
            }
        }

        List<PowerNetworkViewModel.ExternalGroup> result = new ArrayList<>();
        for (ExternalGroupAccumulator acc : grouped.values()) {
            String name = (acc.displayName == null || acc.displayName.isBlank()) ? acc.extId : acc.displayName;
            result.add(new PowerNetworkViewModel.ExternalGroup(
                    acc.extId,
                    name,
                    acc.stored,
                    acc.capacity,
                    List.copyOf(acc.interfaceKeys)
            ));
        }
        result.sort(Comparator
                .comparingLong(PowerNetworkViewModel.ExternalGroup::capacity)
                .reversed()
                .thenComparing(PowerNetworkViewModel.ExternalGroup::displayName));
        return List.copyOf(result);
    }

    private static long computeEnergyPerTick(ObserverDataPayload.BindingEntry binding, boolean isInput) {
        if (binding.itemDeltas() != null) {
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                String key = isInput ? "flux.input_per_tick" : "flux.output_per_tick";
                if (key.equals(item.itemId())) {
                    return Math.abs(item.amount());
                }
            }
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                if ("flux.energy_stored".equals(item.itemId())) {
                    if (isInput && item.delta() > 0) {
                        return item.delta();
                    }
                    if (!isInput && item.delta() < 0) {
                        return Math.abs(item.delta());
                    }
                }
            }
        }
        return isInput ? binding.totalProduced() : binding.totalConsumed();
    }

    private static String inferCategory(String targetBlockId) {
        if (targetBlockId == null || targetBlockId.isBlank()) {
            return "other";
        }
        String lower = targetBlockId.toLowerCase(Locale.ROOT);
        if (lower.contains("miner") || lower.contains("quarry") || lower.contains("drill")
                || lower.contains("pump") || lower.contains("excavat")) {
            return "mining";
        }
        if (lower.contains("assembl") || lower.contains("craft") || lower.contains("inscriber")
                || lower.contains("press") || lower.contains("furnace") || lower.contains("smelter")
                || lower.contains("grinder") || lower.contains("crusher") || lower.contains("machine")) {
            return "assembly";
        }
        if (lower.contains("bus") || lower.contains("interface") || lower.contains("import")
                || lower.contains("export") || lower.contains("pipe") || lower.contains("duct")
                || lower.contains("conveyor") || lower.contains("router")) {
            return "logistics";
        }
        return "other";
    }

    private static PowerNetworkViewModel.AlertLevel computeAlertLevel(
            double usageRatio, long inputPerTick, long outputPerTick
    ) {
        if (usageRatio <= 0.05 && outputPerTick > 0) {
            return PowerNetworkViewModel.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_CRITICAL_THRESHOLD) {
            return PowerNetworkViewModel.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_WARNING_THRESHOLD) {
            return PowerNetworkViewModel.AlertLevel.WARNING;
        }
        if (outputPerTick > 0 && inputPerTick > 0 && outputPerTick > inputPerTick * 2) {
            return PowerNetworkViewModel.AlertLevel.WARNING;
        }
        return PowerNetworkViewModel.AlertLevel.NORMAL;
    }

    private static List<PowerNetworkViewModel.LoadSegment> buildLoadSegments(
            List<PowerNetworkViewModel.DeviceEntry> devices
    ) {
        if (devices.isEmpty()) {
            return List.of();
        }

        Map<String, Long> categoryTotals = new LinkedHashMap<>();
        long grandTotal = 0L;
        for (PowerNetworkViewModel.DeviceEntry device : devices) {
            String cat = device.category() == null ? "other" : device.category();
            categoryTotals.merge(cat, Math.max(0, device.energyPerTick()), Long::sum);
            grandTotal += Math.max(0, device.energyPerTick());
        }
        if (grandTotal <= 0L) {
            return List.of();
        }

        List<PowerNetworkViewModel.LoadSegment> segments = new ArrayList<>();
        for (Map.Entry<String, Long> entry : categoryTotals.entrySet()) {
            double pct = (entry.getValue() * 100.0) / grandTotal;
            if (pct < 0.5) {
                continue;
            }
            String displayName = categoryDisplayName(entry.getKey());
            int color = categoryColor(entry.getKey());
            segments.add(new PowerNetworkViewModel.LoadSegment(entry.getKey(), displayName, pct, color));
        }
        segments.sort(Comparator.comparingDouble(PowerNetworkViewModel.LoadSegment::percentage).reversed());
        return segments;
    }

    private static List<PowerNetworkViewModel.OverloadAlert> buildOverloadAlerts(
            List<PowerNetworkViewModel.DeviceEntry> devices
    ) {
        List<PowerNetworkViewModel.OverloadAlert> alerts = new ArrayList<>();
        for (PowerNetworkViewModel.DeviceEntry device : devices) {
            if (device.alertLevel() == PowerNetworkViewModel.AlertLevel.NORMAL) {
                continue;
            }
            double throughputLoss = 0.0;
            if (device.usageRatio() >= OVERLOAD_CRITICAL_THRESHOLD) {
                throughputLoss = (device.usageRatio() - OVERLOAD_CRITICAL_THRESHOLD)
                        / (1.0 - OVERLOAD_CRITICAL_THRESHOLD) * 50.0;
                throughputLoss = Math.min(100.0, Math.max(0.0, throughputLoss));
            } else if (device.storedEnergy() <= 0 && device.energyPerTick() > 0) {
                throughputLoss = 100.0;
            }

            String desc = Component.translatable(
                    "screen.resourceobserver.power.alert.description",
                    device.displayName(),
                    String.format(Locale.ROOT, "%.0f%%", throughputLoss)
            ).getString();

            alerts.add(new PowerNetworkViewModel.OverloadAlert(
                    device.nodeId(),
                    device.displayName(),
                    device.alertLevel(),
                    throughputLoss,
                    desc
            ));
        }
        return alerts;
    }

    private static List<PowerNetworkViewModel.PowerKpi> buildKpiCards(
            long totalInput,
            long totalOutput,
            long totalStored,
            long totalCapacity
    ) {
        OverviewViewModel.Status inputStatus = totalInput > 0
                ? OverviewViewModel.Status.POSITIVE
                : OverviewViewModel.Status.NEUTRAL;

        OverviewViewModel.Status outputStatus = totalOutput > totalInput
                ? OverviewViewModel.Status.WARNING
                : (totalOutput > 0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.NEUTRAL);

        double storedRatio = totalCapacity > 0 ? (double) totalStored / totalCapacity : 0.0;
        OverviewViewModel.Status storedStatus;
        if (storedRatio <= 0.1) {
            storedStatus = OverviewViewModel.Status.NEGATIVE;
        } else if (storedRatio >= 0.9) {
            storedStatus = OverviewViewModel.Status.WARNING;
        } else {
            storedStatus = OverviewViewModel.Status.POSITIVE;
        }

        return List.of(
                new PowerNetworkViewModel.PowerKpi(
                        "screen.resourceobserver.power.kpi.total_input",
                        formatCompact(totalInput) + " FE/t",
                        inputStatus
                ),
                new PowerNetworkViewModel.PowerKpi(
                        "screen.resourceobserver.power.kpi.total_output",
                        formatCompact(totalOutput) + " FE/t",
                        outputStatus
                ),
                new PowerNetworkViewModel.PowerKpi(
                        "screen.resourceobserver.power.kpi.stored_energy",
                        formatCompact(totalStored) + " / " + formatCompact(totalCapacity) + " FE",
                        storedStatus
                )
        );
    }

    private static String categoryDisplayName(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "mining" -> Component.translatable("screen.resourceobserver.power.category.mining").getString();
            case "assembly" -> Component.translatable("screen.resourceobserver.power.category.assembly").getString();
            case "logistics" -> Component.translatable("screen.resourceobserver.power.category.logistics").getString();
            default -> Component.translatable("screen.resourceobserver.power.category.other").getString();
        };
    }

    private static int categoryColor(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "mining" -> UiThemeTokens.CYAN;
            case "assembly" -> UiThemeTokens.AMBER;
            case "logistics" -> UiThemeTokens.EMERALD;
            default -> UiThemeTokens.BLUE;
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

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record DeviceDebugPartial(
            String interfaceKey,
            String deviceName,
            String deviceType,
            long transferRate,
            long bufferStored
    ) {
    }

    private record ExtRefMetricToken(String extId, String metric) {
    }

    private static final class ExternalGroupAccumulator {
        private final String extId;
        private String displayName;
        private long stored;
        private long capacity;
        private final LinkedHashSet<String> interfaceKeys = new LinkedHashSet<>();

        private ExternalGroupAccumulator(String extId) {
            this.extId = extId;
            this.displayName = extId;
        }
    }
}
