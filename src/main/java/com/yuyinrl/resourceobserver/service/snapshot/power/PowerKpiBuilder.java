package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;

import java.util.List;

/**
 * Power KPI 卡片构建器 —— 三张卡：总输入 / 总输出 / 储能填充率。
 *
 * <p>状态判定：
 * <ul>
 *     <li>总输入：&gt;0 → POSITIVE，否则 NEUTRAL</li>
 *     <li>总输出：&gt; 总输入 → WARNING；&gt;0 → POSITIVE；else NEUTRAL</li>
 *     <li>储能填充率：≤10% → NEGATIVE；≥90% → WARNING；else POSITIVE</li>
 * </ul>
 */
public final class PowerKpiBuilder {

    public static final String KEY_TOTAL_INPUT = "screen.resourceobserver.power.kpi.total_input";
    public static final String KEY_TOTAL_OUTPUT = "screen.resourceobserver.power.kpi.total_output";
    public static final String KEY_STORED = "screen.resourceobserver.power.kpi.stored_energy";

    public static final double STORED_LOW = 0.10;
    public static final double STORED_HIGH = 0.90;

    private PowerKpiBuilder() {
    }

    public static List<PowerSnapshot.KpiSnapshot> build(
            long totalInput, long totalOutput, long totalStored, long totalCapacity
    ) {
        OverviewSnapshot.KpiStatus inputStatus = totalInput > 0
                ? OverviewSnapshot.KpiStatus.POSITIVE
                : OverviewSnapshot.KpiStatus.NEUTRAL;

        OverviewSnapshot.KpiStatus outputStatus = (totalOutput > totalInput)
                ? OverviewSnapshot.KpiStatus.WARNING
                : (totalOutput > 0
                        ? OverviewSnapshot.KpiStatus.POSITIVE
                        : OverviewSnapshot.KpiStatus.NEUTRAL);

        double storedRatio = totalCapacity > 0 ? (double) totalStored / totalCapacity : 0.0;
        OverviewSnapshot.KpiStatus storedStatus = classifyStored(storedRatio);

        return List.of(
                new PowerSnapshot.KpiSnapshot(KEY_TOTAL_INPUT,
                        SnapshotFormatters.formatCompact(totalInput) + " FE/t", inputStatus),
                new PowerSnapshot.KpiSnapshot(KEY_TOTAL_OUTPUT,
                        SnapshotFormatters.formatCompact(totalOutput) + " FE/t", outputStatus),
                new PowerSnapshot.KpiSnapshot(KEY_STORED,
                        SnapshotFormatters.formatPercent(Math.max(0.0, storedRatio)), storedStatus)
        );
    }

    /** 独立暴露的储能比例分类（便于复用 / 单测） */
    public static OverviewSnapshot.KpiStatus classifyStored(double storedRatio) {
        if (storedRatio <= STORED_LOW) return OverviewSnapshot.KpiStatus.NEGATIVE;
        if (storedRatio >= STORED_HIGH) return OverviewSnapshot.KpiStatus.WARNING;
        return OverviewSnapshot.KpiStatus.POSITIVE;
    }
}
