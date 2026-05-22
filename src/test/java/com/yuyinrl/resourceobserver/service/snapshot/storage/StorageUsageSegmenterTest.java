package com.yuyinrl.resourceobserver.service.snapshot.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageUsageSegmenterTest {

    private static StorageSnapshot.ItemRowSnapshot row(String id, String group, long amount) {
        return new StorageSnapshot.ItemRowSnapshot(
                id, id, "", amount, amount, 0.0, StorageSnapshot.AlertLevel.GREEN,
                group, 0.0, "∞", 1.0);
    }

    @Test
    void emptyItemsReturnsEmptySegments() {
        assertTrue(StorageUsageSegmenter.build(List.of()).isEmpty());
    }

    @Test
    void nullItemsReturnsEmpty() {
        assertTrue(StorageUsageSegmenter.build(null).isEmpty());
    }

    @Test
    void allZeroAmountReturnsEmpty() {
        var items = List.of(row("a", "raw", 0L), row("b", "finished", 0L));
        assertTrue(StorageUsageSegmenter.build(items).isEmpty());
    }

    @Test
    void singleGroupSums100Percent() {
        var items = List.of(row("a", "raw", 50L), row("b", "raw", 50L));
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(1, segments.size());
        assertEquals(100.0, segments.get(0).percentage(), 1e-9);
        assertEquals("raw", segments.get(0).groupKey());
        assertEquals(StorageSnapshot.GroupColorSlot.RAW, segments.get(0).colorSlot());
    }

    @Test
    void multipleGroupsSortedDescending() {
        var items = List.of(
                row("a", "raw", 10L),
                row("b", "intermediate", 30L),
                row("c", "finished", 60L)
        );
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(3, segments.size());
        assertEquals("finished", segments.get(0).groupKey());
        assertEquals("intermediate", segments.get(1).groupKey());
        assertEquals("raw", segments.get(2).groupKey());
        assertEquals(60.0, segments.get(0).percentage(), 1e-9);
        assertEquals(30.0, segments.get(1).percentage(), 1e-9);
        assertEquals(10.0, segments.get(2).percentage(), 1e-9);
    }

    @Test
    void tinySegmentsBelowHalfPercentFiltered() {
        var items = List.of(
                row("big", "raw", 999L),
                row("tiny", "finished", 1L) // 0.1% < 0.5% → 过滤
        );
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(1, segments.size());
        assertEquals("raw", segments.get(0).groupKey());
    }

    @Test
    void unknownGroupMapsToOtherSlot() {
        var items = List.of(row("x", "weird-custom", 100L));
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(1, segments.size());
        assertEquals(StorageSnapshot.GroupColorSlot.OTHER, segments.get(0).colorSlot());
        assertEquals("screen.resourceobserver.storage.usage.other", segments.get(0).displayNameKey());
    }

    @Test
    void nullGroupNormalizedToUngrouped() {
        var items = List.of(row("x", null, 100L));
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(1, segments.size());
        assertEquals(StorageUsageSegmenter.GROUP_UNGROUPED, segments.get(0).groupKey());
    }

    @Test
    void negativeAmountTreatedAsZero() {
        var items = List.of(
                row("good", "raw", 100L),
                row("bad", "finished", -50L)
        );
        var segments = StorageUsageSegmenter.build(items);
        assertEquals(1, segments.size());
        assertEquals("raw", segments.get(0).groupKey());
        assertEquals(100.0, segments.get(0).percentage(), 1e-9);
    }

    @Test
    void translationKeysCorrect() {
        assertEquals("screen.resourceobserver.storage.usage.raw",
                StorageUsageSegmenter.groupTranslationKey("raw"));
        assertEquals("screen.resourceobserver.storage.usage.intermediate",
                StorageUsageSegmenter.groupTranslationKey("intermediate"));
        assertEquals("screen.resourceobserver.storage.usage.finished",
                StorageUsageSegmenter.groupTranslationKey("finished"));
        assertEquals("screen.resourceobserver.storage.usage.other",
                StorageUsageSegmenter.groupTranslationKey("anything-else"));
    }

    @Test
    void colorSlotMapping() {
        assertEquals(StorageSnapshot.GroupColorSlot.RAW,
                StorageUsageSegmenter.groupColorSlot("RAW"));
        assertEquals(StorageSnapshot.GroupColorSlot.INTERMEDIATE,
                StorageUsageSegmenter.groupColorSlot("intermediate"));
        assertEquals(StorageSnapshot.GroupColorSlot.FINISHED,
                StorageUsageSegmenter.groupColorSlot("Finished"));
        assertEquals(StorageSnapshot.GroupColorSlot.OTHER,
                StorageUsageSegmenter.groupColorSlot(null));
    }
}
