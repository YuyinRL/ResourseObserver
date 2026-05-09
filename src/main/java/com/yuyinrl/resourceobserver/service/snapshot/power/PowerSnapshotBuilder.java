package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Power Snapshot 装配主入口。
 *
 * <p>本类只依赖 payload 与纯 Java 集合，负责生成三方共用的 Power 域数据契约。</p>
 */
public final class PowerSnapshotBuilder {

    private static final String FLUX_NETWORK_TYPE = "FLUX_ENERGY";
    private static final String DEVICE_PREFIX = "flux.device.";

    private PowerSnapshotBuilder() {
    }

    /** Web / 服务端入口：直接从 Observer 当前状态构造 PowerSnapshot。 */
    public static PowerSnapshot fromObserver(ObserverBlockEntity observer) {
        if (observer == null) {
            return fromPayload(null);
        }
        List<ObserverDataPayload.BindingEntry> entries = new ArrayList<>();
        for (BoundEntry binding : observer.getBindings()) {
            BindingStats stats = observer.getStatsFor(binding.networkId());
            FluxNetworksIntegration.FluxSampleResult flux = observer.getFluxSampleResultFor(binding.networkId());
            long currentValue = flux == null ? stats.currentValue() : flux.totalEnergy();
            long capacity = flux == null ? stats.capacity() : flux.totalMaxEnergyStorage();
            long produced = flux == null ? stats.totalProduced() : flux.energyInput();
            long consumed = flux == null ? stats.totalConsumed() : flux.energyOutput();
            entries.add(new ObserverDataPayload.BindingEntry(
                    binding.networkType(),
                    binding.networkId(),
                    binding.targetBlockId(),
                    resolveDisplayName(binding, flux),
                    "resourceobserver:terminal/kpi_power",
                    currentValue,
                    capacity,
                    ObserverDataPayload.CellCapacityMetrics.unavailable(),
                    produced,
                    consumed,
                    ObserverDataPayload.KpiWindowStats.unavailable(),
                    buildFluxEnergyDeltas(flux),
                    observer.getDebugInfoFor(binding.networkId())
            ));
        }
        return fromPayload(new ObserverDataPayload(
                observer.getBlockPos(),
                observer.isBound(),
                false,
                ChartWindow.WEB_DETAIL_BUFFER_5M_1S,
                ChartScope.GLOBAL,
                "",
                List.of(),
                List.of(),
                "all",
                TableSortMode.NET,
                true,
                TableStatusFilter.ALL,
                0,
                List.of(),
                List.of(),
                entries,
                List.of()
        ));
    }
    public static PowerSnapshot fromPayload(ObserverDataPayload payload) {
        if (payload == null) {
            return new PowerSnapshot("", List.of(), List.of(), List.of(), List.of(),
                    new PowerSnapshot.OverloadInfoSnapshot(0.0, 0L),
                    List.of(), 0L, 0L, 0L, 0L, System.currentTimeMillis());
        }

        List<ObserverDataPayload.BindingEntry> fluxBindings = fluxBindings(payload);
        List<PowerSnapshot.DeviceSnapshot> devices = new ArrayList<>();
        long totalInputPerTick = 0L;
        long totalOutputPerTick = 0L;
        long totalStored = 0L;
        long totalCapacity = 0L;

        for (ObserverDataPayload.BindingEntry binding : fluxBindings) {
            long stored = binding.currentValue();
            long capacity = Math.max(1L, binding.capacity());
            long inputPerTick = computeEnergyPerTick(binding, true);
            long outputPerTick = computeEnergyPerTick(binding, false);

            totalInputPerTick = saturatingAdd(totalInputPerTick, inputPerTick);
            totalOutputPerTick = saturatingAdd(totalOutputPerTick, outputPerTick);
            totalStored = saturatingAdd(totalStored, stored);
            totalCapacity = saturatingAdd(totalCapacity, capacity);

            double usageRatio = Math.min(1.0, (double) stored / capacity);
            PowerSnapshot.LoadCategory category = PowerCategoryClassifier.inferCategory(binding.targetBlockId());
            String modName = PowerCategoryClassifier.extractModNamespace(binding.targetBlockId());
            PowerSnapshot.AlertLevel alert = PowerAlertEvaluator.evaluate(usageRatio, inputPerTick, outputPerTick);

            devices.add(new PowerSnapshot.DeviceSnapshot(
                    binding.networkId(),
                    binding.displayName(),
                    category,
                    modName,
                    outputPerTick > 0 ? outputPerTick : inputPerTick,
                    stored,
                    capacity,
                    usageRatio,
                    alert
            ));
        }

        devices.sort(Comparator.comparingLong(PowerSnapshot.DeviceSnapshot::energyPerTick).reversed());

        List<PowerSnapshot.LoadSegmentSnapshot> loadSegments = PowerLoadSegmenter.build(devices);
        List<PowerSnapshot.OverloadAlertSnapshot> overloadAlerts = PowerOverloadAlertBuilder.build(devices);
        List<PowerSnapshot.KpiSnapshot> kpis = PowerKpiBuilder.build(
                totalInputPerTick, totalOutputPerTick, totalStored, totalCapacity);
        PowerSnapshot.OverloadInfoSnapshot overloadInfo = PowerOverloadInfoCalculator.compute(
                totalInputPerTick, totalOutputPerTick);
        List<PowerSnapshot.ConsumerSnapshot> consumers = buildConsumers(fluxBindings, totalInputPerTick);

        String displayName = fluxBindings.isEmpty() ? "" : fluxBindings.get(0).displayName();
        return new PowerSnapshot(
                displayName,
                List.copyOf(devices),
                kpis,
                loadSegments,
                overloadAlerts,
                overloadInfo,
                consumers,
                totalInputPerTick,
                totalOutputPerTick,
                totalStored,
                totalCapacity,
                System.currentTimeMillis()
        );
    }

