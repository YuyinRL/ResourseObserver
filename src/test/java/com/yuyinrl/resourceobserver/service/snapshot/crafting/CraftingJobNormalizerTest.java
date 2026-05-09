package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingJobNormalizerTest {

    @Test
    void clampsNegativeTotalAndRemaining() {
        var n = CraftingJobNormalizer.normalize(-5L, -10L, 0.5);
        assertEquals(0L, n.total());
        assertEquals(0L, n.remaining());
        assertEquals(0.5, n.progress(), 1e-9);
    }

    @Test
    void clampsProgressAbove1() {
        var n = CraftingJobNormalizer.normalize(100L, 50L, 1.5);
        assertEquals(1.0, n.progress(), 1e-9);
    }

    @Test
    void clampsProgressBelow0() {
        var n = CraftingJobNormalizer.normalize(100L, 50L, -0.5);
        // 进入回退分支：(100-50)/100 = 0.5
        assertEquals(0.5, n.progress(), 1e-9);
    }

    @Test
    void zeroProgressFallsBackToCalculated() {
        var n = CraftingJobNormalizer.normalize(200L, 50L, 0.0);
        // (200-50)/200 = 0.75
        assertEquals(0.75, n.progress(), 1e-9);
    }

    @Test
    void zeroProgressZeroTotalStaysZero() {
        var n = CraftingJobNormalizer.normalize(0L, 0L, 0.0);
        assertEquals(0.0, n.progress(), 1e-9);
    }

    @Test
    void remainingExceedingTotalClampsDoneToZero() {
        var n = CraftingJobNormalizer.normalize(100L, 200L, 0.0);
        // done=max(0, 100-200)=0 → progress=0
        assertEquals(0.0, n.progress(), 1e-9);
    }

    @Test
    void positiveProgressKept() {
        var n = CraftingJobNormalizer.normalize(100L, 50L, 0.42);
        assertEquals(0.42, n.progress(), 1e-9);
        assertEquals(100L, n.total());
        assertEquals(50L, n.remaining());
    }
}
