package com.yuyinrl.resourceobserver.client.ui;

import java.util.List;

/**
 * 存储网络页面视图模型 —— 将服务端原始数据转换为客户端 GUI 可直接渲染的结构。
 * 作为 ObserverDataPayload 和 Storage Network 页面渲染之间的桥梁层。
 *
 * @param nodes           所有存储节点（对应每个绑定）
 * @param selectedNodeId  当前选中的节点 ID（null 或空 = 全部节点）
 * @param alertFilterActive 是否仅显示异常物品
 * @param kpiCards        三个 KPI 卡片数据
 * @param items           经过筛选的物品行列表
 * @param usageSegments   分类使用率分段数据
 * @param totalItemCount  当前筛选后的物品总数
 * @param criticalItemCount 紧急物品数量
 * @param totalUsedRatio  总使用率
 */
public record StorageNetworkViewModel(
        List<NodeEntry> nodes,
        String selectedNodeId,
        boolean alertFilterActive,
        List<StorageKpi> kpiCards,
        List<ItemRow> items,
        List<UsageSegment> usageSegments,
        int totalItemCount,
        int criticalItemCount,
        double totalUsedRatio
) {
    /** 库存警报级别 */
    public enum AlertLevel {
        /** 库存充足（绿色） */
        GREEN,
        /** 库存不足 30 秒消耗量（黄色） */
        YELLOW,
        /** 库存极低或已满（红色） */
        RED
    }

    /**
     * 存储节点 —— 对应一个网络绑定。
     * @param nodeId        节点唯一 ID（使用 networkId）
     * @param displayName   显示名称
     * @param networkType   网络类型（AE2_ITEMS / FLUX_ENERGY）
     * @param iconSprite    图标精灵路径
     * @param itemCount     该节点中的物品种类数
     * @param capacityRatio 容量填充比例（0.0 ~ 1.0）
     * @param selected      是否为当前选中节点
     * @param usedFormatted 已用容量格式化文本（如 "852.0k"）
     * @param totalFormatted 总容量格式化文本（如 "1000.0k"）
     * @param statusLabel   状态标签（Healthy / Alert）
     * @param statusAlert   是否为警报状态
     */
    public record NodeEntry(
            String nodeId,
            String displayName,
            String networkType,
            String iconSprite,
            int itemCount,
            double capacityRatio,
            boolean selected,
            String usedFormatted,
            String totalFormatted,
            String statusLabel,
            boolean statusAlert
    ) {
    }

    /**
     * 存储 KPI 卡片。
     * @param label  标签翻译键
     * @param value  格式化后的数值
     * @param status 状态（决定颜色）
     */
    public record StorageKpi(
            String label,
            String value,
            OverviewViewModel.Status status
    ) {
    }

    /**
     * 物品行 —— 存储网络页面中的一行物品数据。
     * @param itemId          物品 ID
     * @param displayName     显示名称
     * @param iconSprite      图标精灵路径
     * @param localAmount     所选节点内的库存量（全部节点时 = globalAmount）
     * @param globalAmount    所有节点的总库存量
     * @param delta           变化量（正=生产，负=消耗）
     * @param alertLevel      警报级别
     * @param groupKey        所属分组键
     * @param burnRatePerMin  每分钟消耗速率（正数表示消耗）
     * @param estimatedBufferText 预估缓冲时间文本（如 "39m 8s"）
     * @param bufferRatio     缓冲比例（0.0 ~ 1.0，用于进度条）
     */
    public record ItemRow(
            String itemId,
            String displayName,
            String iconSprite,
            long localAmount,
            long globalAmount,
            long delta,
            AlertLevel alertLevel,
            String groupKey,
            long burnRatePerMin,
            String estimatedBufferText,
            double bufferRatio
    ) {
    }

    /**
     * 使用率分段 —— 用于使用率摘要条形图。
     * @param groupKey    分组键
     * @param displayName 分组显示名称
     * @param percentage  占比百分比（0 ~ 100）
     * @param color       颜色（ARGB）
     */
    public record UsageSegment(
            String groupKey,
            String displayName,
            double percentage,
            int color
    ) {
    }
}

