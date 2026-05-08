package com.yuyinrl.resourceobserver.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChartWindow 单元测试 —— 验证 ID 查找、循环切换与基础属性。
 */
class ChartWindowTest {

    @Test
    void fromIdReturnsExpectedWindow() {
        assertEquals(ChartWindow.DAY_24H_5M, ChartWindow.fromId(0));
        assertEquals(ChartWindow.WEEK_7D_30M, ChartWindow.fromId(1));
        assertEquals(ChartWindow.HOUR_1H_1M, ChartWindow.fromId(3));
        assertEquals(ChartWindow.WEB_DETAIL_1M_1S, ChartWindow.fromId(7));
    }

    @Test
    void fromIdFallsBackToDayWhenUnknown() {
        assertEquals(ChartWindow.DAY_24H_5M, ChartWindow.fromId(-1));
        assertEquals(ChartWindow.DAY_24H_5M, ChartWindow.fromId(999));
    }

    @Test
    void uiCycleAdvancesAndWraps() {
        // 当前的 UI 循环：HOUR -> DAY -> WEEK -> HOUR
        assertEquals(ChartWindow.DAY_24H_5M, ChartWindow.HOUR_1H_1M.next());
        assertEquals(ChartWindow.WEEK_7D_30M, ChartWindow.DAY_24H_5M.next());
        assertEquals(ChartWindow.HOUR_1H_1M, ChartWindow.WEEK_7D_30M.next());
    }

    @Test
    void nextOfNonCycleWindowFallsBackToFirstCycleEntry() {
        // Web 系列窗口不在 UI 循环中，next() 应回到首个循环元素 HOUR_1H_1M
        assertEquals(ChartWindow.HOUR_1H_1M, ChartWindow.WEB_SHORT_1M_10S.next());
        assertEquals(ChartWindow.HOUR_1H_1M, ChartWindow.WEB_DETAIL_1M_1S.next());
    }

    @Test
    void bucketCountAndTicksAreConsistent() {
        // DAY 窗口：288 桶 × 6000 tick ≈ 24 小时（24 × 3600 × 20 = 1,728,000 tick）
        assertEquals(288, ChartWindow.DAY_24H_5M.bucketCount());
        assertEquals(6000L, ChartWindow.DAY_24H_5M.bucketTicks());
        assertEquals(1_728_000L,
                (long) ChartWindow.DAY_24H_5M.bucketCount() * ChartWindow.DAY_24H_5M.bucketTicks());

        // HOUR 窗口：60 桶 × 1200 tick = 1 小时（60 × 60 × 20 = 72,000 tick）
        assertEquals(60, ChartWindow.HOUR_1H_1M.bucketCount());
        assertEquals(1200L, ChartWindow.HOUR_1H_1M.bucketTicks());
    }

    @Test
    void shortLabelAndTranslationKeyArePopulated() {
        for (ChartWindow w : ChartWindow.values()) {
            assertNotNull(w.shortLabel(), "shortLabel should not be null for " + w);
            assertFalse(w.shortLabel().isEmpty(), "shortLabel should not be empty for " + w);
            assertNotNull(w.translationKey());
            assertTrue(w.translationKey().startsWith("screen.resourceobserver."));
        }
    }
}
