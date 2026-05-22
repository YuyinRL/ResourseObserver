package com.yuyinrl.resourceobserver.service.snapshot.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerAlertEvaluatorTest {

    // ── evaluate ──

    @Test
    void drainedWithOutputIsCritical() {
        // usageRatio ≤ 0.05 且 outputPerTick > 0
        assertEquals(PowerSnapshot.AlertLevel.CRITICAL,
                PowerAlertEvaluator.evaluate(0.03, 100L, 50L));
        assertEquals(PowerSnapshot.AlertLevel.CRITICAL,
                PowerAlertEvaluator.evaluate(0.05, 100L, 1L));
    }

    @Test
    void drainedWithoutOutputIsNormal() {
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.03, 100L, 0L));
    }

    @Test
    void capacityFullIsCritical() {
        assertEquals(PowerSnapshot.AlertLevel.CRITICAL,
                PowerAlertEvaluator.evaluate(0.95, 100L, 50L));
        assertEquals(PowerSnapshot.AlertLevel.CRITICAL,
                PowerAlertEvaluator.evaluate(1.0, 100L, 50L));
    }

    @Test
    void capacityNearFullIsWarning() {
        assertEquals(PowerSnapshot.AlertLevel.WARNING,
                PowerAlertEvaluator.evaluate(0.80, 100L, 50L));
        assertEquals(PowerSnapshot.AlertLevel.WARNING,
                PowerAlertEvaluator.evaluate(0.90, 100L, 50L));
    }

    @Test
    void outputDoubleInputIsWarning() {
        // outputPerTick > inputPerTick * 2
        assertEquals(PowerSnapshot.AlertLevel.WARNING,
                PowerAlertEvaluator.evaluate(0.5, 100L, 201L));
    }

    @Test
    void outputBelowDoubleInputNormal() {
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.5, 100L, 200L));
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.5, 100L, 150L));
    }

    @Test
    void zeroEverythingNormal() {
        // usageRatio=0 但 output=0 → 不触发 drained-critical 分支
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.0, 0L, 0L));
    }

    @Test
    void healthyMidRangeNormal() {
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.5, 100L, 80L));
    }

    @Test
    void boundaryAt79PercentNormal() {
        assertEquals(PowerSnapshot.AlertLevel.NORMAL,
                PowerAlertEvaluator.evaluate(0.79, 100L, 50L));
    }

    // ── computeThroughputLoss ──

    @Test
    void noLossInHealthyZone() {
        assertEquals(0.0, PowerAlertEvaluator.computeThroughputLoss(0.5, 1000L, 100L), 1e-9);
    }

    @Test
    void capacityOverflowMapsTo50PercentMax() {
        assertEquals(0.0, PowerAlertEvaluator.computeThroughputLoss(0.95, 1000L, 100L), 1e-9);
        assertEquals(50.0, PowerAlertEvaluator.computeThroughputLoss(1.0, 1000L, 100L), 1e-9);
        // 中点 97.5% → 25%
        assertEquals(25.0, PowerAlertEvaluator.computeThroughputLoss(0.975, 1000L, 100L), 1e-9);
    }

    @Test
    void emptyButOutputtingIs100Percent() {
        assertEquals(100.0, PowerAlertEvaluator.computeThroughputLoss(0.5, 0L, 100L), 1e-9);
        assertEquals(100.0, PowerAlertEvaluator.computeThroughputLoss(0.0, -10L, 100L), 1e-9);
    }

    @Test
    void emptyAndIdleIsZero() {
        assertEquals(0.0, PowerAlertEvaluator.computeThroughputLoss(0.0, 0L, 0L), 1e-9);
    }

    @Test
    void capacityOverflowTakesPrecedenceOverEmpty() {
        // usageRatio>=0.95 优先；即使 stored=0 也走容量分支
        assertEquals(50.0, PowerAlertEvaluator.computeThroughputLoss(1.0, 0L, 100L), 1e-9);
    }
}
