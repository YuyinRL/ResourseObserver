package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.client.ui.snapshot.ClientPowerLocalizer;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshotBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力网络视图模型映射器 —— Snapshot → 客户端 ViewModel 的薄壳适配器。
 *
 * <p>F3.B 起：用户面向的数据塑形由 {@link PowerSnapshotBuilder} 完成；本类只负责
 * 本地化、颜色槽映射、枚举 1:1 转换，以及临时保留 Debug Terminal 专用数据。</p>
 */
public final class PowerNetworkViewModelMapper {

    private static final int[] CONSUMER_COLORS = {
            UiThemeTokens.CYAN,
            UiThemeTokens.AMBER,
            UiThemeTokens.EMERALD,
            UiThemeTokens.BLUE,
            UiThemeTokens.ROSE,
            0xFF818CF8,
            0xFFA78BFA,
            0xFF2DD4BF,
    };

    private PowerNetworkViewModelMapper() {
    }

    public static PowerNetworkViewModel fromPayload(ObserverDataPayload payload) {
        PowerSnapshot snapshot = PowerSnapshotBuilder.fromPayload(payload);
        PowerNetworkViewModel.DebugSnapshot debugSnapshot = buildDebugSnapshot(payload);
        return fromSnapshot(snapshot, debugSnapshot);
    }

    /** Snapshot → ViewModel 转换入口，供 V2 / 测试直接复用。 */
    public static PowerNetworkViewModel fromSnapshot(PowerSnapshot snapshot) {
        return fromSnapshot(snapshot, PowerNetworkViewModel.DebugSnapshot.empty());
    }

    private static PowerNetworkViewModel fromSnapshot(
            PowerSnapshot snapshot,
            PowerNetworkViewModel.DebugSnapshot debugSnapshot
    ) {
        if (snapshot == null) {
            snapshot = PowerSnapshotBuilder.fromPayload(null);
        }
        return new PowerNetworkViewModel(
                mapDevices(snapshot.devices()),
                mapKpis(snapshot.kpiCards()),
                mapLoadSegments(snapshot.loadSegments()),
                mapOverloadAlerts(snapshot.overloadAlerts()),
                mapOverloadInfo(snapshot.overloadInfo()),
                snapshot.totalInputPerTick(),
                snapshot.totalOutputPerTick(),
                snapshot.totalStored(),
                snapshot.totalCapacity(),
                snapshot.totalOutputPerTick(),
                debugSnapshot == null ? PowerNetworkViewModel.DebugSnapshot.empty() : debugSnapshot,
                mapConsumers(snapshot.consumers())
        );
    }

    private static List<PowerNetworkViewModel.DeviceEntry> mapDevices(
            List<PowerSnapshot.DeviceSnapshot> source
    ) {
        List<PowerNetworkViewModel.DeviceEntry> result = new ArrayList<>(source.size());
        for (PowerSnapshot.DeviceSnapshot device : source) {
            result.add(new PowerNetworkViewModel.DeviceEntry(
                    device.nodeId(),
                    device.displayName(),
                    categoryKey(device.category()),
                    device.modName(),
                    device.energyPerTick(),
                    device.storedEnergy(),
                    device.maxCapacity(),
                    device.usageRatio(),
                    mapAlert(device.alertLevel())
            ));
        }
        return result;
    }

    private static List<PowerNetworkViewModel.PowerKpi> mapKpis(List<PowerSnapshot.KpiSnapshot> source) {
        List<PowerNetworkViewModel.PowerKpi> result = new ArrayList<>(source.size());
        for (PowerSnapshot.KpiSnapshot kpi : source) {
            result.add(new PowerNetworkViewModel.PowerKpi(
                    ClientPowerLocalizer.INSTANCE.localizeKey(kpi.labelKey()),
                    kpi.value(),
                    mapStatus(kpi.status())
            ));
        }
        return result;
    }

    private static List<PowerNetworkViewModel.LoadSegment> mapLoadSegments(
            List<PowerSnapshot.LoadSegmentSnapshot> source
    ) {
        List<PowerNetworkViewModel.LoadSegment> result = new ArrayList<>(source.size());
        for (PowerSnapshot.LoadSegmentSnapshot segment : source) {
            result.add(new PowerNetworkViewModel.LoadSegment(
                    categoryKey(segment.category()),
                    ClientPowerLocalizer.INSTANCE.localizeKey(segment.displayNameKey()),
                    segment.percentage(),
                    categoryColor(segment.category())
            ));
        }
        return result;
    }

