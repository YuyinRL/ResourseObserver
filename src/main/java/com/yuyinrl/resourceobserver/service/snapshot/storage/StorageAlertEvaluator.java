package com.yuyinrl.resourceobserver.service.snapshot.storage;

/**
 * 存储告警决策器 —— 把 amount / consumptionRate / capacityRatio 映射到 {@link StorageSnapshot.AlertLevel}。
 *
 * <p>F2.A 从 {@code StorageNetworkViewModelMapper.computeAlertLevel/Global} 抽出来的纯逻辑：
 * <ul>
 *     <li>RED：库存 ≤ 0；或任一容量比 ≥ 95%</li>
 *     <li>YELLOW：消耗速率 &gt; 0 且 amount × 60 &lt; 30 × consumptionRate（不足 30 秒缓冲）</li>
 *     <li>GREEN：其它</li>
 * </ul>
 */
public final class StorageAlertEvaluator {

    /** 不足 30 秒缓冲 → 黄色阈值 */
    public static final long ALERT_YELLOW_SECONDS = 30L;

    /** 节点容量临界阈值 → 红色 */
    public static final double CAPACITY_CRITICAL_RATIO = 0.95;

    private StorageAlertEvaluator() {
    }

    /**
     * 单节点视图的告警评估。
     *
     * @param amount           当前库存量
     * @param consumptionRate  每分钟消耗速率（≥0）
     * @param capacityRatio    节点容量填充比例（0.0 ~ 1.0）
     */
    public static StorageSnapshot.AlertLevel evaluate(long amount, double consumptionRate, double capacityRatio) {
        if (amount <= 0L) {
            return StorageSnapshot.AlertLevel.RED;
        }
        if (capacityRatio >= CAPACITY_CRITICAL_RATIO) {
            return StorageSnapshot.AlertLevel.RED;
        }
        if (consumptionRate > 0.0
                && amount * 60.0 < ALERT_YELLOW_SECONDS * consumptionRate) {
            return StorageSnapshot.AlertLevel.YELLOW;
        }
        return StorageSnapshot.AlertLevel.GREEN;
    }

    /**
     * 全节点聚合视图的告警评估。
     *
     * @param amount             所有节点的总库存
     * @param consumptionRate    所有节点的总消耗速率
     * @param maxCapacityRatio   所有节点中的最大容量比例（任一节点 ≥ 95% 即触发 RED）
     */
    public static StorageSnapshot.AlertLevel evaluateGlobal(long amount, double consumptionRate, double maxCapacityRatio) {
        // 全局视图复用单节点逻辑：传入"最大容量比"即可保留"任一节点容量满 → RED"语义。
        return evaluate(amount, consumptionRate, maxCapacityRatio);
    }
}
