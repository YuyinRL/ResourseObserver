package com.yuyinrl.resourceobserver.service.snapshot.overview;

import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;

import java.util.List;

/**
 * Overview 领域快照 —— 服务端权威的总览数据契约。
 *
 * <p>该 record 是 F1 大重构引入的核心契约层：服务端在
 * {@code OverviewSnapshotBuilder.build(...)} 中一次性计算所有派生字段，
 * 三类消费者（ModernUI / 新 Vanilla / Web）都读它而不再各自重复塑形。
 *
 * <p>设计原则：
 * <ul>
 *     <li>数值保持原始类型（long / double），由消费者按本地化策略渲染</li>
 *     <li>状态枚举（{@link KpiStatus}）即业务结论，三方共用</li>
 *     <li>i18n：承载翻译键 + 参数，不承载已渲染字符串</li>
 *     <li>所有列表使用 {@code List.copyOf} 包装，保证不可变</li>
 * </ul>
 *
 * <p>本 record 仅定义数据形状；构建逻辑在 {@code OverviewSnapshotBuilder}
 * （F1.A.6 引入），格式化工具在 {@link com.yuyinrl.resourceobserver.service.snapshot.format}。
 */
public record OverviewSnapshot(
        HeaderInfo header,
        List<KpiSnapshot> kpis,
        List<KpiDetailSnapshot> kpiDetails,
        StorageDetailSnapshot storageDetail,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String chartScopeItemId,
        List<ChartPointSnapshot> chartSeries,
        List<ChartPointSnapshot> energyChartSeries,
        UiStateSnapshot uiState,
        List<WatchlistItemSnapshot> watchlistItems,
        List<TableGroupSnapshot> tableGroups
) {
    public OverviewSnapshot {
        kpis = kpis == null ? List.of() : List.copyOf(kpis);
        kpiDetails = kpiDetails == null ? List.of() : List.copyOf(kpiDetails);
        chartSeries = chartSeries == null ? List.of() : List.copyOf(chartSeries);
        energyChartSeries = energyChartSeries == null ? List.of() : List.copyOf(energyChartSeries);
        watchlistItems = watchlistItems == null ? List.of() : List.copyOf(watchlistItems);
        tableGroups = tableGroups == null ? List.of() : List.copyOf(tableGroups);
    }

    /** 顶部头信息：标题、副标题、连接状态翻译键 */
    public record HeaderInfo(
            String titleKey,
            String subtitleKey,
            String linkStatusKey,
            int bindingCount,
            boolean hasAe2Binding,
            boolean hasFluxBinding
    ) {
    }

    /** KPI 类型枚举 —— 与 OverviewViewModel.KpiType 对齐 */
    public enum KpiType {
        PRODUCTION,
        CONSUMPTION,
        STORAGE,
        BALANCE
    }

    /** KPI 状态：决定卡片颜色，三方共用 */
    public enum KpiStatus {
        POSITIVE,
        WARNING,
        NEGATIVE,
        NEUTRAL
    }

    /**
     * KPI 指标卡服务端契约。
     *
     * @param type        KPI 类型（生产/消耗/库存/平衡）
     * @param labelKey    标签翻译键
     * @param valueRaw    数值原始量（long），消费者负责紧凑/精确格式化
     * @param valueKind   数值语义提示（per-minute / percent / score 等），辅助消费者选择格式器
     * @param trendKey    趋势行翻译键，含参数（如 "+5%"）；不可用时为 {@code null}
     * @param trendArg    趋势翻译键的参数（已格式化的百分比字符串等），可为 {@code null}
     * @param status      业务状态结论
     * @param iconSprite  可选：图标精灵 ID（如某些 KPI 用 vanilla sprite）
     */
    public record KpiSnapshot(
            KpiType type,
            String labelKey,
            double valueRaw,
            ValueKind valueKind,
            String trendKey,
            String trendArg,
            KpiStatus status,
            String iconSprite
    ) {
    }

    /** KPI 数值语义 —— 消费者根据它选择格式策略 */
    public enum ValueKind {
        /** 每分钟流量（条目/分钟），紧凑格式 1.5K */
        FLOW_PER_MINUTE,
        /** 百分比（0-100） */
        PERCENT,
        /** 0-100 平衡分数 */
        BALANCE_SCORE,
        /** 计数（整数） */
        COUNT
    }

    /** KPI 详细分解（物品通道 + 流体通道，供详细弹窗展示） */
    public record KpiDetailSnapshot(
            KpiType type,
            String titleKey,
            String hintKey,
            KpiStatus status,
            ChannelDetail itemChannel,
            ChannelDetail fluidChannel
    ) {
    }

    /** 单通道详情（如"物品生产通道"或"流体消耗通道"） */
    public record ChannelDetail(
            String labelKey,
            double recentRate,
            double previousRate,
            double trendPercent,
            boolean trendAvailable,
            boolean available
    ) {
    }

    /**
     * 存储面板汇总。AE2 物品/流体磁盘 + 外部存储四通道。
     *
     * @param hasAe2Binding    是否存在任何 AE2 绑定
     * @param diskReliable     磁盘容量数据是否可信
     * @param externalReliable 外部存储数据是否可信
     * @param hintKey          提示文本翻译键（如"未绑定 AE2"）
     */
    public record StorageDetailSnapshot(
            boolean hasAe2Binding,
            boolean diskReliable,
            boolean externalReliable,
            String hintKey,
            StorageChannelSnapshot diskItem,
            StorageChannelSnapshot diskFluid,
            StorageChannelSnapshot externalItem,
            StorageChannelSnapshot externalFluid
    ) {
        public static StorageDetailSnapshot unavailable(String hintKey) {
            StorageChannelSnapshot na = StorageChannelSnapshot.na();
            return new StorageDetailSnapshot(false, false, false, hintKey, na, na, na, na);
        }
    }

    /** 单一存储通道：原始字节数 + 类型计数 + 最大值 */
    public record StorageChannelSnapshot(
            String labelKey,
            long usedBytes,
            long totalBytes,
            long usedTypes,
            long totalTypes,
            boolean available
    ) {
        public static StorageChannelSnapshot na() {
            return new StorageChannelSnapshot("screen.resourceobserver.overview.storage.na", 0L, 0L, 0L, 0L, false);
        }
    }

    /** 图表点：与 ObserverDataPayload.ChartPoint 同构，独立定义以脱离 Payload */
    public record ChartPointSnapshot(
            int slotIndex,
            long bucket,
            double production,
            double consumption,
            double net,
            double stock,
            boolean hasFlow,
            boolean hasStock,
            int sampleCount
    ) {
    }

    /** 关注列表条目 */
    public record WatchlistItemSnapshot(
            String itemId,
            String displayName,
            long netPerMinute,
            long stock,
            String iconSprite
    ) {
    }

    /** 分组选项 —— 表格分组下拉选项 */
    public record GroupOptionSnapshot(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    /**
     * 表格分组（一个分组标题下的行列表）。
     * displayName 是已解析好的展示字符串（系统组由服务端做 i18n 后塞入；用户自定义组直接用其名字）。
     */
    public record TableGroupSnapshot(
            String key,
            String displayName,
            List<TableRowSnapshot> rows
    ) {
        public TableGroupSnapshot {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** 表格行：物品级数据 */
    public record TableRowSnapshot(
            String itemId,
            String displayName,
            String groupKey,
            long production,
            long consumption,
            long net,
            long stock,
            boolean critical,
            boolean starred,
            String iconSprite
    ) {
    }

    /** UI 状态：当前过滤、排序、分组键 */
    public record UiStateSnapshot(
            String groupFilterKey,
            List<GroupOptionSnapshot> groups,
            TableSortMode sortMode,
            boolean sortDesc,
            TableStatusFilter statusFilter,
            int watchlistLimit
    ) {
        public UiStateSnapshot {
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }
}
