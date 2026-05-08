package com.yuyinrl.resourceobserver.world.block.entity;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AE2 采样数据容器 —— 从 ObserverBlockEntity 中提取，集中管理所有采样状态 Map 和速率计算。
 * <p>
 * 字段使用包级可见性，仅在同一包内的 {@link Ae2Sampler} 可直接读写；包外（OBE / FluxSampler /
 * Web Handler 等）必须通过本类的访问器/领域方法（{@link #getCellCapacityMetrics}、
 * {@link #cachedSnapshotSize}、{@link #clearCellCapacityMetrics} 等）来读写。
 */
public class Ae2DataStore {

    // ===================== 物品快照 Maps =====================

    final Map<String, Map<String, Long>> itemAmounts = new HashMap<>();
    final Map<String, Map<String, Long>> itemDeltas = new HashMap<>();
    final Map<String, Map<String, Double>> itemRatesPerMin = new HashMap<>();
    final Map<String, Map<String, Double>> itemProdRatesPerMin = new HashMap<>();
    final Map<String, Map<String, Double>> itemConsRatesPerMin = new HashMap<>();
    final Map<String, Map<String, Double>> itemProdRateEma = new HashMap<>();
    final Map<String, Map<String, Double>> itemConsRateEma = new HashMap<>();

    // ===================== 双缓冲滑动窗口 =====================

    final Map<String, Map<String, Long>> itemConsCurrent = new HashMap<>();
    final Map<String, Map<String, Long>> itemProdCurrent = new HashMap<>();
    final Map<String, Map<String, Long>> itemConsPrev = new HashMap<>();
    final Map<String, Map<String, Long>> itemProdPrev = new HashMap<>();
    final Map<String, Integer> itemWindowSampleCount = new HashMap<>();
    final Map<String, Integer> itemPrevWindowSampleCount = new HashMap<>();
    final Map<String, Integer> warmupRemaining = new HashMap<>();

    // ===================== KPI 聚合双缓冲 =====================

    /** [0]=itemProd, [1]=itemCons, [2]=fluidProd, [3]=fluidCons */
    final Map<String, long[]> kpiAccumCurrent = new HashMap<>();
    final Map<String, long[]> kpiAccumPrev = new HashMap<>();

    // ===================== 容量 / 调试 =====================

    final Map<String, Ae2CellCapacityMetrics> cellCapacityMetrics = new HashMap<>();

    // ===================== 只读访问器 =====================

    public Map<String, Long> getItemAmounts(String networkId) {
        Map<String, Long> d = itemAmounts.get(networkId);
        return d == null ? Map.of() : Collections.unmodifiableMap(d);
    }

    public Map<String, Long> getItemDeltas(String networkId) {
        Map<String, Long> d = itemDeltas.get(networkId);
        return d == null ? Map.of() : Collections.unmodifiableMap(d);
    }

    public Map<String, Double> getItemRatesPerMin(String networkId) {
        Map<String, Double> d = itemRatesPerMin.get(networkId);
        return d == null ? Map.of() : Collections.unmodifiableMap(d);
    }

    public Map<String, Double> getItemProdRatesPerMin(String networkId) {
        Map<String, Double> d = itemProdRatesPerMin.get(networkId);
        return d == null ? Map.of() : Collections.unmodifiableMap(d);
    }

    public Map<String, Double> getItemConsRatesPerMin(String networkId) {
        Map<String, Double> d = itemConsRatesPerMin.get(networkId);
        return d == null ? Map.of() : Collections.unmodifiableMap(d);
    }

    /** 返回指定网络的存储单元（Cell）容量指标；缺失时返回 {@link Ae2CellCapacityMetrics#unavailable()}。 */
    public Ae2CellCapacityMetrics getCellCapacityMetrics(String networkId) {
        return cellCapacityMetrics.getOrDefault(networkId, Ae2CellCapacityMetrics.unavailable());
    }

    /** 返回指定网络上次缓存的物品快照大小（用于 Web 高精度采样的快照规模阈值判断）。 */
    public int cachedSnapshotSize(String networkId) {
        Map<String, Long> cached = itemAmounts.get(networkId);
        return cached == null ? 0 : cached.size();
    }

    /** 移除指定网络的容量指标记录（FluxSampler 在切换网络类型时调用）。 */
    public void clearCellCapacityMetrics(String networkId) {
        cellCapacityMetrics.remove(networkId);
    }

    /**
     * 计算 KPI 窗口统计 —— 从 ObserverBlockEntity.getKpiStats 迁移。
     * @param sampleInterval 采样间隔 tick 数
     */
    public ObserverDataPayload.KpiWindowStats computeKpiStats(String networkId, int sampleInterval) {
        long[] cur = kpiAccumCurrent.getOrDefault(networkId, new long[4]);
        long[] prev = kpiAccumPrev.getOrDefault(networkId, new long[4]);
        int curSamples = itemWindowSampleCount.getOrDefault(networkId, 0);
        int prevSamples = itemPrevWindowSampleCount.getOrDefault(networkId, 0);

        int totalSamples = curSamples + prevSamples;
        boolean recentAvailable = totalSamples > 0;
        boolean previousAvailable = prevSamples > 0;
        boolean trendAvailable = curSamples > 0 && previousAvailable;

        double toPerMin = 20.0 * 60.0 / sampleInterval;

        double itemProdRecent = recentAvailable ? (cur[0] + prev[0]) * toPerMin / totalSamples : 0;
        double itemConsRecent = recentAvailable ? (cur[1] + prev[1]) * toPerMin / totalSamples : 0;
        double fluidProdRecent = recentAvailable ? (cur[2] + prev[2]) * toPerMin / totalSamples : 0;
        double fluidConsRecent = recentAvailable ? (cur[3] + prev[3]) * toPerMin / totalSamples : 0;

        double itemProdPrev = previousAvailable ? prev[0] * toPerMin / prevSamples : 0;
        double itemConsPrev = previousAvailable ? prev[1] * toPerMin / prevSamples : 0;
        double fluidProdPrev = previousAvailable ? prev[2] * toPerMin / prevSamples : 0;
        double fluidConsPrev = previousAvailable ? prev[3] * toPerMin / prevSamples : 0;

        return new ObserverDataPayload.KpiWindowStats(
                itemProdRecent, itemConsRecent, itemProdPrev, itemConsPrev,
                fluidProdRecent, fluidConsRecent, fluidProdPrev, fluidConsPrev,
                recentAvailable, previousAvailable, trendAvailable
        );
    }

    /** 清空指定网络的所有数据 */
    public void clearNetwork(String networkId) {
        itemAmounts.remove(networkId);
        itemDeltas.remove(networkId);
        itemRatesPerMin.remove(networkId);
        itemProdRatesPerMin.remove(networkId);
        itemConsRatesPerMin.remove(networkId);
        itemProdRateEma.remove(networkId);
        itemConsRateEma.remove(networkId);
        itemConsCurrent.remove(networkId);
        itemProdCurrent.remove(networkId);
        itemConsPrev.remove(networkId);
        itemProdPrev.remove(networkId);
        itemWindowSampleCount.remove(networkId);
        itemPrevWindowSampleCount.remove(networkId);
        warmupRemaining.remove(networkId);
        kpiAccumCurrent.remove(networkId);
        kpiAccumPrev.remove(networkId);
        cellCapacityMetrics.remove(networkId);
    }

    /** 清空所有数据 */
    public void clearAll() {
        itemAmounts.clear();
        itemDeltas.clear();
        itemRatesPerMin.clear();
        itemProdRatesPerMin.clear();
        itemConsRatesPerMin.clear();
        itemProdRateEma.clear();
        itemConsRateEma.clear();
        itemConsCurrent.clear();
        itemProdCurrent.clear();
        itemConsPrev.clear();
        itemProdPrev.clear();
        itemWindowSampleCount.clear();
        itemPrevWindowSampleCount.clear();
        warmupRemaining.clear();
        kpiAccumCurrent.clear();
        kpiAccumPrev.clear();
        cellCapacityMetrics.clear();
    }
}
