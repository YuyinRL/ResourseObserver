package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;

import java.util.List;

/**
 * Power 域服务端权威快照 —— 由 {@link PowerSnapshotBuilder#fromPayload} 装配。
 *
 * <p>F3.A 设计目标：把 690 行 {@code PowerNetworkViewModelMapper} 的数据塑形抽离成
 * 三方共用（ModernUI / Vanilla V2 / Web）的 record 契约。
 *
 * <p>本快照仅承载用户面向数据；调试视图（Debug terminal 的内部接口元数据）保持
 * 在 ViewModel 层，留给 F3.B 决定是否拆分。
 */
public record PowerSnapshot(
        String displayName,
        List<DeviceSnapshot> devices,
        List<KpiSnapshot> kpiCards,
        List<LoadSegmentSnapshot> loadSegments,
        List<OverloadAlertSnapshot> overloadAlerts,
        OverloadInfoSnapshot overloadInfo,
        List<ConsumerSnapshot> consumers,
        long totalInputPerTick,
        long totalOutputPerTick,
        long totalStored,
        long totalCapacity,
        long timestampMs
) {

    /** Power 设备级告警（沿用三档：NORMAL / WARNING / CRITICAL） */
    public enum AlertLevel {
        NORMAL,
        WARNING,
        CRITICAL
    }

    /** 设备分类槽位（决定 LoadSegment 颜色与翻译键） */
    public enum LoadCategory {
        MINING,
        ASSEMBLY,
        LOGISTICS,
        OTHER
    }

    /** 设备项 —— 一台 Flux 节点 / 网络入口 */
    public record DeviceSnapshot(
            String nodeId,
            String displayName,
            LoadCategory category,
            String modName,
            long energyPerTick,
            long storedEnergy,
            long maxCapacity,
            double usageRatio,
            AlertLevel alertLevel
    ) {
    }

    /** Power 域 KPI 卡片（复用 Overview 的 KpiStatus 枚举） */
    public record KpiSnapshot(
            String labelKey,
            String value,
            OverviewSnapshot.KpiStatus status
    ) {
    }

    /** 负载分段（按设备分类聚合） */
    public record LoadSegmentSnapshot(
            LoadCategory category,
            String displayNameKey,
            double percentage
    ) {
    }

    /** 过载告警 —— 单设备 */
    public record OverloadAlertSnapshot(
            String nodeId,
            String displayName,
            AlertLevel alertLevel,
            double throughputLoss,
            String descriptionKey,
            List<Object> descriptionArgs
    ) {
    }

    /** 网络余量信息 */
    public record OverloadInfoSnapshot(
            double headroomPercent,
            long reservePerTick
    ) {
    }

    /** 用电器项 —— 输出接口检测到的相邻方块聚合 */
    public record ConsumerSnapshot(
            String deviceName,
            String modName,
            long consumptionPerTick,
            double percentage,
            double supplyRatio,
            int paletteIndex,
            int count
    ) {
    }
}
