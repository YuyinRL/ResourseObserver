package com.yuyinrl.resourceobserver.service.snapshot.power;

/**
 * Power 设备告警决策器 —— 复刻原 mapper 的 {@code computeAlertLevel}。
 *
 * <p>规则：
 * <ul>
 *     <li>{@code usageRatio ≤ 5%} 且 {@code outputPerTick > 0} → CRITICAL（电网空载但仍在输出）</li>
 *     <li>{@code usageRatio ≥ 95%} → CRITICAL（容量逼近上限）</li>
 *     <li>{@code usageRatio ≥ 80%} → WARNING</li>
 *     <li>{@code outputPerTick > 2 × inputPerTick} 且双方均 > 0 → WARNING（消耗远超供给）</li>
 *     <li>否则 NORMAL</li>
 * </ul>
 */
public final class PowerAlertEvaluator {

    public static final double OVERLOAD_CRITICAL_THRESHOLD = 0.95;
    public static final double OVERLOAD_WARNING_THRESHOLD = 0.80;
    public static final double DRAINED_RATIO_THRESHOLD = 0.05;
    public static final double OUTPUT_OVER_INPUT_FACTOR = 2.0;

    private PowerAlertEvaluator() {
    }

    public static PowerSnapshot.AlertLevel evaluate(double usageRatio, long inputPerTick, long outputPerTick) {
        if (usageRatio <= DRAINED_RATIO_THRESHOLD && outputPerTick > 0) {
            return PowerSnapshot.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_CRITICAL_THRESHOLD) {
            return PowerSnapshot.AlertLevel.CRITICAL;
        }
        if (usageRatio >= OVERLOAD_WARNING_THRESHOLD) {
            return PowerSnapshot.AlertLevel.WARNING;
        }
        if (outputPerTick > 0 && inputPerTick > 0 && outputPerTick > inputPerTick * OUTPUT_OVER_INPUT_FACTOR) {
            return PowerSnapshot.AlertLevel.WARNING;
        }
        return PowerSnapshot.AlertLevel.NORMAL;
    }

    /**
     * 计算单设备的吞吐损失估算（百分比 0~100）。
     * <ul>
     *     <li>容量逼近上限：在 95%~100% 区间线性映射到 0%~50% 损失</li>
     *     <li>容量耗尽（stored ≤ 0）但仍在输出：100% 损失</li>
     *     <li>其他：0%</li>
     * </ul>
     */
    public static double computeThroughputLoss(double usageRatio, long storedEnergy, long energyPerTick) {
        if (usageRatio >= OVERLOAD_CRITICAL_THRESHOLD) {
            double loss = (usageRatio - OVERLOAD_CRITICAL_THRESHOLD)
                    / (1.0 - OVERLOAD_CRITICAL_THRESHOLD) * 50.0;
            return Math.max(0.0, Math.min(100.0, loss));
        }
        if (storedEnergy <= 0 && energyPerTick > 0) {
            return 100.0;
        }
        return 0.0;
    }
}