    private static List<PowerNetworkViewModel.OverloadAlert> mapOverloadAlerts(
            List<PowerSnapshot.OverloadAlertSnapshot> source
    ) {
        List<PowerNetworkViewModel.OverloadAlert> result = new ArrayList<>(source.size());
        for (PowerSnapshot.OverloadAlertSnapshot alert : source) {
            result.add(new PowerNetworkViewModel.OverloadAlert(
                    alert.nodeId(),
                    alert.displayName(),
                    mapAlert(alert.alertLevel()),
                    alert.throughputLoss(),
                    ClientPowerLocalizer.INSTANCE.localizeKey(
                            alert.descriptionKey(), alert.descriptionArgs().toArray())
            ));
        }
        return result;
    }

    private static PowerNetworkViewModel.OverloadInfo mapOverloadInfo(PowerSnapshot.OverloadInfoSnapshot info) {
        if (info == null) {
            return new PowerNetworkViewModel.OverloadInfo(0.0, 0L);
        }
        return new PowerNetworkViewModel.OverloadInfo(info.headroomPercent(), info.reservePerTick());
    }

    private static List<PowerNetworkViewModel.ConsumerEntry> mapConsumers(
            List<PowerSnapshot.ConsumerSnapshot> source
    ) {
        List<PowerNetworkViewModel.ConsumerEntry> result = new ArrayList<>(source.size());
        for (PowerSnapshot.ConsumerSnapshot consumer : source) {
            result.add(new PowerNetworkViewModel.ConsumerEntry(
                    consumer.deviceName(),
                    consumer.modName(),
                    consumer.consumptionPerTick(),
                    consumer.percentage(),
                    consumer.supplyRatio(),
                    consumerColor(consumer.paletteIndex()),
                    consumer.count()
            ));
        }
        return result;
    }

    // ===== Debug Terminal 专用路径：暂未进入 PowerSnapshot 契约 =====

