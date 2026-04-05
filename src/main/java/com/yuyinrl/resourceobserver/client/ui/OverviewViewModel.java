package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 总览仪表板视图模型 —— 将服务端原始数据转换为客户端 GUI 可直接渲染的结构。
 * 作为 Payload 数据和 Screen 渲染之间的桥梁层（ViewModel 模式）。
 *
 * @param headerTitle       标题文本
 * @param headerSubtitle    副标题文本
 * @param linkStatus        连接状态描述
 * @param kpis              四个 KPI 指标卡数据
 * @param chartWindow       当前图表时间窗口
 * @param chartScopeItemId  图表单物品作用域 ID
 * @param chartSeries       图表数据点列表（物品数据）
 * @param energyChartSeries 电量图表数据点列表（FLUX_ENERGY 数据）
 * @param uiState           UI 状态（筛选、排序、分组设置）
 * @param watchlistItems    关注列表物品数据
 * @param tableGroups       表格分组（每组含行列表）
 */
public record OverviewViewModel(
        String headerTitle,
        String headerSubtitle,
        String linkStatus,
        List<KpiMetric> kpis,
        ChartWindow chartWindow,
        String chartScopeItemId,
        List<FlowPoint> chartSeries,
        List<FlowPoint> energyChartSeries,
        UiState uiState,
        List<WatchlistItem> watchlistItems,
        List<TableGroup> tableGroups,
        List<KpiDetail> kpiDetails,
        StorageDetail storageDetail
) {
    public enum KpiType {
        PRODUCTION,
        CONSUMPTION,
        STORAGE,
        BALANCE
    }

    /**
     * KPI 指标卡数据。
     * @param label      指标标签（如"生产"、"消耗"）
     * @param value      格式化后的数值字符串
     * @param trend      趋势描述（如"+5%"）
     * @param status     状态枚举（决定颜色）
     * @param iconSprite 图标精灵路径
     */
    public record KpiMetric(
            KpiType type,
            String label,
            String value,
            String trend,
            Status status,
            String iconSprite
    ) {
    }

    public KpiDetail kpiDetailFor(KpiType type) {
        if (type == null || kpiDetails == null || kpiDetails.isEmpty()) {
            return null;
        }
        for (KpiDetail detail : kpiDetails) {
            if (detail.type() == type) {
                return detail;
            }
        }
        return null;
    }

    public record KpiDetail(
            KpiType type,
            String title,
            String hintText,
            Status status,
            KpiDetailChannel itemChannel,
            KpiDetailChannel fluidChannel
    ) {
    }

    public record KpiDetailChannel(
            String label,
            String recentText,
            String previousText,
            String trendText,
            boolean available
    ) {
    }

    public record StorageDetail(
            boolean hasAe2Binding,
            boolean diskReliable,
            boolean externalReliable,
            String hintText,
            StorageChannel diskItem,
            StorageChannel diskFluid,
            StorageChannel externalItem,
            StorageChannel externalFluid
    ) {
        public static StorageDetail unavailable(String hintText) {
            StorageChannel na = StorageChannel.na();
            return new StorageDetail(false, false, false, hintText, na, na, na, na);
        }
    }

    public record StorageChannel(
            String label,
            String usageText,
            String typesText,
            String usageDetailText,
            String typesDetailText,
            boolean available
    ) {
        public static StorageChannel na() {
            return new StorageChannel("N/A", "N/A", "N/A", "N/A", "N/A", false);
        }
    }

    /**
     * 图表数据点 —— 与 ObserverDataPayload.ChartPoint 结构对齐。
     */
    public record FlowPoint(
            int slotIndex,
            double production,
            double consumption,
            double net,
            double stock,
            boolean hasFlow,
            boolean hasStock
    ) {
    }

    /**
     * 关注列表物品数据。
     * @param itemId       物品注册 ID
     * @param displayName  显示名称
     * @param netPerMinute 每分钟净变化量
     * @param stock        当前库存
     * @param starred      是否已关注
     * @param iconSprite   图标精灵路径
     */
    public record WatchlistItem(
            String itemId,
            String displayName,
            long netPerMinute,
            long stock,
            boolean starred,
            String iconSprite
    ) {
    }

    /** 分组选项 —— 用于分组下拉菜单 */
    public record GroupOption(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    /** 表格分组 —— 一个分组标题下的行列表 */
    public record TableGroup(
            String key,
            String title,
            List<TableRow> rows
    ) {
    }

    /**
     * 表格行数据 —— 物品流通表中的一行。
     * @param itemId      物品 ID
     * @param displayName 显示名称
     * @param groupKey    所属分组键
     * @param production  生产量
     * @param consumption 消耗量
     * @param net         净变化量
     * @param stock       库存
     * @param critical    是否处于严重亏损状态
     * @param starred     是否已关注
     * @param iconSprite  图标精灵路径
     */
    public record TableRow(
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

    /**
     * UI 状态 —— 当前界面的筛选、排序、分组设置。
     */
    public record UiState(
            String groupFilterKey,
            List<GroupOption> groups,
            TableSortMode sortMode,
            boolean sortDesc,
            TableStatusFilter statusFilter,
            int watchlistLimit
    ) {
        /** 根据分组键查找分组显示名称 */
        public String groupNameByKey(String key) {
            if (key == null) {
                return "";
            }
            if (PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(key)) {
                return Component.translatable("screen.resourceobserver.overview.filter.group.all").getString();
            }
            for (GroupOption group : groups) {
                if (key.equals(group.key())) {
                    return group.displayName();
                }
            }
            return key;
        }
    }

    /** 状态枚举 —— 决定 KPI 卡片的颜色 */
    public enum Status {
        POSITIVE,  // 正面（绿色/青色）
        WARNING,   // 警告（黄色/琥珀色）
        NEGATIVE,  // 负面（红色/玫瑰色）
        NEUTRAL    // 中性（灰色）
    }
}
