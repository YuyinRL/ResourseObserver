package com.yuyinrl.resourceobserver.service.snapshot.storage;

import java.util.Map;

/**
 * 缓冲倒计时双层平滑器 —— Layer 1 速率 EMA + Layer 2 倒计时锁定。
 *
 * <p>F2.A 从 {@code StorageNetworkViewModelMapper.smoothRateAndUpdateAnchor} 原样迁出，
 * 行为与原实现完全一致以保证 ModernUI / 新 Vanilla 显示一致；后续若要改算法只在此一处。
 *
 * <p>状态数组 {@code double[4]}：
 * <pre>
 *   [0] = smoothedRatePerMin  — EMA 平滑后的消耗速率
 *   [1] = displayAnchorSec    — 倒计时锚点秒数
 *   [2] = anchorTimeMs        — 锚点时间戳
 *   [3] = lastAmount          — 上次库存量（用于检测突变）
 * </pre>
 *
 * <p>纯逻辑设计：时间戳 / 时钟通过 {@link Clock} 接口注入，便于单测确定性。
 */
public final class StorageBufferSmoother {

    /** 速率 EMA 平滑因子 —— α = 0.05，对应 ~10–20 秒半衰期 */
    public static final double RATE_EMA_ALPHA = 0.05;

    /** 锁定低阈值 —— 偏差 &lt; 2% 视为噪声，不更新锚点 */
    public static final double LOCK_THRESHOLD_LOW = 0.02;

    /** 锁定高阈值 —— 偏差 &gt; 25% 视为突变，直接 snap */
    public static final double LOCK_THRESHOLD_HIGH = 0.25;

    /** 中等偏差混合权重（新值占比） */
    public static final double BLEND_ALPHA = 0.15;

    private StorageBufferSmoother() {
    }

    /** 时钟抽象 —— 测试可注入固定时间 */
    @FunctionalInterface
    public interface Clock {
        long currentTimeMillis();

        Clock SYSTEM = System::currentTimeMillis;
    }

    /**
     * 默认时钟版本 —— 与原 mapper 行为一致。
     *
     * @return 当前应显示的锚点秒数
     */
    public static double smoothRateAndUpdateAnchor(
            String itemId, double rawRate, long amount, Map<String, double[]> bufferEma) {
        return smoothRateAndUpdateAnchor(itemId, rawRate, amount, bufferEma, Clock.SYSTEM);
    }

    /**
     * 注入时钟版本 —— 测试入口。
     */
    public static double smoothRateAndUpdateAnchor(
            String itemId, double rawRate, long amount, Map<String, double[]> bufferEma, Clock clock) {

        double now = clock.currentTimeMillis();
        double[] prev = bufferEma.get(itemId);

        double smoothedRate;
        if (prev == null || prev.length < 4 || prev[0] <= 0) {
            smoothedRate = rawRate;
        } else {
            smoothedRate = RATE_EMA_ALPHA * rawRate + (1.0 - RATE_EMA_ALPHA) * prev[0];
        }

        double instantBuffer = (double) amount * 60.0 / smoothedRate;

        double anchorSec;
        if (prev == null || prev.length < 4 || prev[1] <= 0) {
            anchorSec = instantBuffer;
        } else {
            double elapsed = (now - prev[2]) / 1000.0;
            double projected = prev[1] - elapsed;
            if (projected < 0) projected = 0;

            double deviation = Math.abs(instantBuffer - projected) / Math.max(projected, 1.0);

            if (deviation < LOCK_THRESHOLD_LOW) {
                anchorSec = projected;
                bufferEma.put(itemId, new double[]{ smoothedRate, projected, now, amount });
                return anchorSec;
            } else if (deviation > LOCK_THRESHOLD_HIGH) {
                anchorSec = instantBuffer;
            } else {
                anchorSec = BLEND_ALPHA * instantBuffer + (1.0 - BLEND_ALPHA) * projected;
            }
        }

        bufferEma.put(itemId, new double[]{ smoothedRate, anchorSec, now, amount });
        return anchorSec;
    }

    /**
     * 基于锚点计算"现在应显示多少秒"。
     *
     * @return 倒计时秒数；不存在或 ≤ 0 → -1（不可用 / ∞）
     */
    public static double computeCountdownSeconds(String itemId, Map<String, double[]> bufferEma) {
        return computeCountdownSeconds(itemId, bufferEma, Clock.SYSTEM);
    }

    /** 注入时钟版本 */
    public static double computeCountdownSeconds(String itemId, Map<String, double[]> bufferEma, Clock clock) {
        double[] state = bufferEma.get(itemId);
        if (state == null || state.length < 3 || state[1] <= 0) return -1;
        double elapsed = (clock.currentTimeMillis() - state[2]) / 1000.0;
        return Math.max(0, state[1] - elapsed);
    }
}
