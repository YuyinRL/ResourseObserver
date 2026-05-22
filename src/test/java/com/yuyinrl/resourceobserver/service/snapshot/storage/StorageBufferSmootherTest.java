package com.yuyinrl.resourceobserver.service.snapshot.storage;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBufferSmootherTest {

    /** 可控时钟：测试推进 currentTime */
    private static final class FakeClock implements StorageBufferSmoother.Clock {
        long now = 1_000_000L;
        @Override public long currentTimeMillis() { return now; }
        void advance(long ms) { now += ms; }
    }

    @Test
    void firstSampleSeedsAnchorAtInstantBuffer() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        // amount=120, rate=60/min → 瞬时缓冲 = 120 * 60 / 60 = 120s
        double anchor = StorageBufferSmoother.smoothRateAndUpdateAnchor(
                "iron", 60.0, 120L, ema, clock);
        assertEquals(120.0, anchor, 1e-9);
        assertNotNull(ema.get("iron"));
        assertEquals(60.0, ema.get("iron")[0], 1e-9);
        assertEquals(120.0, ema.get("iron")[1], 1e-9);
        assertEquals(clock.now, (long) ema.get("iron")[2]);
        assertEquals(120.0, ema.get("iron")[3], 1e-9);
    }

    @Test
    void smallNoiseLocksAnchorAndDecreasesProjected() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        // 第一次：120s 锚点
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        // 推进 10 秒，再来一个非常接近的瞬时（amount/rate 几乎不变）
        clock.advance(10_000L);
        double anchor = StorageBufferSmoother.smoothRateAndUpdateAnchor(
                "iron", 60.0, 120L, ema, clock);
        // 投影 = 120 - 10 = 110；瞬时仍约 120；偏差 = 10/110 ≈ 9.1% > 2%
        // 实际偏差 (120 - 110) / max(110,1) = 10/110 ≈ 0.0909，落到 LOW (0.02) ~ HIGH (0.25) 之间
        // 走 BLEND 路径：anchor = 0.15 * 120 + 0.85 * 110 = 111.5
        assertEquals(111.5, anchor, 1e-9);
    }

    @Test
    void tinyDeviationStaysOnProjected() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        // 推进 1 秒；新瞬时仍是 120s；偏差 = (120-119)/119 ≈ 0.84% < 2%
        clock.advance(1000L);
        double anchor = StorageBufferSmoother.smoothRateAndUpdateAnchor(
                "iron", 60.0, 120L, ema, clock);
        // LOW 路径：返回 projected = 119s
        assertEquals(119.0, anchor, 1e-9);
        // 锚点重新刷到 projected
        assertEquals(119.0, ema.get("iron")[1], 1e-9);
    }

    @Test
    void bigJumpSnapsToInstant() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        // 推进 5 秒；amount 减半到 60，rate=60 → 新瞬时 60s；
        // smoothedRate = 0.05*60 + 0.95*60 = 60；瞬时 = 60*60/60 = 60s
        // projected = 120-5 = 115；偏差 = |60-115|/115 ≈ 47.8% > 25% → snap
        clock.advance(5000L);
        double anchor = StorageBufferSmoother.smoothRateAndUpdateAnchor(
                "iron", 60.0, 60L, ema, clock);
        assertEquals(60.0, anchor, 1e-6);
    }

    @Test
    void rateEmaSmoothsSpike() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        // 初始：rate=60
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        clock.advance(1000L);
        // 突然 rate=600（瞬时拉满）
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 600.0, 120L, ema, clock);
        // smoothedRate = 0.05*600 + 0.95*60 = 30 + 57 = 87
        assertEquals(87.0, ema.get("iron")[0], 1e-9);
    }

    @Test
    void countdownSecondsReflectsElapsed() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        clock.advance(30_000L);
        double sec = StorageBufferSmoother.computeCountdownSeconds("iron", ema, clock);
        assertEquals(90.0, sec, 1e-9); // 120 - 30 = 90
    }

    @Test
    void countdownReturnsMinusOneWhenMissing() {
        Map<String, double[]> ema = new HashMap<>();
        assertEquals(-1.0,
                StorageBufferSmoother.computeCountdownSeconds("missing", ema, new FakeClock()),
                1e-9);
    }

    @Test
    void countdownClampsAtZero() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        clock.advance(1_000_000L); // 1000 秒，远超 120s 锚点
        double sec = StorageBufferSmoother.computeCountdownSeconds("iron", ema, clock);
        assertEquals(0.0, sec, 1e-9);
    }

    @Test
    void corruptedStateRecovers() {
        Map<String, double[]> ema = new HashMap<>();
        ema.put("iron", new double[]{0.0, 0.0}); // 长度不足 + 锚点为 0
        FakeClock clock = new FakeClock();
        double anchor = StorageBufferSmoother.smoothRateAndUpdateAnchor(
                "iron", 60.0, 120L, ema, clock);
        assertEquals(120.0, anchor, 1e-9);
        assertEquals(4, ema.get("iron").length);
    }

    @Test
    void multipleItemsTrackedIndependently() {
        Map<String, double[]> ema = new HashMap<>();
        FakeClock clock = new FakeClock();
        StorageBufferSmoother.smoothRateAndUpdateAnchor("iron", 60.0, 120L, ema, clock);
        StorageBufferSmoother.smoothRateAndUpdateAnchor("gold", 30.0, 60L, ema, clock);
        assertTrue(ema.containsKey("iron"));
        assertTrue(ema.containsKey("gold"));
        assertEquals(120.0, ema.get("iron")[1], 1e-9);
        assertEquals(120.0, ema.get("gold")[1], 1e-9); // 60*60/30
    }

    @Test
    void systemClockExists() {
        // 烟雾测试：默认 SYSTEM 时钟可调用
        long t = StorageBufferSmoother.Clock.SYSTEM.currentTimeMillis();
        assertFalse(t <= 0L);
    }
}
