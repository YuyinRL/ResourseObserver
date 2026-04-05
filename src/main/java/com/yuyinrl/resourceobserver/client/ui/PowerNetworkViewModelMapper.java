package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 电力网络视图模型映射器 —— 将 ObserverDataPayload 转换为 PowerNetworkViewModel。
 * <p>
 * 映射逻辑：
 * 1. 筛选所有 FLUX_ENERGY 类型绑定
 * 2. 从每个绑定提取能量数据（currentValue/capacity/produced/consumed）
 * 3. 根据目标方块类型推断设备分类（Mining / Assembly / Logistics / Other）
 * 4. 计算负载分布分段
 * 5. 检测过载并生成警报
 * 6. 构建 KPI 卡片
 */
public final class PowerNetworkViewModelMapper {
    private PowerNetworkViewModelMapper() {
    }

    /** 容量使用率 ≥ 95% → 红色过载 */
    private static final double OVERLOAD_CRITICAL_THRESHOLD = 0.95;
    /** 容量使用率 ≥ 80% → 黄色警告 */
    private static final double OVERLOAD_WARNING_THRESHOLD = 0.80;

    /**
     * 将 Payload 数据转换为 PowerNetworkViewModel。
     *
     * @param payload 服务端数据载荷
     */
    public static PowerNetworkViewModel fromPayload(ObserverDataPayload payload) {
        List<ObserverDataPayload.BindingEntry> bindings = payload.bindings();

        // ========== 筛选 Flux 能量绑定 ==========
        List<ObserverDataPayload.BindingEntry> fluxBindings = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : bindings) {
            if ("FLUX_ENERGY".equals(binding.networkType())) {
                fluxBindings.add(binding);
            }
        }

        // ========== 构建设备列表 ==========
        List<PowerNetworkViewModel.DeviceEntry> devices = new ArrayList<>();
        long totalInputPerTick = 0L;
        long totalOutputPerTick = 0L;
        long totalStored = 0L;
        long totalCapacity = 0L;

        // Debug 用：从 itemDeltas 提取原始 Flux 指标
        long debugInputRate = 0L;
        long debugOutputRate = 0L;
        long debugTotalBuffer = 0L;
        int debugPlugCount = 0;
        int debugPointCount = 0;
        int debugStorageCount = 0;
        int debugControllerCount = 0;
        // 使用 Map 缓存每个设备的基础 + 外部能量数据，key = "<idx>"
        Map<String, PowerNetworkViewModel.DeviceDebugEntry> debugDeviceBaseMap = new LinkedHashMap<>();
        Map<String, long[]> debugDeviceExtMap = new LinkedHashMap<>();
        List<String> debugInputDeviceKeys = new ArrayList<>();
        List<String> debugOutputDeviceKeys = new ArrayList<>();

