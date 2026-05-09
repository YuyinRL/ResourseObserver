package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageKpiBuilderTest {

    @Test
    void buildProducesThreeKpis() {
        List<StorageSnapshot.KpiSnapshot> kpis = StorageKpiBuilder.build(0L, 0, 0.0);
        assertEquals(3, kpis.size());
        assertEquals(StorageKpiBuilder.KEY_TOTAL_ITEMS, kpis.get(0).labelKey());
        assertEquals(StorageKpiBuilder.KEY_TOTAL_TYPES, kpis.get(1).labelKey());
        assertEquals(StorageKpiBuilder.KEY_FILL_RATE, kpis.get(2).labelKey());
    }

    @Test
    void totalAmountZeroIsNeutral() {
        var kpis = StorageKpiBuilder.build(0L, 5, 0.5);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, kpis.get(0).status());
    }

    @Test
    void totalAmountPositiveIsPositive() {
        var kpis = StorageKpiBuilder.build(1L, 5, 0.5);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(0).status());
    }

    @Test
    void totalTypesZeroIsNeutral() {
        var kpis = StorageKpiBuilder.build(100L, 0, 0.5);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, kpis.get(1).status());
    }

    @Test
    void totalTypesPositiveIsPositive() {
        var kpis = StorageKpiBuilder.build(100L, 1, 0.5);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(1).status());
    }

    @Test
    void fillRateAt90PercentIsWarning() {
        var kpis = StorageKpiBuilder.build(100L, 5, 0.9);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, kpis.get(2).status());
    }

    @Test
    void fillRateAbove90PercentIsWarning() {
        var kpis = StorageKpiBuilder.build(100L, 5, 0.99);
        assertEquals(OverviewSnapshot.KpiStatus.WARNING, kpis.get(2).status());
    }

    @Test
    void fillRateBelow90PercentIsPositive() {
        var kpis = StorageKpiBuilder.build(100L, 5, 0.5);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(2).status());
    }

    @Test
    void fillRateZeroIsPositive() {
        var kpis = StorageKpiBuilder.build(100L, 5, 0.0);
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE, kpis.get(2).status());
    }

    @Test
    void negativeFillRateIsNeutral() {
        var kpis = StorageKpiBuilder.build(100L, 5, -1.0);
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL, kpis.get(2).status());
    }

    @Test
    void classifyFillRateBoundaries() {
        assertEquals(OverviewSnapshot.KpiStatus.NEUTRAL,
                StorageKpiBuilder.classifyFillRate(-0.001));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                StorageKpiBuilder.classifyFillRate(0.0));
        assertEquals(OverviewSnapshot.KpiStatus.POSITIVE,
                StorageKpiBuilder.classifyFillRate(0.899));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                StorageKpiBuilder.classifyFillRate(0.9));
        assertEquals(OverviewSnapshot.KpiStatus.WARNING,
                StorageKpiBuilder.classifyFillRate(1.0));
    }

    @Test
    void totalTypesValueRendered() {
        var kpis = StorageKpiBuilder.build(100L, 42, 0.5);
        assertEquals("42", kpis.get(1).value());
    }
}