    private static String resolveDisplayName(
            BoundEntry binding,
            FluxNetworksIntegration.FluxSampleResult flux
    ) {
        if (flux != null && flux.networkName() != null && !flux.networkName().isBlank()) {
            return flux.networkName();
        }
        return binding.networkId();
    }

    private static List<ObserverDataPayload.ItemDeltaEntry> buildFluxEnergyDeltas(
            FluxNetworksIntegration.FluxSampleResult result
    ) {
        if (result == null) {
            return List.of();
        }
        List<ObserverDataPayload.ItemDeltaEntry> deltas = new ArrayList<>();
        deltas.add(metricDelta("flux.input_per_tick", "Input/t", result.energyInput()));
        deltas.add(metricDelta("flux.output_per_tick", "Output/t", result.energyOutput()));
        deltas.add(metricDelta("flux.energy_stored", "Energy Stored", result.totalEnergy()));
        deltas.add(metricDelta("flux.max_energy_storage", "Max Energy Storage", result.totalMaxEnergyStorage()));
        deltas.add(metricDelta("flux.plug_count", "Plug Count", result.plugCount()));
        deltas.add(metricDelta("flux.point_count", "Point Count", result.pointCount()));
        deltas.add(metricDelta("flux.storage_count", "Storage Count", result.storageCount()));
        deltas.add(metricDelta("flux.controller_count", "Controller Count", result.controllerCount()));
        int index = 0;
        for (FluxNetworksIntegration.FluxDeviceSnapshot device : result.devices()) {
            appendFluxDeviceDeltas(deltas, index++, device);
        }
        return List.copyOf(deltas);
    }

