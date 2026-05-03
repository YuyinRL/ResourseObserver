package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AE2 网络采样器 —— 从 ObserverBlockEntity 中提取的 AE2 采样逻辑。
 * <p>
 * Phase 1: 提取静态工具方法（toEntryId, computeDeltas 等）<br>
 * Phase 2: 迁移 sampleAe2Network() 核心逻辑至实例方法，ObserverBlockEntity 委托调用<br>
 * <p>
 * 与 ObserverBlockEntity 同包，可访问其 package-private 成员（dataStore, readAe2NetworkItems 等）。
 */
public final class Ae2Sampler {

    private static final Logger LOG = LoggerFactory.getLogger(Ae2Sampler.class);

    static final String FLUID_KEY_PREFIX = "fluid:";

    /** 采样间隔 tick 数 */
    static final int SAMPLE_INTERVAL = 10;
    /** 双缓冲窗口大小 */
    static final int RATE_WINDOW_SAMPLES = 24;
    /** 启动预热采样次数 */
    static final int WARMUP_SAMPLES = 4;
    /** 服务端速率 EMA α */
    static final double SERVER_RATE_EMA_ALPHA = 0.10;

    Ae2Sampler() {}

    // ===================== Key 转换工具 =====================

    public static String toEntryId(AEKey key) {
        if (key instanceof AEItemKey itemKey) return itemKey.getId().toString();
        if (key instanceof AEFluidKey fluidKey) return FLUID_KEY_PREFIX + fluidKey.getId();
        return null;
    }

    public static boolean isFluidEntryId(String entryId) {
        return entryId != null && entryId.startsWith(FLUID_KEY_PREFIX);
    }

    public static boolean isItemEntryId(String entryId) {
        return !isFluidEntryId(entryId);
    }

    // ===================== 增量计算 =====================

