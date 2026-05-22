package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingSummaryAggregatorTest {

    @Test
    void emptyAggregatorIsReliable() {
        var s = new CraftingSummaryAggregator().build();
        assertEquals(0, s.cpuCount());
        assertEquals(0, s.busyCpuCount());
        assertEquals(0L, s.totalStorageBytes());
        assertEquals(0, s.totalCoProcessors());
        assertTrue(s.reliable());
    }

    @Test
    void accumulatesAcrossNetworks() {
        var agg = new CraftingSummaryAggregator();
        agg.accumulate(2, 1, 1024L, 4, true);
        agg.accumulate(3, 2, 2048L, 8, true);
        var s = agg.build();
        assertEquals(5, s.cpuCount());
        assertEquals(3, s.busyCpuCount());
        assertEquals(3072L, s.totalStorageBytes());
        assertEquals(12, s.totalCoProcessors());
        assertTrue(s.reliable());
    }

    @Test
    void anyUnreliableMakesAllUnreliable() {
        var agg = new CraftingSummaryAggregator();
        agg.accumulate(1, 0, 0L, 0, true);
        agg.accumulate(1, 0, 0L, 0, false);
        agg.accumulate(1, 0, 0L, 0, true);
        assertEquals(false, agg.build().reliable());
    }
}