    private static ObserverDataPayload.ItemDeltaEntry metricDelta(String itemId, String displayName, long amount) {
        return new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                itemId,
                displayName,
                "flux_metrics",
                "resourceobserver:terminal/kpi_power",
                amount,
                0.0d,
                0.0d,
                0.0d
        );
    }

    private static void appendFluxDeviceDeltas(
            List<ObserverDataPayload.ItemDeltaEntry> deltas,
            int index,
            FluxNetworksIntegration.FluxDeviceSnapshot device
    ) {
        String type = device.deviceType() == null || device.deviceType().isBlank() ? "unknown" : device.deviceType();
        deltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                DEVICE_PREFIX + index + "." + type,
                device.customName(),
                "flux_devices",
                "resourceobserver:terminal/kpi_power",
                device.transferBuffer(),
                device.transferChange(),
                0.0d,
                0.0d
        ));
        if (device.externalRefs() == null) {
            return;
        }
        for (FluxNetworksIntegration.ExternalEnergyRef ref : device.externalRefs()) {
            String extId = ref.extId() == null || ref.extId().isBlank() ? ref.displayName() : ref.extId();
            deltas.add(metricDelta(DEVICE_PREFIX + index + ".ext." + extId + ".stored",
                    ref.displayName(), ref.stored()));
            deltas.add(metricDelta(DEVICE_PREFIX + index + ".ext." + extId + ".cap",
                    ref.displayName(), ref.capacity()));
            deltas.add(metricDelta(DEVICE_PREFIX + index + ".ext." + extId + ".maxAccept",
                    ref.displayName(), ref.maxAcceptPerTick()));
        }
    }

    /** 计算每 tick 输入或输出能量。优先读 itemDeltas 中的速率项，回退到 totals。 */
    public static long computeEnergyPerTick(ObserverDataPayload.BindingEntry binding, boolean isInput) {
        if (binding.itemDeltas() != null) {
            String key = isInput ? "flux.input_per_tick" : "flux.output_per_tick";
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                if (key.equals(item.itemId())) {
                    return Math.abs(item.amount());
                }
            }
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                if ("flux.energy_stored".equals(item.itemId())) {
                    if (isInput && item.delta() > 0) return Math.round(item.delta());
                    if (!isInput && item.delta() < 0) return Math.round(Math.abs(item.delta()));
                }
            }
        }
        return isInput ? binding.totalProduced() : binding.totalConsumed();
    }

    private static List<ObserverDataPayload.BindingEntry> fluxBindings(ObserverDataPayload payload) {
        List<ObserverDataPayload.BindingEntry> result = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if (FLUX_NETWORK_TYPE.equals(binding.networkType())) {
                result.add(binding);
            }
        }
        return result;
    }

    private static List<PowerSnapshot.ConsumerSnapshot> buildConsumers(
            List<ObserverDataPayload.BindingEntry> fluxBindings,
            long totalInputPerTick
    ) {
        Map<String, OutputDevicePartial> outputDevices = parseOutputDevices(fluxBindings);
        if (outputDevices.isEmpty()) {
            return List.of();
        }

        Map<String, long[]> uniqueDeviceMap = new LinkedHashMap<>();
        Map<String, String> deviceModMap = new LinkedHashMap<>();
        Map<String, String> deviceDisplayNameMap = new LinkedHashMap<>();
        for (OutputDevicePartial device : outputDevices.values()) {
            addUniqueConsumers(device, uniqueDeviceMap, deviceModMap, deviceDisplayNameMap);
        }
        if (uniqueDeviceMap.isEmpty()) {
            return List.of();
        }

        Map<String, long[]> consumerMap = new LinkedHashMap<>();
        Map<String, String> modNameMap = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : uniqueDeviceMap.entrySet()) {
            String dedupeKey = entry.getKey();
            String rawDisplayName = deviceDisplayNameMap.getOrDefault(dedupeKey, dedupeKey);
            String cleanName = stripCoordinates(rawDisplayName);
            consumerMap.computeIfAbsent(cleanName, k -> new long[2]);
            consumerMap.get(cleanName)[0] += entry.getValue()[0];
            consumerMap.get(cleanName)[1]++;
            modNameMap.putIfAbsent(cleanName, deviceModMap.getOrDefault(dedupeKey, ""));
        }

        long grandTotal = 0L;
        for (long[] value : consumerMap.values()) {
            grandTotal = saturatingAdd(grandTotal, value[0]);
        }

        List<PowerSnapshot.ConsumerSnapshot> result = new ArrayList<>();
        int paletteIndex = 0;
        for (Map.Entry<String, long[]> entry : consumerMap.entrySet()) {
            long consumption = entry.getValue()[0];
            double percentage = grandTotal > 0 ? (consumption * 100.0) / grandTotal : 0.0;
            double supplyRatio = totalInputPerTick > 0 ? (consumption * 100.0) / totalInputPerTick : 0.0;
            result.add(new PowerSnapshot.ConsumerSnapshot(
                    entry.getKey(),
                    modNameMap.getOrDefault(entry.getKey(), ""),
                    consumption,
                    percentage,
                    supplyRatio,
                    paletteIndex++,
                    (int) entry.getValue()[1]
            ));
        }
        result.sort(Comparator.comparingLong(PowerSnapshot.ConsumerSnapshot::consumptionPerTick).reversed());
        return List.copyOf(result);
    }

    private static Map<String, OutputDevicePartial> parseOutputDevices(
            List<ObserverDataPayload.BindingEntry> fluxBindings
    ) {
        Map<String, OutputDevicePartial> devices = new LinkedHashMap<>();
        for (ObserverDataPayload.BindingEntry binding : fluxBindings) {
            if (binding.itemDeltas() == null) {
                continue;
            }
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                parseOutputDeviceItem(binding, item, devices);
            }
        }
        return devices;
    }

    private static void parseOutputDeviceItem(
            ObserverDataPayload.BindingEntry binding,
            ObserverDataPayload.ItemDeltaEntry item,
            Map<String, OutputDevicePartial> devices
    ) {
        if (item.itemId() == null || !item.itemId().startsWith(DEVICE_PREFIX)) {
            return;
        }
        String suffix = item.itemId().substring(DEVICE_PREFIX.length());
        int dotPos = suffix.indexOf('.');
        if (dotPos < 0) {
            return;
        }
        String deviceKey = binding.networkId() + ":" + suffix.substring(0, dotPos);
        String tail = suffix.substring(dotPos + 1);
        ExtRefMetricToken extToken = parseExtToken(tail);
        if (extToken != null) {
            recordExternalRef(item, devices.computeIfAbsent(deviceKey, k -> new OutputDevicePartial()), extToken);
            return;
        }
        if ("point".equals(tail)) {
            OutputDevicePartial device = devices.computeIfAbsent(deviceKey, k -> new OutputDevicePartial());
            device.deviceName = item.displayName();
            device.transferRate = Math.round(item.delta());
        }
    }

    private static void recordExternalRef(
            ObserverDataPayload.ItemDeltaEntry item,
            OutputDevicePartial device,
            ExtRefMetricToken extToken
    ) {
        device.refMetricMap.computeIfAbsent(extToken.extId(), k -> new long[3]);
        if ("stored".equals(extToken.metric())) {
            String name = item.displayName() == null || item.displayName().isBlank()
                    ? extToken.extId()
                    : item.displayName();
            device.refNameMap.putIfAbsent(extToken.extId(), name);
        }
    }

    private static void addUniqueConsumers(
            OutputDevicePartial device,
            Map<String, long[]> uniqueDeviceMap,
            Map<String, String> deviceModMap,
            Map<String, String> deviceDisplayNameMap
    ) {
        long rate = Math.abs(device.transferRate);
        if (!device.refMetricMap.isEmpty()) {
            long perRef = rate > 0 ? rate / device.refMetricMap.size() : 0L;
            long remainder = rate > 0 ? rate - perRef * device.refMetricMap.size() : 0L;
            int index = 0;
            for (String extId : device.refMetricMap.keySet()) {
                String displayName = device.refNameMap.getOrDefault(extId, extId);
                String dedupeKey = extId == null || extId.isBlank() ? displayName : extId;
                long share = perRef + (index++ == 0 ? remainder : 0L);
                uniqueDeviceMap.computeIfAbsent(dedupeKey, k -> new long[1])[0] += share;
                deviceDisplayNameMap.putIfAbsent(dedupeKey, displayName);
                deviceModMap.putIfAbsent(dedupeKey, extractModFromRefName(displayName));
            }
            return;
        }
        if (rate > 0) {
            String name = device.deviceName == null || device.deviceName.isBlank() ? "Unknown" : device.deviceName;
            uniqueDeviceMap.computeIfAbsent(name, k -> new long[1])[0] += rate;
            deviceDisplayNameMap.putIfAbsent(name, name);
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
        String metric = payload.substring(split + 1);
        if (!"stored".equals(metric) && !"cap".equals(metric) && !"maxAccept".equals(metric)) {
            return null;
        }
        return new ExtRefMetricToken(payload.substring(0, split), metric);
    }

    private static String extractModFromRefName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        int pipeIdx = rawName.indexOf('|');
        if (pipeIdx <= 0) return "";
        String regId = rawName.substring(0, pipeIdx);
        int colonIdx = regId.indexOf(':');
        return colonIdx > 0 ? regId.substring(0, colonIdx) : regId;
    }

    private static String stripCoordinates(String rawName) {
        if (rawName == null || rawName.isBlank()) return "Unknown";
        int pipeIdx = rawName.indexOf('|');
        String cleaned = pipeIdx >= 0 ? rawName.substring(pipeIdx + 1) : rawName;
        int atIdx = cleaned.lastIndexOf(" @ ");
        return atIdx > 0 ? cleaned.substring(0, atIdx).trim() : cleaned.trim();
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }

    private record ExtRefMetricToken(String extId, String metric) {
    }

    private static final class OutputDevicePartial {
        private String deviceName;
        private long transferRate;
        private final Map<String, long[]> refMetricMap = new LinkedHashMap<>();
        private final Map<String, String> refNameMap = new LinkedHashMap<>();
    }
}