    public static Map<String, Long> computeDeltas(Map<String, Long> previous, Map<String, Long> current) {
        Map<String, Long> deltas = new HashMap<>();
        for (Map.Entry<String, Long> entry : current.entrySet()) {
            long oldValue = previous.getOrDefault(entry.getKey(), 0L);
            long delta = entry.getValue() - oldValue;
            if (delta != 0) deltas.put(entry.getKey(), delta);
        }
        for (Map.Entry<String, Long> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey()) && entry.getValue() != 0) {
                deltas.put(entry.getKey(), -entry.getValue());
            }
        }
        return deltas;
    }

    // ===================== 安全加法 =====================

    public static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    // ===================== 核心采样逻辑（Phase 2: 从 ObserverBlockEntity 迁移） =====================

    /**
     * 对指定 AE2 网络执行一次采样。
     * <p>
     * 读取网络中所有物品的当前数量，与上次快照对比计算每种物品的增量，
     * 正增量累加到生产量，负增量绝对值累加到消耗量。
     * 同时更新双缓冲滑动窗口速率计算和 KPI 聚合。
     * <p>
     * ObserverBlockEntity.sampleAe2Network() 委托至此方法。
     */
    ObserverBlockEntity.BindingStats sampleAe2Network(
            ObserverBlockEntity be, String networkId, net.minecraft.core.BlockPos targetPos,
            ObserverBlockEntity.BindingStats oldStats
    ) {
        ObserverBlockEntity.Ae2ReadResult readResult = be.readAe2NetworkItems(targetPos);
        be.debugInfoMap.put(networkId, readResult.debugInfo());
        be.dataStore.cellCapacityMetrics.put(networkId, readResult.cellCapacityMetrics());
        be.reportAe2CapacityIssue(networkId, targetPos, readResult);

        Map<String, Long> snapshot = readResult.snapshot();
        if (snapshot == null) {
            be.dataStore.itemDeltas.put(networkId, Map.of());
            return oldStats;
        }

        Map<String, Long> previous = be.dataStore.itemAmounts.getOrDefault(networkId, Map.of());

        // ── 启动预热保护 ──
        boolean warmingUp;
        if (previous.isEmpty()) {
            be.dataStore.warmupRemaining.put(networkId, WARMUP_SAMPLES);
            warmingUp = true;
        } else {
            int remaining = be.dataStore.warmupRemaining.getOrDefault(networkId, 0);
            if (remaining > 0) {
                be.dataStore.warmupRemaining.put(networkId, remaining - 1);
                warmingUp = true;
            } else {
                warmingUp = false;
            }
        }

        Map<String, Long> deltas = warmingUp ? Map.of() : computeDeltas(previous, snapshot);
        be.dataStore.itemAmounts.put(networkId, snapshot);
        be.dataStore.itemDeltas.put(networkId, deltas);

        // ── 双缓冲滑动窗口速率计算 + KPI 聚合 ──
        if (!warmingUp) {
            Map<String, Long> curCons = be.dataStore.itemConsCurrent.computeIfAbsent(networkId, k -> new HashMap<>());
            Map<String, Long> curProd = be.dataStore.itemProdCurrent.computeIfAbsent(networkId, k -> new HashMap<>());
            long[] kpiCur = be.dataStore.kpiAccumCurrent.computeIfAbsent(networkId, k -> new long[4]);

            for (Map.Entry<String, Long> entry : deltas.entrySet()) {
                long d = entry.getValue();
                if (isFluidEntryId(entry.getKey())) {
                    if (d > 0) kpiCur[2] = saturatingAdd(kpiCur[2], d);
                    else if (d < 0) kpiCur[3] = saturatingAdd(kpiCur[3], -d);
                } else {
                    if (d < 0) curCons.merge(entry.getKey(), -d, Long::sum);
                    else if (d > 0) curProd.merge(entry.getKey(), d, Long::sum);
                    if (d > 0) kpiCur[0] = saturatingAdd(kpiCur[0], d);
                    else if (d < 0) kpiCur[1] = saturatingAdd(kpiCur[1], -d);
                }
            }

            int curSamples = be.dataStore.itemWindowSampleCount.merge(networkId, 1, Integer::sum);

            // 合并当前窗口 + 上一完整窗口，计算组合平均速率
            Map<String, Long> prevCons = be.dataStore.itemConsPrev.getOrDefault(networkId, Map.of());
            Map<String, Long> prevProd = be.dataStore.itemProdPrev.getOrDefault(networkId, Map.of());
            int prevSamples = be.dataStore.itemPrevWindowSampleCount.getOrDefault(networkId, 0);
            int totalSamples = Math.max(1, curSamples + prevSamples);
            final double toPerMin = 20.0 * 60.0 / SAMPLE_INTERVAL;

            Map<String, Double> rates = new HashMap<>();
            Map<String, Double> prodRates = new HashMap<>();
            Map<String, Double> consRates = new HashMap<>();
            java.util.Set<String> allItems = new java.util.HashSet<>(curCons.keySet());
            allItems.addAll(curProd.keySet());
            allItems.addAll(prevCons.keySet());
            allItems.addAll(prevProd.keySet());

            Map<String, Double> prevProdEma = be.dataStore.itemProdRateEma.computeIfAbsent(networkId, k -> new HashMap<>());
            Map<String, Double> prevConsEma = be.dataStore.itemConsRateEma.computeIfAbsent(networkId, k -> new HashMap<>());

            for (String itemId : allItems) {
                long consumed = curCons.getOrDefault(itemId, 0L) + prevCons.getOrDefault(itemId, 0L);
                long produced = curProd.getOrDefault(itemId, 0L) + prevProd.getOrDefault(itemId, 0L);
                double rawProd = produced * toPerMin / totalSamples;
                double rawCons = consumed * toPerMin / totalSamples;
                double smoothProd = SERVER_RATE_EMA_ALPHA * rawProd
                        + (1.0 - SERVER_RATE_EMA_ALPHA) * prevProdEma.getOrDefault(itemId, rawProd);
                double smoothCons = SERVER_RATE_EMA_ALPHA * rawCons
                        + (1.0 - SERVER_RATE_EMA_ALPHA) * prevConsEma.getOrDefault(itemId, rawCons);
                prevProdEma.put(itemId, smoothProd);
                prevConsEma.put(itemId, smoothCons);
                double netRatePerMin = smoothProd - smoothCons;
                if (Math.abs(netRatePerMin) > 0.001) rates.put(itemId, netRatePerMin);
                if (smoothProd > 0.001) prodRates.put(itemId, smoothProd);
                if (smoothCons > 0.001) consRates.put(itemId, smoothCons);
            }

            be.dataStore.itemRatesPerMin.put(networkId, rates);
            be.dataStore.itemProdRatesPerMin.put(networkId, prodRates);
            be.dataStore.itemConsRatesPerMin.put(networkId, consRates);

            // 窗口满后滚动
            if (curSamples >= RATE_WINDOW_SAMPLES) {
                be.dataStore.itemConsPrev.put(networkId, new HashMap<>(curCons));
                be.dataStore.itemProdPrev.put(networkId, new HashMap<>(curProd));
                be.dataStore.itemPrevWindowSampleCount.put(networkId, curSamples);
                curCons.clear();
                curProd.clear();
                be.dataStore.itemWindowSampleCount.put(networkId, 0);
                kpiAccumPrevRoll(be, networkId, kpiCur);
                logWindowRotation(networkId, curSamples, prevSamples);
            }
        }

        // ── 计算汇总统计 ──
        long totalAmount = 0;
        long itemTypeCount = 0;
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            if (!isItemEntryId(entry.getKey())) continue;
            totalAmount = saturatingAdd(totalAmount, Math.max(0L, entry.getValue()));
            itemTypeCount++;
        }

        long produced = 0;
        long consumed = 0;
        for (Map.Entry<String, Long> entry : deltas.entrySet()) {
            if (!isItemEntryId(entry.getKey())) continue;
            long delta = entry.getValue();
            if (delta > 0) produced = saturatingAdd(produced, delta);
            else if (delta < 0) consumed = saturatingAdd(consumed, -delta);
        }

        logSampleSummary(networkId, snapshot.size(), deltas.size(),
                totalAmount, produced, consumed,
                be.dataStore.itemRatesPerMin.getOrDefault(networkId, Map.of()).size(), warmingUp);

        return oldStats.withDelta(totalAmount, itemTypeCount, produced, consumed);
    }

    // ===================== AE2 网络读取（Phase 3: 从 ObserverBlockEntity 迁移） =====================

    /**
     * 读取 AE2 网络中所有物品的当前快照及容量指标。
     * ObserverBlockEntity.readAe2NetworkItems() 委托至此方法。
     */
    ObserverBlockEntity.Ae2ReadResult readAe2NetworkItems(ObserverBlockEntity be, net.minecraft.core.BlockPos targetPos) {
        appeng.api.networking.IGrid grid = be.resolveAe2GridAt(targetPos);
        if (grid == null) {
            return new ObserverBlockEntity.Ae2ReadResult(
                    null,
                    "channel=ae2.grid;status=unavailable;target=" + targetPos.toShortString(),
                    ObserverBlockEntity.Ae2CellCapacityMetrics.unavailable()
            );
        }

        appeng.api.networking.storage.IStorageService storageService = grid.getStorageService();
        appeng.api.stacks.KeyCounter cachedInventory = storageService.getCachedInventory();
        java.util.Map<String, Long> snapshot = new java.util.HashMap<>();
        int itemTypeCount = 0;
        int fluidTypeCount = 0;
        for (appeng.api.stacks.AEKey key : cachedInventory.keySet()) {
            String entryId = toEntryId(key);
            if (entryId == null) continue;
            long amount = cachedInventory.get(key);
            if (amount <= 0) continue;
            if (isFluidEntryId(entryId)) fluidTypeCount++;
            else itemTypeCount++;
            snapshot.merge(entryId, amount, Long::sum);
        }

        ObserverBlockEntity.Ae2CapacityReadResult capacityReadResult = be.readAe2CellCapacityMetrics(grid);
        ObserverBlockEntity.Ae2CellCapacityMetrics capacityMetrics = capacityReadResult.metrics();
        String debug = "channel=ae2.cached_inventory;status=ok;types="
                + snapshot.size() + ";item_types=" + itemTypeCount + ";fluid_types=" + fluidTypeCount
                + ";cells=" + (capacityMetrics.available() ? "present" : "none")
                + ";capacity_reliable=" + capacityMetrics.reliable()
                + capacityReadResult.debugSuffix();
        return new ObserverBlockEntity.Ae2ReadResult(snapshot, debug, capacityMetrics);
    }

    private static void kpiAccumPrevRoll(ObserverBlockEntity be, String networkId, long[] kpiCur) {
        be.dataStore.kpiAccumPrev.put(networkId, kpiCur.clone());
        be.dataStore.kpiAccumCurrent.put(networkId, new long[4]);
    }

    // ===================== 日志 =====================

    public static void logSampleSummary(
            String networkId, int snapshotSize, int deltaCount,
            long totalAmount, long totalProduced, long totalConsumed,
            int rateCount, boolean warmingUp
    ) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[Ae2Sample] net={} snap={} delta={} amt={} prod={} cons={} rates={} warm={}",
                    networkId, snapshotSize, deltaCount, totalAmount,
                    totalProduced, totalConsumed, rateCount, warmingUp);
        }
    }

    public static void logWindowRotation(String networkId, int curSamples, int prevSamples) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[Ae2Sample] WINDOW_ROTATE net={} curSmp={} prevSmp={}", networkId, curSamples, prevSamples);
        }
    }
}
