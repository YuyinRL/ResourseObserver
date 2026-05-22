package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;

import java.util.List;

/**
 * Storage Network 领域快照 —— 服务端权威的存储网络数据契约。
 *
 * <p>F2.A 引入：把 {@code StorageNetworkViewModelMapper}（814 行）的塑形逻辑沉淀成
 * 三方共用（ModernUI / 新 Vanilla / Web）的不可变契约。
 *
 * <p>设计原则与 {@link OverviewSnapshot} 一致：
 * <ul>
 *     <li>数值保持原始类型；i18n 翻译键 + 参数承载（非渲染字符串）</li>
 *     <li>状态枚举即业务结论（{@link AlertLevel} / {@link OverviewSnapshot.KpiStatus}）</li>
 *     <li>分组颜色用 {@link GroupColorSlot}，避免 Snapshot 直接依赖 UI 主题色值</li>
 *     <li>所有列表 {@code List.copyOf} 包装保持不可变</li>
 * </ul>
 *
 * <p>构建逻辑在 {@code StorageSnapshotBuilder}；纯逻辑工具在同包下的
 * {@code StorageAlertEvaluator} / {@code StorageBufferSmoother} /
 * {@code StorageUsageSegmenter} / {@code StorageKpiBuilder}。
 */
public record StorageSnapshot(
        List<NodeSnapshot> nodes,
        String selectedNodeId,
        boolean alertFilterActive,
        String searchQuery,
        List<KpiSnapshot> kpis,
        List<ItemRowSnapshot> items,
        List<UsageSegmentSnapshot> usageSegments,
        int totalItemCount,
        int criticalItemCount,
        double totalUsedRatio
) {
    public StorageSnapshot {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        kpis = kpis == null ? List.of() : List.copyOf(kpis);
        items = items == null ? List.of() : List.copyOf(items);
        usageSegments = usageSegments == null ? List.of() : List.copyOf(usageSegments);
    }

    /** 物品库存告警级别 */
    public enum AlertLevel {
        /** 库存充足 */
        GREEN,
        /** 库存不足 30 秒消耗 */
        YELLOW,
        /** 库存为 0 或节点容量 ≥ 95% */
        RED
    }

    /** 分组颜色槽位 —— 把 UI 颜色决策从 Snapshot 中剥离 */
    public enum GroupColorSlot {
        /** 原料类（cyan） */
        RAW,
        /** 中间产物（amber） */
        INTERMEDIATE,
        /** 成品（emerald） */
        FINISHED,
        /** 其它（blue 默认槽） */
        OTHER
    }

    /** 存储节点 —— 对应一个网络绑定。 */
    public record NodeSnapshot(
            String nodeId,
            String displayName,
            String networkType,
            String iconSprite,
            int itemCount,
            double capacityRatio,
            boolean selected,
            String usedFormatted,
            String totalFormatted,
            String statusKey,
            boolean statusAlert,
            String coordinatesText
    ) {
    }

    /** 存储 KPI 卡片 —— labelKey 翻译键 + 已格式化的 value + 业务状态 */
    public record KpiSnapshot(
            String labelKey,
            String value,
            OverviewSnapshot.KpiStatus status
    ) {
    }

    /** 物品行 —— 列表展示与告警计算的单元 */
    public record ItemRowSnapshot(
            String itemId,
            String displayName,
            String iconSprite,
            long localAmount,
            long globalAmount,
            double delta,
            AlertLevel alertLevel,
            String groupKey,
            double burnRatePerMin,
            String estimatedBufferText,
            double bufferRatio
    ) {
    }

    /** 使用率分段 —— 摘要条形图的单元；颜色由 {@link GroupColorSlot} 决定 */
    public record UsageSegmentSnapshot(
            String groupKey,
            String displayNameKey,
            String fallbackName,
            double percentage,
            GroupColorSlot colorSlot
    ) {
    }
}
