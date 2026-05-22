package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;

import java.util.List;

/**
 * Power KPI 卡片构建器 —— 四张卡：有效输入 / 有效输出 / 储能 / 外储排除速率。
 *
 * <p>G7 对齐：标签键改用 effective_input / effective_output（默认无外储排除时与 total 等价）；
 * 储能格式改为 "X / Y FE"（与 ModernUI buildEffectiveKpis 一致）；
 * 第 4 张卡 external_excluded 由服务端固定输出 "0 FE/t" / NEUTRAL，
 * 用户在 ModernUI 选中外储组后客户端会覆盖。</p>
 *
 * <p>状态判定：
 * <ul>
 *     <li>有效输入：&gt;0 → POSITIVE，否则 NEUTRAL</li>
 *     <li>有效输出：&gt; 有效输入 → WARNING；&gt;0 → POSITIVE；else NEUTRAL</li>
 *     <li>储能填充率：≤10% → NEGATIVE；≥90% → WARNING；else POSITIVE</li>
 *     <li>外储排除速率：服务端默认 NEUTRAL（= 0，无排除）</li>
 * </ul>
 */
public final class PowerKpiBuilder {

    public static final String KEY_EFFECTIVE_INPUT = "screen.resourceobserver.power.kpi.effective_input";
    public static final String KEY_EFFECTIVE_OUTPUT = "screen.resourceobserver.power.kpi.effective_output";
    public static final String KEY_STORED = "screen.resourceobserver.power.kpi.stored_energy";
    public static final String KEY_EXTERNAL_EXCLUDED = "screen.resourceobserver.power.kpi.external_excluded";

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

        // 储能格式："已储 / 总容量 FE"（与 ModernUI buildEffectiveKpis 对齐）
        String storedValue = SnapshotFormatters.formatCompact(totalStored)
                + " / " + SnapshotFormatters.formatCompact(totalCapacity) + " FE";

        return List.of(
                new PowerSnapshot.KpiSnapshot(KEY_EFFECTIVE_INPUT,
                        SnapshotFormatters.formatCompact(totalInput) + " FE/t", inputStatus),
                new PowerSnapshot.KpiSnapshot(KEY_EFFECTIVE_OUTPUT,
                        SnapshotFormatters.formatCompact(totalOutput) + " FE/t", outputStatus),
                new PowerSnapshot.KpiSnapshot(KEY_STORED, storedValue, storedStatus),
                new PowerSnapshot.KpiSnapshot(KEY_EXTERNAL_EXCLUDED,
                        "0 FE/t", OverviewSnapshot.KpiStatus.NEUTRAL)
        );
    }

    /** 独立暴露的储能比例分类（便于复用 / 单测） */
    public static OverviewSnapshot.KpiStatus classifyStored(double storedRatio) {
        if (storedRatio <= STORED_LOW) return OverviewSnapshot.KpiStatus.NEGATIVE;
        if (storedRatio >= STORED_HIGH) return OverviewSnapshot.KpiStatus.WARNING;
        return OverviewSnapshot.KpiStatus.POSITIVE;
    }
}