        for (ObserverDataPayload.BindingEntry binding : fluxBindings) {
            long stored = binding.currentValue();
            long capacity = Math.max(1L, binding.capacity());
            long produced = binding.totalProduced();
            long consumed = binding.totalConsumed();

            // 估算每 tick 输入/输出：使用累计值的差异近似
            // 对于 Flux Networks，totalProduced = 累计输入，totalConsumed = 累计输出
            // 我们用 delta 近似每 tick 值（如有 itemDeltas 中的能量条目则取之）
            long inputPerTick = computeEnergyPerTick(binding, true);
            long outputPerTick = computeEnergyPerTick(binding, false);

            totalInputPerTick = saturatingAdd(totalInputPerTick, inputPerTick);
            totalOutputPerTick = saturatingAdd(totalOutputPerTick, outputPerTick);
            totalStored = saturatingAdd(totalStored, stored);
            totalCapacity = saturatingAdd(totalCapacity, capacity);

            // ========== Debug：提取 Flux 原始指标和逐接口数据 ==========
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
                        default -> {
                            // 逐设备条目：flux.device.<idx>.<type> 或 flux.device.<idx>.ext_stored/ext_cap
                            if (item.itemId().startsWith("flux.device.")) {
                                String suffix = item.itemId().substring("flux.device.".length());
                                // suffix 格式: "<idx>.<type>", 例如 "0.plug" 或 "0.ext_stored"
                                int dotPos = suffix.indexOf('.');
                                String idxStr = dotPos >= 0 ? suffix.substring(0, dotPos) : suffix;
                                String tail = dotPos >= 0 ? suffix.substring(dotPos + 1) : "unknown";

                                if ("ext_stored".equals(tail)) {
                                    debugDeviceExtMap.computeIfAbsent(idxStr, k -> new long[2])[0] = item.amount();
                                } else if ("ext_cap".equals(tail)) {
                                    debugDeviceExtMap.computeIfAbsent(idxStr, k -> new long[2])[1] = item.amount();
                                } else {
                                    // 基础设备条目
                                    String devType = tail;
                                    PowerNetworkViewModel.DeviceDebugEntry entry =
                                            new PowerNetworkViewModel.DeviceDebugEntry(
                                                    item.displayName(),
                                                    devType,
                                                    item.delta(),       // transferChange (FE/t)
                                                    item.amount(),      // transferBuffer (FE)
                                                    0L,                 // 占位，后续合并
                                                    0L                  // 占位，后续合并
                                            );
                                    debugDeviceBaseMap.put(idxStr, entry);
                                    if ("plug".equals(devType)) {
                                        debugInputDeviceKeys.add(idxStr);
                                    } else if ("point".equals(devType)) {
                                        debugOutputDeviceKeys.add(idxStr);
                                    }
                                }
                            }
                        }
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

        // 按能量使用量降序排序
        devices.sort(Comparator.comparingLong(PowerNetworkViewModel.DeviceEntry::energyPerTick).reversed());

        // ========== 构建负载分布 ==========
        List<PowerNetworkViewModel.LoadSegment> loadSegments = buildLoadSegments(devices);

        // ========== 构建过载警报 ==========
        List<PowerNetworkViewModel.OverloadAlert> overloadAlerts = buildOverloadAlerts(devices);

        // ========== 构建 KPI 卡片 ==========
        List<PowerNetworkViewModel.PowerKpi> kpiCards = buildKpiCards(
                totalInputPerTick, totalOutputPerTick, totalStored, totalCapacity
        );

        // ========== 构建过载风险摘要 ==========
        double headroomPercent = totalInputPerTick > 0
                ? Math.max(0.0, (totalInputPerTick - totalOutputPerTick) * 100.0 / totalInputPerTick)
                : 0.0;
        long reservePerTick = Math.max(0, totalInputPerTick - totalOutputPerTick);
        PowerNetworkViewModel.OverloadInfo overloadInfo = new PowerNetworkViewModel.OverloadInfo(
                headroomPercent, reservePerTick
        );

        long totalDemandFEt = totalOutputPerTick;

        // ========== 构建 Debug 快照（合并设备基础数据与外部能量数据）==========
        List<PowerNetworkViewModel.DeviceDebugEntry> debugInputDevices = new ArrayList<>();
        for (String key : debugInputDeviceKeys) {
            debugInputDevices.add(mergeDeviceWithExt(debugDeviceBaseMap.get(key), debugDeviceExtMap.get(key)));
        }
        List<PowerNetworkViewModel.DeviceDebugEntry> debugOutputDevices = new ArrayList<>();
        for (String key : debugOutputDeviceKeys) {
            debugOutputDevices.add(mergeDeviceWithExt(debugDeviceBaseMap.get(key), debugDeviceExtMap.get(key)));
        }

        PowerNetworkViewModel.DebugSnapshot debugSnapshot = new PowerNetworkViewModel.DebugSnapshot(
                debugInputRate,
                debugOutputRate,
                debugTotalBuffer,
                debugPlugCount,
                debugPointCount,
                debugStorageCount,
                debugControllerCount,
                List.copyOf(debugInputDevices),
                List.copyOf(debugOutputDevices)
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
                totalDemandFEt,
                debugSnapshot
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从绑定数据估算能量每 tick 输入或输出。
     * 优先使用 itemDeltas 中的能量条目 delta，否则回退到 totalProduced/totalConsumed。
     */
    private static long computeEnergyPerTick(ObserverDataPayload.BindingEntry binding, boolean isInput) {
        // 检查 itemDeltas 中是否有 flux.input_per_tick / flux.output_per_tick 键
        if (binding.itemDeltas() != null) {
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                String key = isInput ? "flux.input_per_tick" : "flux.output_per_tick";
                if (key.equals(item.itemId())) {
                    return Math.abs(item.amount());
                }
            }
            // 也检查 energy_stored 相关
            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                if ("flux.energy_stored".equals(item.itemId())) {
                    // delta 正值 = 输入 > 输出，负值 = 输出 > 输入
                    if (isInput && item.delta() > 0) {
                        return item.delta();
                    }
                    if (!isInput && item.delta() < 0) {
                        return Math.abs(item.delta());
                    }
                }
            }
        }
        // 回退：使用累计值（不够精确，但总比没有好）
        if (isInput) {
            return binding.totalProduced();
        }
        return binding.totalConsumed();
    }

