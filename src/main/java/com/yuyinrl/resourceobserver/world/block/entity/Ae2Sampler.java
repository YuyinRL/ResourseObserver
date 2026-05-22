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
 * Phase 6：改为有状态对象，持有 {@link Ae2DataStore} / {@link Ae2GridResolver} 引用与
 * 共享的 debugInfoMap，移除对 ObserverBlockEntity 的反向回调（不再有 {@code be.xxx}）。
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

    private final Ae2DataStore dataStore;
    private final Ae2GridResolver gridResolver;
    private final Map<String, String> debugInfoMap;

    /**
     * 构造 AE2 采样器。
     *
     * @param dataStore     共享的 AE2 数据存储（snapshot/deltas/rates 等）
     * @param gridResolver  Grid 解析器（同时承担容量异常告警）
     * @param debugInfoMap  共享的调试信息表（networkId → 单行 debug 串）
     */
    Ae2Sampler(Ae2DataStore dataStore, Ae2GridResolver gridResolver, Map<String, String> debugInfoMap) {
        this.dataStore = dataStore;
        this.gridResolver = gridResolver;
        this.debugInfoMap = debugInfoMap;
    }

    // ===================== Key 转换工具 =====================

    /**
     * 把 AE2 的 {@link AEKey} 转为我们 Web/快照层使用的字符串 ID。
     * <ul>
     *   <li>{@link AEItemKey} → {@code "<namespace>:<path>"}</li>
     *   <li>{@link AEFluidKey} → {@code "fluid:<namespace>:<path>"}</li>
     * </ul>
     *
     * @param key AE2 原始 key（item / fluid）
     * @return 字符串 ID；未知类型返回 {@code null}
     */
    public static String toEntryId(AEKey key) {
        if (key instanceof AEItemKey itemKey) return itemKey.getId().toString();
        if (key instanceof AEFluidKey fluidKey) return FLUID_KEY_PREFIX + fluidKey.getId();
        return null;
    }

    /** 判断给定 entryId 是否为流体（带 {@code "fluid:"} 前缀）。 */
    public static boolean isFluidEntryId(String entryId) {
        return entryId != null && entryId.startsWith(FLUID_KEY_PREFIX);
    }

    /** 与 {@link #isFluidEntryId} 互斥；{@code null} 视为物品。 */
    public static boolean isItemEntryId(String entryId) {
        return !isFluidEntryId(entryId);
    }

    // ===================== 增量计算 =====================

    /**
     * 比较两次快照求每个 entryId 的存量增量。
     * <p>
     * 同时考虑 current 中新增、previous 中消失（视为变成 0）的条目；增量为 0 的不放入结果。
     *
     * @param previous 上一次快照（entryId → 数量）
     * @param current  本次快照
     * @return 仅包含非零增量的 map：正数=生产，负数=消耗
     */
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

    /**
     * 饱和加法 —— 防止 long 累计溢出回卷。
     *
     * @param left  当前累计值（应为 ≥0）
     * @param right 增量（≤0 时直接返回 left，避免做减法）
     * @return {@code min(Long.MAX_VALUE, left + right)}
     */
    public static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    // ===================== 核心采样逻辑（Phase 2: 从 ObserverBlockEntity 迁移） =====================

    /**
     * 对指定 AE2 网络执行一次采样。
     */
    BindingStats sample(
            net.minecraft.world.level.Level level, String networkId,
            net.minecraft.core.BlockPos targetPos, BindingStats oldStats
    ) {
        ObserverBlockEntity.Ae2ReadResult readResult = readAe2NetworkItems(level, targetPos);
        debugInfoMap.put(networkId, readResult.debugInfo());
        dataStore.cellCapacityMetrics.put(networkId, readResult.cellCapacityMetrics());
        gridResolver.reportCapacityIssue(networkId, targetPos, readResult);

        Map<String, Long> snapshot = readResult.snapshot();
        if (snapshot == null) {
            dataStore.itemDeltas.put(networkId, Map.of());
            return oldStats;
        }

        Map<String, Long> previous = dataStore.itemAmounts.getOrDefault(networkId, Map.of());

        // ── 启动预热保护 ──
        boolean warmingUp;
        if (previous.isEmpty()) {
            dataStore.warmupRemaining.put(networkId, WARMUP_SAMPLES);
            warmingUp = true;
        } else {
            int remaining = dataStore.warmupRemaining.getOrDefault(networkId, 0);
            if (remaining > 0) {
                dataStore.warmupRemaining.put(networkId, remaining - 1);
                warmingUp = true;
            } else {
                warmingUp = false;
            }
        }

        Map<String, Long> deltas = warmingUp ? Map.of() : computeDeltas(previous, snapshot);
        dataStore.itemAmounts.put(networkId, snapshot);
        dataStore.itemDeltas.put(networkId, deltas);

        // ── 双缓冲滑动窗口速率计算 + KPI 聚合 ──
        if (!warmingUp) {
            Map<String, Long> curCons = dataStore.itemConsCurrent.computeIfAbsent(networkId, k -> new HashMap<>());
            Map<String, Long> curProd = dataStore.itemProdCurrent.computeIfAbsent(networkId, k -> new HashMap<>());
            long[] kpiCur = dataStore.kpiAccumCurrent.computeIfAbsent(networkId, k -> new long[4]);

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

            int curSamples = dataStore.itemWindowSampleCount.merge(networkId, 1, Integer::sum);

            // 合并当前窗口 + 上一完整窗口，计算组合平均速率
            Map<String, Long> prevCons = dataStore.itemConsPrev.getOrDefault(networkId, Map.of());
            Map<String, Long> prevProd = dataStore.itemProdPrev.getOrDefault(networkId, Map.of());
            int prevSamples = dataStore.itemPrevWindowSampleCount.getOrDefault(networkId, 0);
            int totalSamples = Math.max(1, curSamples + prevSamples);
            final double toPerMin = 20.0 * 60.0 / SAMPLE_INTERVAL;

            Map<String, Double> rates = new HashMap<>();
            Map<String, Double> prodRates = new HashMap<>();
            Map<String, Double> consRates = new HashMap<>();
            java.util.Set<String> allItems = new java.util.HashSet<>(curCons.keySet());
            allItems.addAll(curProd.keySet());
            allItems.addAll(prevCons.keySet());
            allItems.addAll(prevProd.keySet());

            Map<String, Double> prevProdEma = dataStore.itemProdRateEma.computeIfAbsent(networkId, k -> new HashMap<>());
            Map<String, Double> prevConsEma = dataStore.itemConsRateEma.computeIfAbsent(networkId, k -> new HashMap<>());

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

            dataStore.itemRatesPerMin.put(networkId, rates);
            dataStore.itemProdRatesPerMin.put(networkId, prodRates);
            dataStore.itemConsRatesPerMin.put(networkId, consRates);

            // 窗口满后滚动
            if (curSamples >= RATE_WINDOW_SAMPLES) {
                dataStore.itemConsPrev.put(networkId, new HashMap<>(curCons));
                dataStore.itemProdPrev.put(networkId, new HashMap<>(curProd));
                dataStore.itemPrevWindowSampleCount.put(networkId, curSamples);
                curCons.clear();
                curProd.clear();
                dataStore.itemWindowSampleCount.put(networkId, 0);
                dataStore.kpiAccumPrev.put(networkId, kpiCur.clone());
                dataStore.kpiAccumCurrent.put(networkId, new long[4]);
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
                dataStore.itemRatesPerMin.getOrDefault(networkId, Map.of()).size(), warmingUp);

        return oldStats.withDelta(totalAmount, itemTypeCount, produced, consumed);
    }

    // ===================== AE2 网络读取 =====================

    /**
     * 读取 AE2 网络中所有物品的当前快照及容量指标。
     */
    ObserverBlockEntity.Ae2ReadResult readAe2NetworkItems(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos targetPos
    ) {
        appeng.api.networking.IGrid grid = gridResolver.resolveAt(level, targetPos);
        if (grid == null) {
            return new ObserverBlockEntity.Ae2ReadResult(
                    null,
                    "channel=ae2.grid;status=unavailable;target=" + targetPos.toShortString(),
                    Ae2CellCapacityMetrics.unavailable()
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

        ObserverBlockEntity.Ae2CapacityReadResult capacityReadResult = Ae2CellProber.readCellCapacityMetrics(grid);
        Ae2CellCapacityMetrics capacityMetrics = capacityReadResult.metrics();
        String debug = "channel=ae2.cached_inventory;status=ok;types="
                + snapshot.size() + ";item_types=" + itemTypeCount + ";fluid_types=" + fluidTypeCount
                + ";cells=" + (capacityMetrics.available() ? "present" : "none")
                + ";capacity_reliable=" + capacityMetrics.reliable()
                + capacityReadResult.debugSuffix();
        return new ObserverBlockEntity.Ae2ReadResult(snapshot, debug, capacityMetrics);
    }

    // ===================== 日志 =====================

    /**
     * 输出"本次采样汇总"调试日志。
     * <p>仅在 DEBUG 级别启用时执行字符串拼装，避免热点路径 GC 压力。</p>
     *
     * @param networkId       AE2 网络 ID
     * @param snapshotSize    本次快照中的 entry 数（含物品+流体）
     * @param deltaCount      本次非零增量的 entry 数
     * @param totalAmount     物品总数累计
     * @param totalProduced   本 tick 总生产
     * @param totalConsumed   本 tick 总消耗
     * @param rateCount       当前活跃速率条目数
     * @param warmingUp       是否处于启动预热阶段
     */
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

    /**
     * 输出"双缓冲滑动窗口滚动"调试日志。
     *
     * @param networkId   AE2 网络 ID
     * @param curSamples  当前窗口积累的采样数（即将被滚动到 prev）
     * @param prevSamples 上一窗口的采样数（即将被丢弃）
     */
    public static void logWindowRotation(String networkId, int curSamples, int prevSamples) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[Ae2Sample] WINDOW_ROTATE net={} curSmp={} prevSmp={}", networkId, curSamples, prevSamples);
        }
    }
}
