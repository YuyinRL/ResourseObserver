package com.yuyinrl.resourceobserver.service.snapshot.overview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KpiCalculator 单元测试 —— 覆盖 percentChange / balanceScore / 三个分类器的边界。
 */
class KpiCalculatorTest {

    private static final double EPS = 1.0E-9;

    // --- percentChange ---

    @Test
    void percentChange_normalGrowth() {
        assertEquals(50.0, KpiCalculator.percentChange(150, 100), EPS);
    }

    @Test
    void percentChange_normalDrop() {
        assertEquals(-25.0, KpiCalculator.percentChange(75, 100), EPS);
    }

    @Test
    void percentChange_zeroPrevious_returnsZero() {
        assertEquals(0.0, KpiCalculator.percentChange(100, 0), EPS);
    }

    @Test
    void percentChange_negativePrevious_returnsZero() {
        assertEquals(0.0, KpiCalculator.percentChange(100, -10), EPS);
    }

    @Test
    void percentChange_nan_returnsZero() {
        assertEquals(0.0, KpiCalculator.percentChange(Double.NaN, 100), EPS);
        assertEquals(0.0, KpiCalculator.percentChange(100, Double.NaN), EPS);
        assertEquals(0.0, KpiCalculator.percentChange(Double.POSITIVE_INFINITY, 100), EPS);
    }

    // --- balanceScore ---

    @Test
    void balanceScore_perfectBalance_returnsZero() {
        assertEquals(0.0, KpiCalculator.balanceScore(50, 50), EPS);
    }

    @Test
    void balanceScore_pureProduction_returnsHundred() {
        assertEquals(100.0, KpiCalculator.balanceScore(50, 0), EPS);
    }

    @Test
    void balanceScore_pureConsumption_returnsMinusHundred() {
        assertEquals(-100.0, KpiCalculator.balanceScore(0, 50), EPS);
    }

    @Test
    void balanceScore_partialFavorProduction() {
        assertEquals(50.0, KpiCalculator.balanceScore(75, 25), EPS);
    }

    @Test
    void balanceScore_zeroSum_returnsZero() {
        assertEquals(0.0, KpiCalculator.balanceScore(0, 0), EPS);
    }

    @Test
    void balanceScore_nan_returnsZero() {
        assertEquals(0.0, KpiCalculator.balanceScore(Double.NaN, 50), EPS);
        assertEquals(0.0, KpiCalculator.balanceScore(50, Double.NaN), EPS);
    }

    @Test
    void balanceScore_clampedToRange() {
        double v = KpiCalculator.balanceScore(1_000_000, 1);
        assertTrue(v <= 100.0, "score must be clamped to <=100");
        assertTrue(v > 99.999, "near-pure production should approach 100");
    }

    // --- classifyProduction ---

    @Test
    void classifyProduction_unavailable_neutral() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyProduction(false, 100.0));
    }

    @Test
    void classifyProduction_growth_positive() {
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                KpiCalculator.classifyProduction(true, 5.0));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                KpiCalculator.classifyProduction(true, 50.0));
    }

    @Test
    void classifyProduction_drop_warning() {
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyProduction(true, -5.0));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyProduction(true, -100.0));
    }

    @Test
    void classifyProduction_smallChange_neutral() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyProduction(true, 0.0));
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyProduction(true, 4.99));
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyProduction(true, -4.99));
    }

    // --- classifyConsumption ---

    @Test
    void classifyConsumption_unavailable_neutral() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyConsumption(false, 100.0));
    }

    @Test
    void classifyConsumption_rising_warning() {
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyConsumption(true, 5.0));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyConsumption(true, 100.0));
    }

    @Test
    void classifyConsumption_falling_positive() {
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                KpiCalculator.classifyConsumption(true, -5.0));
    }

    @Test
    void classifyConsumption_smallChange_neutral() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyConsumption(true, 0.0));
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyConsumption(true, 4.99));
    }

    // --- classifyBalance ---

    @Test
    void classifyBalance_unavailable_neutral() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyBalance(50.0, false));
    }

    @Test
    void classifyBalance_strongPositive() {
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                KpiCalculator.classifyBalance(8.0, true));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                KpiCalculator.classifyBalance(50.0, true));
    }

    @Test
    void classifyBalance_neutralBand() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyBalance(0.0, true));
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyBalance(7.99, true));
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                KpiCalculator.classifyBalance(-7.99, true));
    }

    @Test
    void classifyBalance_warningBand() {
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyBalance(-8.0, true));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyBalance(-15.0, true));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                KpiCalculator.classifyBalance(-25.0, true));
    }

    @Test
    void classifyBalance_negative() {
        assertEquals(OverviewSnapshot.KpiStatus.NEGATIVE,
                KpiCalculator.classifyBalance(-25.01, true));
        assertEquals(OverviewSnapshot.KpiStatus.NEGATIVE,
                KpiCalculator.classifyBalance(-100.0, true));
    }
}
