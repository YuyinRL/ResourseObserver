package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;

import java.util.List;

/**
 * Storage KPI 卡片构建器 —— 计算"总物品 / 类型数 / 填充率"三个 KPI 与对应业务状态。
 *
 * <p>F2.A 从 {@code StorageNetworkViewModelMapper.buildKpiCards} 抽出来的纯逻辑：
 * <ul>
 *     <li>总物品 / 类型数：&gt; 0 → {@code POSITIVE}，否则 {@code NEUTRAL}</li>
 *     <li>填充率：≥ 90% → {@code WARNING}；&gt;= 0 → {@code POSITIVE}；负数 → {@code NEUTRAL}</li>
 *     <li>数值通过 {@link SnapshotFormatters#formatCompact(long)} / {@link SnapshotFormatters#formatPercent(double)} 渲染</li>
 * </ul>
 */
public final class StorageKpiBuilder {

    /** 总物品 KPI 翻译键 */
    public static final String KEY_TOTAL_ITEMS = "screen.resourceobserver.storage.kpi.total_items";
    /** 总类型数 KPI 翻译键 */
    public static final String KEY_TOTAL_TYPES = "screen.resourceobserver.storage.kpi.total_types";
    /** 填充率 KPI 翻译键 */
    public static final String KEY_FILL_RATE = "screen.resourceobserver.storage.kpi.fill_rate";

    /** 填充率告警阈值 */
    public static final double FILL_RATE_WARNING = 0.9;

    private StorageKpiBuilder() {
    }

    /**
     * 构建三个 KPI 卡片。
     *
     * @param totalAmount 总库存（已 saturating add）
     * @param totalTypes  总类型数（items.size）
     * @param fillRate    填充率（0.0 ~ 1.0；负数表示不可用）
     */
    public static List<StorageSnapshot.KpiSnapshot> build(long totalAmount, int totalTypes, double fillRate) {
        return List.of(
                new StorageSnapshot.KpiSnapshot(
                        KEY_TOTAL_ITEMS,
                        SnapshotFormatters.formatCompact(totalAmount),
                        totalAmount > 0
                                ? OverviewSnapshot.KpiStatus.POSITIVE
                                : OverviewSnapshot.KpiStatus.NEUTRAL
                ),
                new StorageSnapshot.KpiSnapshot(
                        KEY_TOTAL_TYPES,
                        String.valueOf(totalTypes),
                        totalTypes > 0
                                ? OverviewSnapshot.KpiStatus.POSITIVE
                                : OverviewSnapshot.KpiStatus.NEUTRAL
                ),
                new StorageSnapshot.KpiSnapshot(
                        KEY_FILL_RATE,
                        SnapshotFormatters.formatPercent(fillRate),
                        classifyFillRate(fillRate)
                )
        );
    }

    /** 填充率 → 业务状态分类（独立暴露便于复用与单测） */
    public static OverviewSnapshot.KpiStatus classifyFillRate(double fillRate) {
        if (fillRate >= FILL_RATE_WARNING) {
            return OverviewSnapshot.KpiStatus.WARNING;
        }
        if (fillRate >= 0.0) {
            return OverviewSnapshot.KpiStatus.POSITIVE;
        }
        return OverviewSnapshot.KpiStatus.NEUTRAL;
    }
}
