package com.yuyinrl.resourceobserver.service.snapshot.power;

import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerKpiBuilderTest {

    @Test
    void buildsThreeCards() {
        List<PowerSnapshot.KpiSnapshot> kpis = PowerKpiBuilder.build(100L, 50L, 500L, 1000L);
        assertEquals(3, kpis.size());
        assertEquals(PowerKpiBuilder.KEY_TOTAL_INPUT, kpis.get(0).labelKey());
        assertEquals(PowerKpiBuilder.KEY_TOTAL_OUTPUT, kpis.get(1).labelKey());
        assertEquals(PowerKpiBuilder.KEY_STORED, kpis.get(2).labelKey());
    }

    @Test
    void inputZeroIsNeutral() {
        var kpis = PowerKpiBuilder.build(0L, 0L, 0L, 1L);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, kpis.get(0).status());
    }

    @Test
    void inputPositive() {
        var kpis = PowerKpiBuilder.build(100L, 0L, 500L, 1000L);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(0).status());
    }

    @Test
    void outputExceedsInputIsWarning() {
        var kpis = PowerKpiBuilder.build(100L, 200L, 500L, 1000L);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, kpis.get(1).status());
    }

    @Test
    void outputPositiveBelowInput() {
        var kpis = PowerKpiBuilder.build(100L, 50L, 500L, 1000L);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(1).status());
    }

    @Test
    void outputZeroNeutral() {
        var kpis = PowerKpiBuilder.build(100L, 0L, 500L, 1000L);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, kpis.get(1).status());
    }

    @Test
    void storedLowIsNegative() {
        // storedRatio = 0.05 → ≤ 0.10 → NEGATIVE
        assertEquals(OverviewSnapshot.KpiStatus.NEGATIVE,
                PowerKpiBuilder.classifyStored(0.05));
        assertEquals(OverviewSnapshot.KpiStatus.NEGATIVE,
                PowerKpiBuilder.classifyStored(0.10));
    }

    @Test
    void storedHighIsWarning() {
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                PowerKpiBuilder.classifyStored(0.90));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                PowerKpiBuilder.classifyStored(0.99));
    }

    @Test
    void storedMidIsPositive() {
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                PowerKpiBuilder.classifyStored(0.5));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                PowerKpiBuilder.classifyStored(0.11));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                PowerKpiBuilder.classifyStored(0.89));
    }

    @Test
    void zeroCapacityHandled() {
        var kpis = PowerKpiBuilder.build(0L, 0L, 0L, 0L);
        // storedRatio fallback to 0.0 → NEGATIVE
        assertEquals(OverviewSnapshot.KpiStatus.NEGATIVE, kpis.get(2).status());
    }

    @Test
    void valueIncludesUnitSuffix() {
        var kpis = PowerKpiBuilder.build(1000L, 500L, 5000L, 10000L);
        assertTrue(kpis.get(0).value().endsWith("FE/t"));
        assertTrue(kpis.get(1).value().endsWith("FE/t"));
        assertTrue(kpis.get(2).value().endsWith("%"));
    }
}
