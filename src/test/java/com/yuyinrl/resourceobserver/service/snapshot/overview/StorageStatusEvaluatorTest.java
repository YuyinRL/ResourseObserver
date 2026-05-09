package com.yuyinrl.resourceobserver.service.snapshot.overview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * StorageStatusEvaluator 单元测试 —— 覆盖六个决策分支与夹逼工具。
 */
class StorageStatusEvaluatorTest {

    private static final double EPS = 1.0E-9;

    /** 构造一个"全绿"基线，便于按需打破单一字段验证某条决策分支。 */
    private static StorageStatusEvaluator.Inputs healthy() {
        return new StorageStatusEvaluator.Inputs(
                /* hasAe2Binding   */ true,
                /* diskAvailable   */ true,
                /* diskReliable    */ true,
                /* externalAvailable */ true,
                /* externalReliable  */ true,
                /* itemUsedBytes   */ 100,
                /* itemTotalBytes  */ 1000,
                /* itemUsedTypes   */ 5,
                /* itemTotalTypes  */ 64,
                /* fluidUsedBytes  */ 100,
                /* fluidTotalBytes */ 1000,
                /* fluidUsedTypes  */ 2,
                /* fluidTotalTypes */ 32
        );
    }

    private static StorageStatusEvaluator.Inputs withItemBytes(long used, long total) {
        StorageStatusEvaluator.Inputs base = healthy();
        return new StorageStatusEvaluator.Inputs(
                base.hasAe2Binding(),
                base.diskAvailable(), base.diskReliable(),
                base.externalAvailable(), base.externalReliable(),
                used, total,
                base.itemUsedTypes(), base.itemTotalTypes(),
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                base.fluidUsedTypes(), base.fluidTotalTypes()
        );
    }

    // --- evaluate priority ---

    @Test
    void evaluate_noAe2_short_circuit_neutral() {
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                false, true, true, true, true,
                900, 1000, 60, 64, 100, 1000, 2, 32);
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, r.status());
        assertEquals(StorageStatusEvaluator.Reason.NO_AE2, r.reason());
    }

    @Test
    void evaluate_diskAndExternalUnavailable_cellsUnavailable() {
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true,
                false, base.diskReliable(),
                false, base.externalReliable(),
                base.itemUsedBytes(), base.itemTotalBytes(),
                base.itemUsedTypes(), base.itemTotalTypes(),
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                base.fluidUsedTypes(), base.fluidTotalTypes());
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, r.status());
        assertEquals(StorageStatusEvaluator.Reason.CELLS_UNAVAILABLE, r.reason());
    }

    @Test
    void evaluate_oneSideAvailable_thenChecksReliability() {
        // disk 不可用但 external 可用 → 进入下一层判 reliable
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true,
                false, false,            // disk 不可用 + 不可靠
                true, true,              // external 完好
                100, 1000, 5, 64,
                100, 1000, 2, 32);
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, r.status());
        assertEquals(StorageStatusEvaluator.Reason.UNRELIABLE, r.reason());
    }

    @Test
    void evaluate_diskUnreliable_warning() {
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, false, true, true,
                base.itemUsedBytes(), base.itemTotalBytes(),
                base.itemUsedTypes(), base.itemTotalTypes(),
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                base.fluidUsedTypes(), base.fluidTotalTypes());
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, r.status());
        assertEquals(StorageStatusEvaluator.Reason.UNRELIABLE, r.reason());
    }

    @Test
    void evaluate_externalUnreliable_warning() {
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, true, true, false,
                base.itemUsedBytes(), base.itemTotalBytes(),
                base.itemUsedTypes(), base.itemTotalTypes(),
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                base.fluidUsedTypes(), base.fluidTotalTypes());
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(StorageStatusEvaluator.Reason.UNRELIABLE, r.reason());
    }

    @Test
    void evaluate_itemTypesFull_typesFull() {
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, true, true, true,
                base.itemUsedBytes(), base.itemTotalBytes(),
                64, 64, // 类型槽全占
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                base.fluidUsedTypes(), base.fluidTotalTypes());
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, r.status());
        assertEquals(StorageStatusEvaluator.Reason.TYPES_FULL, r.reason());
    }

    @Test
    void evaluate_fluidTypesFull_typesFull() {
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, true, true, true,
                base.itemUsedBytes(), base.itemTotalBytes(),
                base.itemUsedTypes(), base.itemTotalTypes(),
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                32, 32);
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(StorageStatusEvaluator.Reason.TYPES_FULL, r.reason());
    }

    @Test
    void evaluate_typesTotalZero_doesNotTriggerFull() {
        // total=0 不应触发 TYPES_FULL（即便 used=0）
        StorageStatusEvaluator.Inputs base = healthy();
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, true, true, true,
                base.itemUsedBytes(), base.itemTotalBytes(),
                0, 0,
                base.fluidUsedBytes(), base.fluidTotalBytes(),
                0, 0);
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, r.status());
        assertEquals(StorageStatusEvaluator.Reason.NORMAL, r.reason());
    }

    @Test
    void evaluate_bytesAt90Percent_bytesHigh() {
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(withItemBytes(900, 1000));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, r.status());
        assertEquals(StorageStatusEvaluator.Reason.BYTES_HIGH, r.reason());
    }

    @Test
    void evaluate_bytesJustBelow90_normal() {
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(withItemBytes(899, 1000));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, r.status());
        assertEquals(StorageStatusEvaluator.Reason.NORMAL, r.reason());
    }

    @Test
    void evaluate_typesFullTakesPrecedenceOverBytesHigh() {
        StorageStatusEvaluator.Inputs in = new StorageStatusEvaluator.Inputs(
                true, true, true, true, true,
                950, 1000,        // bytes >= 90%
                64, 64,           // 类型槽全占
                100, 1000, 0, 32);
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(in);
        assertEquals(StorageStatusEvaluator.Reason.TYPES_FULL, r.reason());
    }

    @Test
    void evaluate_healthy_normal() {
        StorageStatusEvaluator.Result r = StorageStatusEvaluator.evaluate(healthy());
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, r.status());
        assertEquals(StorageStatusEvaluator.Reason.NORMAL, r.reason());
    }

    // --- helpers ---

    @Test
    void clampTotal_takesMax() {
        assertEquals(100, StorageStatusEvaluator.clampTotal(50, 100));
        assertEquals(50, StorageStatusEvaluator.clampTotal(50, 30));   // total < used → 夹到 used
        assertEquals(0, StorageStatusEvaluator.clampTotal(0, 0));
    }

    @Test
    void bytesFillRatio_basic() {
        assertEquals(0.5, StorageStatusEvaluator.bytesFillRatio(500, 1000), EPS);
        assertEquals(0.0, StorageStatusEvaluator.bytesFillRatio(0, 0), EPS);
        // total < used 被夹至 used，比例为 1.0
        assertEquals(1.0, StorageStatusEvaluator.bytesFillRatio(100, 50), EPS);
        assertEquals(0.0, StorageStatusEvaluator.bytesFillRatio(0, 1000), EPS);
    }
}