    private static PowerNetworkViewModel.DebugSnapshot buildDebugSnapshot(ObserverDataPayload payload) {
        if (payload == null) {
            return PowerNetworkViewModel.DebugSnapshot.empty();
        }

        DebugParseState state = new DebugParseState();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if (!"FLUX_ENERGY".equals(binding.networkType()) || binding.itemDeltas() == null) {
                continue;
            }
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                parseDebugItem(binding, item, state);
            }
        }

        List<PowerNetworkViewModel.DeviceDebugEntry> inputDevices = buildDebugDevices(state.inputKeys, state);
        List<PowerNetworkViewModel.DeviceDebugEntry> outputDevices = buildDebugDevices(state.outputKeys, state);
        return new PowerNetworkViewModel.DebugSnapshot(
                state.inputRate,
                state.outputRate,
                state.totalBuffer,
                state.plugCount,
                state.pointCount,
                state.storageCount,
                state.controllerCount,
                inputDevices,
                outputDevices,
                buildExternalGroups(inputDevices, outputDevices)
        );
    }

    private static void parseDebugItem(
            ObserverDataPayload.BindingEntry binding,
            ObserverDataPayload.ItemDeltaEntry item,
            DebugParseState state
    ) {
        switch (item.itemId()) {
            case "flux.input_per_tick" -> state.inputRate = saturatingAdd(state.inputRate, Math.abs(item.amount()));
            case "flux.output_per_tick" -> state.outputRate = saturatingAdd(state.outputRate, Math.abs(item.amount()));
            case "flux.max_energy_storage" -> state.totalBuffer = saturatingAdd(state.totalBuffer, Math.abs(item.amount()));
            case "flux.plug_count" -> state.plugCount += (int) item.amount();
            case "flux.point_count" -> state.pointCount += (int) item.amount();
            case "flux.storage_count" -> state.storageCount += (int) item.amount();
            case "flux.controller_count" -> state.controllerCount += (int) item.amount();
            default -> parseDebugDeviceItem(binding, item, state);
        }
    }

    private static void parseDebugDeviceItem(
            ObserverDataPayload.BindingEntry binding,
            ObserverDataPayload.ItemDeltaEntry item,
            DebugParseState state
    ) {
        if (item.itemId() == null || !item.itemId().startsWith("flux.device.")) {
            return;
        }
        String suffix = item.itemId().substring("flux.device.".length());
        int dotPos = suffix.indexOf('.');
        if (dotPos < 0) {
            return;
        }

        String deviceKey = binding.networkId() + ":" + suffix.substring(0, dotPos);
        String tail = suffix.substring(dotPos + 1);
        if ("ext_stored".equals(tail)) {
            state.legacyExtMap.computeIfAbsent(deviceKey, k -> new long[2])[0] = item.amount();
            return;
        }
        if ("ext_cap".equals(tail)) {
            state.legacyExtMap.computeIfAbsent(deviceKey, k -> new long[2])[1] = item.amount();
            return;
        }
        ExtRefMetricToken extToken = parseExtToken(tail);
        if (extToken != null) {
            recordExternalRef(item, deviceKey, extToken, state);
            return;
        }

        String interfaceKey = deviceKey + ":" + tail;
        state.baseMap.put(deviceKey, new DeviceDebugPartial(
                interfaceKey, item.displayName(), tail, Math.round(item.delta()), item.amount()
        ));
        if ("plug".equals(tail)) {
            addKeyOnce(state.inputKeys, deviceKey);
        } else if ("point".equals(tail)) {
            addKeyOnce(state.outputKeys, deviceKey);
        }
    }

    private static void recordExternalRef(
            ObserverDataPayload.ItemDeltaEntry item,
            String deviceKey,
            ExtRefMetricToken extToken,
            DebugParseState state
    ) {
        Map<String, long[]> metricsByExtId = state.refMetricMap.computeIfAbsent(deviceKey, k -> new LinkedHashMap<>());
        long[] metrics = metricsByExtId.computeIfAbsent(extToken.extId(), k -> new long[3]);
        switch (extToken.metric()) {
            case "stored" -> metrics[0] = item.amount();
            case "cap" -> metrics[1] = item.amount();
            case "maxAccept" -> metrics[2] = item.amount();
            default -> { }
        }
        if ("stored".equals(extToken.metric())) {
            String name = item.displayName() == null || item.displayName().isBlank()
                    ? extToken.extId()
                    : item.displayName();
            state.refNameMap.computeIfAbsent(deviceKey, k -> new LinkedHashMap<>()).putIfAbsent(extToken.extId(), name);
        }
    }

    private static List<PowerNetworkViewModel.DeviceDebugEntry> buildDebugDevices(
            List<String> keys,
            DebugParseState state
    ) {
        List<PowerNetworkViewModel.DeviceDebugEntry> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            result.add(buildDeviceWithExt(
                    key,
                    state.baseMap.get(key),
                    state.legacyExtMap.get(key),
                    state.refMetricMap.get(key),
                    state.refNameMap.get(key)
            ));
        }
        return List.copyOf(result);
    }

    private static PowerNetworkViewModel.DeviceDebugEntry buildDeviceWithExt(
            String deviceKey,
            DeviceDebugPartial base,
            long[] legacyExt,
            Map<String, long[]> refMetricByExtId,
            Map<String, String> refNameByExtId
    ) {
        if (base == null) {
            return new PowerNetworkViewModel.DeviceDebugEntry(deviceKey, "?", "unknown", 0L, 0L, 0L, 0L, List.of());
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
                long maxAccept = metrics != null && metrics.length > 2 ? metrics[2] : 0L;
                refs.add(new PowerNetworkViewModel.ExternalRef(
                        extId, externalName(extId, refNameByExtId), stored, cap, maxAccept
                ));
                aggregatedStored = saturatingAdd(aggregatedStored, Math.max(0L, stored));
                aggregatedCap = saturatingAdd(aggregatedCap, Math.max(0L, cap));
            }
        }
        if (refs.isEmpty() && legacyExt != null) {
            aggregatedStored = Math.max(0L, legacyExt[0]);
            aggregatedCap = Math.max(0L, legacyExt[1]);
        }
        return new PowerNetworkViewModel.DeviceDebugEntry(
                base.interfaceKey(), base.deviceName(), base.deviceType(), base.transferRate(), base.bufferStored(),
                aggregatedStored, aggregatedCap, List.copyOf(refs)
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
            for (PowerNetworkViewModel.ExternalRef ref : device.externalRefs()) {
                if (ref.extId() == null || ref.extId().isBlank()) {
                    continue;
                }
                ExternalGroupAccumulator acc = grouped.computeIfAbsent(ref.extId(), ExternalGroupAccumulator::new);
                acc.displayName = cleanExternalDisplayName(ref.displayName(), acc.extId);
                acc.stored = Math.max(acc.stored, Math.max(0L, ref.stored()));
                acc.capacity = Math.max(acc.capacity, Math.max(0L, ref.capacity()));
                addKeyOnce(acc.interfaceKeys, device.interfaceKey());
            }
        }
        List<PowerNetworkViewModel.ExternalGroup> result = new ArrayList<>(grouped.size());
        for (ExternalGroupAccumulator acc : grouped.values()) {
            result.add(new PowerNetworkViewModel.ExternalGroup(
                    acc.extId, acc.displayName, acc.stored, acc.capacity, List.copyOf(acc.interfaceKeys)
            ));
        }
        result.sort(Comparator.comparingLong(PowerNetworkViewModel.ExternalGroup::capacity)
                .reversed().thenComparing(PowerNetworkViewModel.ExternalGroup::displayName));
        return List.copyOf(result);
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
        String metric = payload.substring(split + 1);
        if (!"stored".equals(metric) && !"cap".equals(metric) && !"maxAccept".equals(metric)) {
            return null;
        }
        return new ExtRefMetricToken(payload.substring(0, split), metric);
    }

    private static void addKeyOnce(List<String> keys, String key) {
        if (key != null && !key.isBlank() && !keys.contains(key)) {
            keys.add(key);
        }
    }

    private static String externalName(String extId, Map<String, String> names) {
        if (names == null) {
            return extId;
        }
        String name = names.get(extId);
        return name == null || name.isBlank() ? extId : name;
    }

    private static String cleanExternalDisplayName(String rawDisplay, String fallback) {
        if (rawDisplay == null || rawDisplay.isBlank()) {
            return fallback;
        }
        int pipeIdx = rawDisplay.indexOf('|');
        return pipeIdx >= 0 ? rawDisplay.substring(pipeIdx + 1) : rawDisplay;
    }

    private static PowerNetworkViewModel.AlertLevel mapAlert(PowerSnapshot.AlertLevel alert) {
        if (alert == null) {
            return PowerNetworkViewModel.AlertLevel.NORMAL;
        }
        return switch (alert) {
            case NORMAL -> PowerNetworkViewModel.AlertLevel.NORMAL;
            case WARNING -> PowerNetworkViewModel.AlertLevel.WARNING;
            case CRITICAL -> PowerNetworkViewModel.AlertLevel.CRITICAL;
        };
    }

    private static OverviewViewModel.Status mapStatus(OverviewSnapshot.KpiStatus status) {
        if (status == null) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        return switch (status) {
            case POSITIVE -> OverviewViewModel.Status.POSITIVE;
            case WARNING -> OverviewViewModel.Status.WARNING;
            case NEGATIVE -> OverviewViewModel.Status.NEGATIVE;
            case NEUTRAL -> OverviewViewModel.Status.NEUTRAL;
        };
    }

    private static String categoryKey(PowerSnapshot.LoadCategory category) {
        if (category == null) {
            return "other";
        }
        return switch (category) {
            case MINING -> "mining";
            case ASSEMBLY -> "assembly";
            case LOGISTICS -> "logistics";
            case OTHER -> "other";
        };
    }

    private static int categoryColor(PowerSnapshot.LoadCategory category) {
        if (category == null) {
            return UiThemeTokens.BLUE;
        }
        return switch (category) {
            case MINING -> UiThemeTokens.CYAN;
            case ASSEMBLY -> UiThemeTokens.AMBER;
            case LOGISTICS -> UiThemeTokens.EMERALD;
            case OTHER -> UiThemeTokens.BLUE;
        };
    }

    private static int consumerColor(int paletteIndex) {
        return CONSUMER_COLORS[Math.floorMod(paletteIndex, CONSUMER_COLORS.length)];
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

    private static final class DebugParseState {
        private long inputRate;
        private long outputRate;
        private long totalBuffer;
        private int plugCount;
        private int pointCount;
        private int storageCount;
        private int controllerCount;
        private final Map<String, DeviceDebugPartial> baseMap = new LinkedHashMap<>();
        private final Map<String, long[]> legacyExtMap = new LinkedHashMap<>();
        private final Map<String, Map<String, long[]>> refMetricMap = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> refNameMap = new LinkedHashMap<>();
        private final List<String> inputKeys = new ArrayList<>();
        private final List<String> outputKeys = new ArrayList<>();
    }

    private static final class ExternalGroupAccumulator {
        private final String extId;
        private String displayName;
        private long stored;
        private long capacity;
        private final List<String> interfaceKeys = new ArrayList<>();

        private ExternalGroupAccumulator(String extId) {
            this.extId = extId;
            this.displayName = extId;
        }
    }
}
