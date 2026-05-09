package com.yuyinrl.resourceobserver.service.snapshot.overview;

/**
 * Overview KPI 纯逻辑计算器 —— 趋势百分比、平衡得分、状态分类。
 *
 * <p>F1.A.3 从 {@code OverviewViewModelMapper} 迁出来的 5 个 KPI 决策方法，
 * 集中至此作为 {@link OverviewSnapshot} 的纯逻辑层。
 *
 * <p>设计约束：
 * <ul>
 *     <li>纯函数、无副作用、无 MC / NeoForge 依赖（保证可单测）</li>
 *     <li>输入 NaN / Infinite 时返回稳定默认值（0 或 NEUTRAL）</li>
 *     <li>状态阈值与 Mapper 现有副本逐一对齐，迁移期间行为不变</li>
 * </ul>
 *
 * <h3>阈值速览</h3>
 * <ul>
 *     <li>生产 / 消费趋势变化判定：±5%</li>
 *     <li>平衡得分：≥ 8 POSITIVE；> -8 NEUTRAL；≥ -25 WARNING；其余 NEGATIVE</li>
 *     <li>平衡得分上下限：[-100, 100]</li>
 * </ul>
 */
public final class KpiCalculator {

    /** 生产/消费类 KPI 视为"显著变化"的趋势百分比阈值（绝对值） */
    public static final double TREND_CHANGE_THRESHOLD = 5.0d;

    /** 平衡分进入 POSITIVE 的下限 */
    public static final double BALANCE_POSITIVE_THRESHOLD = 8.0d;

    /** 平衡分跌至此即视为开始 WARNING（更低则进 NEGATIVE） */
    public static final double BALANCE_WARNING_LOWER_BOUND = -25.0d;

    /** 平衡分从 NEUTRAL 退至 WARNING 的边界 */
    public static final double BALANCE_NEUTRAL_LOWER_BOUND = -8.0d;

    private KpiCalculator() {
    }

    /**
     * 计算两个时段之间的相对变化（百分比）。
     *
     * @param recent   当前/最近一窗口值
     * @param previous 上一窗口值
     * @return ((recent - previous) / previous) * 100；previous &le; 0 或非有限输入返回 0
     */
    public static double percentChange(double recent, double previous) {
        if (!Double.isFinite(recent) || !Double.isFinite(previous) || previous <= 0.0d) {
            return 0.0d;
        }
        return ((recent - previous) / previous) * 100.0d;
    }

    /**
     * 计算生产/消费的平衡得分，归一化到 [-100, 100]。
     *
     * @param productionRate  生产速率
     * @param consumptionRate 消费速率
     * @return ((production - consumption) / (production + consumption)) * 100；
     *         非有限输入或两者之和 &le; 0 时返回 0
     */
    public static double balanceScore(double productionRate, double consumptionRate) {
        if (!Double.isFinite(productionRate) || !Double.isFinite(consumptionRate)) {
            return 0.0d;
        }
        double sum = productionRate + consumptionRate;
        if (sum <= 0.0d) {
            return 0.0d;
        }
        double score = ((productionRate - consumptionRate) / sum) * 100.0d;
        if (score > 100.0d) {
            return 100.0d;
        }
        if (score < -100.0d) {
            return -100.0d;
        }
        return score;
    }

    /**
     * 生产 KPI 状态分类：上升 → POSITIVE；下降 → WARNING（生产下降是预警）；其他 NEUTRAL。
     */
    public static OverviewSnapshot.KpiStatus classifyProduction(boolean trendAvailable, double trendPercent) {
        if (!trendAvailable) {
            return OverviewSnapshot.KpiStatus.NEUTRAL;
        }
        if (trendPercent >= TREND_CHANGE_THRESHOLD) {
            return OverviewSnapshot.KpiStatus.POSITIVE;
        }
        if (trendPercent <= -TREND_CHANGE_THRESHOLD) {
            return OverviewSnapshot.KpiStatus.WARNING;
        }
        return OverviewSnapshot.KpiStatus.NEUTRAL;
    }

    /**
     * 消费 KPI 状态分类：上升 → WARNING（消费上升是预警）；下降 → POSITIVE；其他 NEUTRAL。
     */
    public static OverviewSnapshot.KpiStatus classifyConsumption(boolean trendAvailable, double trendPercent) {
        if (!trendAvailable) {
            return OverviewSnapshot.KpiStatus.NEUTRAL;
        }
        if (trendPercent >= TREND_CHANGE_THRESHOLD) {
            return OverviewSnapshot.KpiStatus.WARNING;
        }
        if (trendPercent <= -TREND_CHANGE_THRESHOLD) {
            return OverviewSnapshot.KpiStatus.POSITIVE;
        }
        return OverviewSnapshot.KpiStatus.NEUTRAL;
    }

    /**
     * 平衡得分分类：
     * <ul>
     *     <li>不可用 → NEUTRAL</li>
     *     <li>得分 ≥ {@value #BALANCE_POSITIVE_THRESHOLD} → POSITIVE</li>
     *     <li>{@value #BALANCE_NEUTRAL_LOWER_BOUND} &lt; 得分 &lt; {@value #BALANCE_POSITIVE_THRESHOLD} → NEUTRAL</li>
     *     <li>得分 ≥ {@value #BALANCE_WARNING_LOWER_BOUND} → WARNING</li>
     *     <li>否则 → NEGATIVE</li>
     * </ul>
     */
    public static OverviewSnapshot.KpiStatus classifyBalance(double score, boolean available) {
        if (!available) {
            return OverviewSnapshot.KpiStatus.NEUTRAL;
        }
        if (score >= BALANCE_POSITIVE_THRESHOLD) {
            return OverviewSnapshot.KpiStatus.POSITIVE;
        }
        if (score > BALANCE_NEUTRAL_LOWER_BOUND) {
            return OverviewSnapshot.KpiStatus.NEUTRAL;
        }
        if (score >= BALANCE_WARNING_LOWER_BOUND) {
            return OverviewSnapshot.KpiStatus.WARNING;
        }
        return OverviewSnapshot.KpiStatus.NEGATIVE;
    }
}
