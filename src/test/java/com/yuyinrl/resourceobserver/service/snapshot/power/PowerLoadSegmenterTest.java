package com.yuyinrl.resourceobserver.service.snapshot.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerLoadSegmenterTest {

    private static PowerSnapshot.DeviceSnapshot dev(String id, PowerSnapshot.LoadCategory cat, long ept) {
        return new PowerSnapshot.DeviceSnapshot(id, id, cat, "mod", ept, 0L, 1L, 0.0,
                PowerSnapshot.AlertLevel.NORMAL);
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(PowerLoadSegmenter.build(null).isEmpty());
        assertTrue(PowerLoadSegmenter.build(List.of()).isEmpty());
    }

    @Test
    void zeroSumReturnsEmpty() {
        var devices = List.of(
                dev("a", PowerSnapshot.LoadCategory.MINING, 0),
                dev("b", PowerSnapshot.LoadCategory.OTHER, -5)
        );
        assertTrue(PowerLoadSegmenter.build(devices).isEmpty());
    }

    @Test
    void aggregatesByCategory() {
        var devices = List.of(
                dev("a", PowerSnapshot.LoadCategory.MINING, 100L),
                dev("b", PowerSnapshot.LoadCategory.MINING, 50L),
                dev("c", PowerSnapshot.LoadCategory.ASSEMBLY, 50L)
        );
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals(2, segments.size());
        assertEquals(PowerSnapshot.LoadCategory.MINING, segments.get(0).category());
        assertEquals(75.0, segments.get(0).percentage(), 1e-9);
        assertEquals(PowerSnapshot.LoadCategory.ASSEMBLY, segments.get(1).category());
        assertEquals(25.0, segments.get(1).percentage(), 1e-9);
    }

    @Test
    void filtersBelowHalfPercent() {
        var devices = List.of(
                dev("a", PowerSnapshot.LoadCategory.MINING, 9999L),
                dev("b", PowerSnapshot.LoadCategory.OTHER, 1L) // 0.01% < 0.5%
        );
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals(1, segments.size());
        assertEquals(PowerSnapshot.LoadCategory.MINING, segments.get(0).category());
    }

    @Test
    void sortsDescending() {
        var devices = List.of(
                dev("a", PowerSnapshot.LoadCategory.OTHER, 10L),
                dev("b", PowerSnapshot.LoadCategory.MINING, 30L),
                dev("c", PowerSnapshot.LoadCategory.ASSEMBLY, 20L),
                dev("d", PowerSnapshot.LoadCategory.LOGISTICS, 40L)
        );
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals(4, segments.size());
        for (int i = 1; i < segments.size(); i++) {
            assertTrue(segments.get(i - 1).percentage() >= segments.get(i).percentage());
        }
    }

    @Test
    void negativeEnergyTreatedAsZero() {
        var devices = List.of(
                dev("a", PowerSnapshot.LoadCategory.MINING, 100L),
                dev("b", PowerSnapshot.LoadCategory.MINING, -50L)
        );
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals(1, segments.size());
        assertEquals(100.0, segments.get(0).percentage(), 1e-9);
    }

    @Test
    void nullCategoryFallsBackToOther() {
        var devices = List.of(
                new PowerSnapshot.DeviceSnapshot("a", "a", null, "mod", 100L, 0L, 1L, 0.0,
                        PowerSnapshot.AlertLevel.NORMAL)
        );
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals(1, segments.size());
        assertEquals(PowerSnapshot.LoadCategory.OTHER, segments.get(0).category());
    }

    @Test
    void translationKeyAttached() {
        var devices = List.of(dev("a", PowerSnapshot.LoadCategory.MINING, 100L));
        var segments = PowerLoadSegmenter.build(devices);
        assertEquals("screen.resourceobserver.power.category.mining", segments.get(0).displayNameKey());
    }
}
