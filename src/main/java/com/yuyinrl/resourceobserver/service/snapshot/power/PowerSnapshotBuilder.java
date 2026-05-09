package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Power Snapshot 装配主入口。F3.A 范围：
 * <ul>
 *     <li>设备列表（Flux 节点） + KPI + 负载分段 + 过载告警 + 余量信息</li>
 *     <li>{@code consumers} 暂留 {@link List#of()}：F3.B 迁移调试设备解析时一并补齐</li>
 *     <li>不依赖 MC 类型；复用 {@code ObserverDataPayload} 直接读取</li>
 * </ul>
 */
public final class PowerSnapshotBuilder {

    private static final String FLUX_NETWORK_TYPE = "FLUX_ENERGY";

    private PowerSnapshotBuilder() {
    }

    public static PowerSnapshot fromPayload(ObserverDataPayload payload) {
        if (payload == null) {
            return new PowerSnapshot("", List.of(), List.of(), List.of(), List.of(),
                    new PowerSnapshot.OverloadInfoSnapshot(0.0, 0L),
                    List.of(), 0L, 0L, 0L, 0L, System.currentTimeMillis());
        }

        List<ObserverDataPayload.BindingEntry> fluxBindings = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            if (FLUX_NETWORK_TYPE.equals(binding.networkType())) {
                fluxBindings.add(binding);
            }
        }

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

        String displayName = fluxBindings.isEmpty() ? "" : fluxBindings.get(0).displayName();

        return new PowerSnapshot(
                displayName,
                List.copyOf(devices),
                kpis,
                loadSegments,
                overloadAlerts,
                overloadInfo,
                List.of(), // F3.B 补齐 consumers
                totalInputPerTick,
                totalOutputPerTick,
                totalStored,
                totalCapacity,
                System.currentTimeMillis()
        );
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

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }
}