    /** 根据目标方块 ID 推断设备分类 */
    private static String inferCategory(String targetBlockId) {
        if (targetBlockId == null || targetBlockId.isBlank()) {
            return "other";
        }
        String lower = targetBlockId.toLowerCase(Locale.ROOT);
        // 挖矿/采集设备
        if (lower.contains("miner") || lower.contains("quarry") || lower.contains("drill")
                || lower.contains("pump") || lower.contains("excavat")) {
            return "mining";
        }
        // 组装/加工设备
        if (lower.contains("assembl") || lower.contains("craft") || lower.contains("inscriber")
                || lower.contains("press") || lower.contains("furnace") || lower.contains("smelter")
                || lower.contains("grinder") || lower.contains("crusher") || lower.contains("machine")) {
            return "assembly";
        }
        // 物流设备
        if (lower.contains("bus") || lower.contains("interface") || lower.contains("import")
                || lower.contains("export") || lower.contains("pipe") || lower.contains("duct")
                || lower.contains("conveyor") || lower.contains("router")) {
            return "logistics";
        }
        return "other";
    }

    /** 计算设备警报级别 */
    private static PowerNetworkViewModel.AlertLevel computeAlertLevel(
            double usageRatio, long inputPerTick, long outputPerTick
    ) {
        // 输出远超输入 = 能量净消耗，容量也高 → 危险
        if (usageRatio <= 0.05 && outputPerTick > 0) {
            return PowerNetworkViewModel.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_CRITICAL_THRESHOLD) {
            return PowerNetworkViewModel.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_WARNING_THRESHOLD) {
            return PowerNetworkViewModel.AlertLevel.WARNING;
        }
        // 输出远超输入 → 能量正在快速消耗
        if (outputPerTick > 0 && inputPerTick > 0 && outputPerTick > inputPerTick * 2) {
            return PowerNetworkViewModel.AlertLevel.WARNING;
        }
        return PowerNetworkViewModel.AlertLevel.NORMAL;
    }

    /** 构建负载分布分段 */
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

    /** 构建过载警报列表 */
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
                throughputLoss = (device.usageRatio() - OVERLOAD_CRITICAL_THRESHOLD) / (1.0 - OVERLOAD_CRITICAL_THRESHOLD) * 50.0;
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

    /** 构建三个 KPI 卡片 */
    private static List<PowerNetworkViewModel.PowerKpi> buildKpiCards(
            long totalInput, long totalOutput, long totalStored, long totalCapacity
    ) {
        // 总输入/t
        OverviewViewModel.Status inputStatus = totalInput > 0
                ? OverviewViewModel.Status.POSITIVE
                : OverviewViewModel.Status.NEUTRAL;

        // 总输出/t
        OverviewViewModel.Status outputStatus = totalOutput > totalInput
                ? OverviewViewModel.Status.WARNING
                : (totalOutput > 0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.NEUTRAL);

        // 储能状态
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

    /** 合并设备基础条目和外部能量数据 */
    private static PowerNetworkViewModel.DeviceDebugEntry mergeDeviceWithExt(
            PowerNetworkViewModel.DeviceDebugEntry base, long[] ext
    ) {
        if (base == null) {
            return new PowerNetworkViewModel.DeviceDebugEntry("?", "unknown", 0L, 0L, 0L, 0L);
        }
        long extStored = ext != null ? ext[0] : 0L;
        long extCap = ext != null ? ext[1] : 0L;
        return new PowerNetworkViewModel.DeviceDebugEntry(
                base.deviceName(), base.deviceType(),
                base.transferRate(), base.bufferStored(),
                extStored, extCap
        );
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
}

