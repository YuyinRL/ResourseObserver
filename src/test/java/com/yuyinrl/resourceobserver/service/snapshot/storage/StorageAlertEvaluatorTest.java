package com.yuyinrl.resourceobserver.service.snapshot.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageAlertEvaluatorTest {

    // ====== evaluate (单节点视图) ======

    @Test
    void emptyAmountIsRed() {
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluate(0L, 0.0, 0.5));
    }

    @Test
    void negativeAmountIsRed() {
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluate(-5L, 0.0, 0.0));
    }

    @Test
    void capacityAt95PercentIsRed() {
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluate(100L, 0.0, 0.95));
    }

    @Test
    void capacityAt94PercentIsNotRed() {
        // 仅当 ≥ 95% 才 RED；94% 仍走绿/黄分支（这里 consumption=0 → GREEN）
        assertEquals(StorageSnapshot.AlertLevel.GREEN,
                StorageAlertEvaluator.evaluate(100L, 0.0, 0.94));
    }

    @Test
    void low30sBufferIsYellow() {
        // amount=10, consumption=30/min → 缓冲 = 10*60/30 = 20s < 30s → YELLOW
        assertEquals(StorageSnapshot.AlertLevel.YELLOW,
                StorageAlertEvaluator.evaluate(10L, 30.0, 0.5));
    }

    @Test
    void exactly30sBufferIsGreen() {
        // amount=15, consumption=30/min → 缓冲 = 30s（等于阈值）
        // 严格 < 才 YELLOW，所以等于 30s 应为 GREEN
        assertEquals(StorageSnapshot.AlertLevel.GREEN,
                StorageAlertEvaluator.evaluate(15L, 30.0, 0.0));
    }

    @Test
    void noConsumptionIsGreen() {
        assertEquals(StorageSnapshot.AlertLevel.GREEN,
                StorageAlertEvaluator.evaluate(1000L, 0.0, 0.5));
    }

    @Test
    void wellStockedIsGreen() {
        // 缓冲 = 1000*60/10 = 6000s → GREEN
        assertEquals(StorageSnapshot.AlertLevel.GREEN,
                StorageAlertEvaluator.evaluate(1000L, 10.0, 0.5));
    }

    @Test
    void capacityAndYellowBufferStillRed() {
        // RED 优先于 YELLOW
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluate(10L, 30.0, 0.97));
    }

    // ====== evaluateGlobal ======

    @Test
    void globalEmptyAmountIsRed() {
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluateGlobal(0L, 0.0, 0.0));
    }

    @Test
    void globalAnyNodeFullIsRed() {
        // 任一节点 ≥ 95% → RED
        assertEquals(StorageSnapshot.AlertLevel.RED,
                StorageAlertEvaluator.evaluateGlobal(1000L, 0.0, 0.96));
    }

    @Test
    void globalLowBufferIsYellow() {
        assertEquals(StorageSnapshot.AlertLevel.YELLOW,
                StorageAlertEvaluator.evaluateGlobal(5L, 30.0, 0.5));
    }

    @Test
    void globalAllHealthyIsGreen() {
        assertEquals(StorageSnapshot.AlertLevel.GREEN,
                StorageAlertEvaluator.evaluateGlobal(1_000_000L, 1000.0, 0.3));
    }
}
