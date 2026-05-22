package com.yuyinrl.resourceobserver.service.snapshot.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerOverloadInfoCalculatorTest {

    @Test
    void zeroInputReturnsZero() {
        var info = PowerOverloadInfoCalculator.compute(0L, 0L);
        assertEquals(0.0, info.headroomPercent(), 1e-9);
        assertEquals(0L, info.reservePerTick());
    }

    @Test
    void halfHeadroom() {
        var info = PowerOverloadInfoCalculator.compute(100L, 50L);
        assertEquals(50.0, info.headroomPercent(), 1e-9);
        assertEquals(50L, info.reservePerTick());
    }

    @Test
    void fullHeadroom() {
        var info = PowerOverloadInfoCalculator.compute(100L, 0L);
        assertEquals(100.0, info.headroomPercent(), 1e-9);
        assertEquals(100L, info.reservePerTick());
    }

    @Test
    void negativeHeadroomClamped() {
        var info = PowerOverloadInfoCalculator.compute(100L, 200L);
        assertEquals(0.0, info.headroomPercent(), 1e-9);
        assertEquals(0L, info.reservePerTick());
    }

    @Test
    void inputOnlyOutputsZero() {
        var info = PowerOverloadInfoCalculator.compute(0L, 100L);
        // input=0 → headroomPercent=0; reserve=max(0, 0-100)=0
        assertEquals(0.0, info.headroomPercent(), 1e-9);
        assertEquals(0L, info.reservePerTick());
    }
}
