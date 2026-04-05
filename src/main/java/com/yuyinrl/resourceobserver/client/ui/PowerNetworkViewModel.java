package com.yuyinrl.resourceobserver.client.ui;

import java.util.List;

/**
 * 电力网络页面视图模型 —— 将服务端 Flux Networks 数据转换为客户端 GUI 可直接渲染的结构。
 * 作为 ObserverDataPayload 和 Power Network 页面渲染之间的桥梁层。
 *
 * @param devices          设备/生产线条目列表
 * @param kpiCards         三个 KPI 卡片（总输入/t、总输出/t、储能）
 * @param loadSegments     负载分布分段数据（Mining / Assembly / Logistics）
 * @param overloadAlerts   过载警报列表
 * @param totalInputPerTick  总输入 FE/t
 * @param totalOutputPerTick 总输出 FE/t
 * @param totalStored      总储能
 * @param totalCapacity    总容量
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
        DebugSnapshot debugSnapshot
) {
    /** 过载警报级别 */
    public enum AlertLevel {
        /** 正常运行（绿色） */
        NORMAL,
        /** 接近容量上限（黄色） */
        WARNING,
        /** 过载或严重不足（红色） */
        CRITICAL
    }

    /**
     * 设备/生产线条目 —— 电力网络中一个能量节点的数据。
     * @param nodeId         节点 ID（使用 networkId）
     * @param displayName    显示名称
     * @param category       设备分类（Mining / Assembly / Logistics / Other）
     * @param energyPerTick  每 tick 能量使用量（FE/t）
     * @param storedEnergy   当前储能
     * @param maxCapacity    最大容量
     * @param usageRatio     使用比例（0.0 ~ 1.0）
     * @param alertLevel     警报级别
     */
    public record DeviceEntry(
            String nodeId,
            String displayName,
            String category,
            long energyPerTick,
            long storedEnergy,
            long maxCapacity,
            double usageRatio,
            AlertLevel alertLevel
    ) {
    }

    /**
     * 电力 KPI 卡片。
     * @param label  标签翻译键
     * @param value  格式化后的数值
     * @param status 状态（决定颜色）
     */
    public record PowerKpi(
            String label,
            String value,
            OverviewViewModel.Status status
    ) {
    }

    /**
     * 负载分布分段 —— 用于环形/条形图。
     * @param category    设备类别键
     * @param displayName 类别显示名
     * @param percentage  占比百分比（0 ~ 100）
     * @param color       颜色（ARGB）
     */
    public record LoadSegment(
            String category,
            String displayName,
            double percentage,
            int color
    ) {
    }

    /**
     * 过载警报 —— 超容量设备/生产线的警报信息。
     * @param nodeId          节点 ID
     * @param displayName     显示名称
     * @param alertLevel      警报级别
     * @param throughputLoss  预估吞吐量损失百分比
     * @param description     警报描述文本
     */
    public record OverloadAlert(
            String nodeId,
            String displayName,
            AlertLevel alertLevel,
            double throughputLoss,
            String description
    ) {
    }

    /**
     * 过载风险摘要 —— 用于 OverloadAlertRenderer 卡片。
     * @param headroomPercent  距离过载的余量百分比
     * @param reservePerTick   每 tick 的发电盈余（FE/t）
     */
    public record OverloadInfo(
            double headroomPercent,
            long reservePerTick
    ) {
    }

    /**
     * 电力网络 Debug 快照 —— 从 Flux Networks API 实时读取的原始值。
     * 每次数据刷新时自动更新，用于 Debug 面板显示。
     *
     * @param inputRate         当前网络总输入速率（FE/t），来自所有 Plug（输入接口）的合计
     * @param outputRate        当前网络总输出速率（FE/t），来自所有 Point（输出接口）的合计
     * @param totalBufferCapacity 网络总缓冲容量（FE），即所有储能方块容量之和
     * @param plugCount         Plug（输入接口）数量
     * @param pointCount        Point（输出接口）数量
     * @param storageCount      Storage（储能方块）数量
     * @param controllerCount   Controller（控制器）数量
     * @param inputDevices      每个 Plug 接口的详细传输数据
     * @param outputDevices     每个 Point 接口的详细传输数据
     */
    public record DebugSnapshot(
            long inputRate,
            long outputRate,
            long totalBufferCapacity,
            int plugCount,
            int pointCount,
            int storageCount,
            int controllerCount,
            List<DeviceDebugEntry> inputDevices,
            List<DeviceDebugEntry> outputDevices
    ) {
        /** 返回一个空的 Debug 快照（无 Flux 数据时的安全默认值） */
        public static DebugSnapshot empty() {
            return new DebugSnapshot(0L, 0L, 0L, 0, 0, 0, 0, List.of(), List.of());
        }
    }

    /**
     * 单个 Flux 接口的 Debug 条目。
     *
     * @param deviceName             设备名称（自定义名称或 "TYPE @ pos"）
     * @param deviceType             设备类型（plug / point / storage / controller）
     * @param transferRate           当前每 tick 传输速率（FE/t）
     * @param bufferStored           当前缓冲区储能量（FE）
     * @param externalEnergyStored   外部容器当前储能（FE），0 表示无外部容器
     * @param externalEnergyCapacity 外部容器最大容量（FE），0 表示无外部容器
     */
    public record DeviceDebugEntry(
            String deviceName,
            String deviceType,
            long transferRate,
            long bufferStored,
            long externalEnergyStored,
            long externalEnergyCapacity
    ) {
    }
}
