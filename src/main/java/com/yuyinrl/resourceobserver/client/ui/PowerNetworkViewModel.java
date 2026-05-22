package com.yuyinrl.resourceobserver.client.ui;

import java.util.List;

/**
 * 电力网络页面视图模型。
 */
public record PowerNetworkViewModel(
        List<DeviceEntry> devices,
        List<PowerKpi> kpiCards,
        List<LoadSegment> loadSegments,
        List<OverloadAlert> overloadAlerts,
        OverloadInfo overloadInfo,
        long totalInputPerTick,
        long totalOutputPerTick,
        long totalStored,
        long totalCapacity,
        long totalDemandFEt,
        DebugSnapshot debugSnapshot,
        List<ConsumerEntry> consumers
) {
    public enum AlertLevel {
        NORMAL,
        WARNING,
        CRITICAL
    }

    public record DeviceEntry(
            String nodeId,
            String displayName,
            String category,
            String modName,
            long energyPerTick,
            long storedEnergy,
            long maxCapacity,
            double usageRatio,
            AlertLevel alertLevel
    ) {
    }

    public record PowerKpi(
            String label,
            String value,
            OverviewViewModel.Status status
    ) {
    }

    public record LoadSegment(
            String category,
            String displayName,
            double percentage,
            int color
    ) {
    }

    public record OverloadAlert(
            String nodeId,
            String displayName,
            AlertLevel alertLevel,
            double throughputLoss,
            String description
    ) {
    }

    public record OverloadInfo(
            double headroomPercent,
            long reservePerTick
    ) {
    }

    public record DebugSnapshot(
            long inputRate,
            long outputRate,
            long totalBufferCapacity,
            int plugCount,
            int pointCount,
            int storageCount,
            int controllerCount,
            List<DeviceDebugEntry> inputDevices,
            List<DeviceDebugEntry> outputDevices,
            List<ExternalGroup> externalGroups
    ) {
        public static DebugSnapshot empty() {
            return new DebugSnapshot(0L, 0L, 0L, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }

    public record DeviceDebugEntry(
            String interfaceKey,
            String deviceName,
            String deviceType,
            long transferRate,
            long bufferStored,
            long externalEnergyStored,
            long externalEnergyCapacity,
            List<ExternalRef> externalRefs
    ) {
    }

    public record ExternalRef(
            String extId,
            String displayName,
            long stored,
            long capacity,
            long maxAcceptPerTick
    ) {
    }

    public record ExternalGroup(
            String extId,
            String displayName,
            long stored,
            long capacity,
            List<String> interfaceKeys
    ) {
    }

    /**
     * 从输出接口（Point）的相邻设备检测到的用电器。
     * 按设备名称分组，汇总传输速率。
     */
    public record ConsumerEntry(
            String deviceName,
            String modName,
            long consumptionPerTick,
            double percentage,
            double supplyRatio,
            int color,
            int count
    ) {
    }
}
