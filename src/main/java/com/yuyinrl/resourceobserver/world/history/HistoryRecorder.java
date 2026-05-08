package com.yuyinrl.resourceobserver.world.history;

import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 历史数据记录器 —— 提供对 ObserverHistorySavedData 的简便访问门面（Facade）。
 * <p>
 * 职责：
 * 1. recordSample：将观察者每次采样的增量数据记录到世界级持久化存储中
 * 2. querySeries：聚合查询多个网络绑定的历史图表数据点
 * <p>
 * historyKey 格式："维度ID|观察者坐标编码|网络ID"，保证全局唯一性。
 */
public final class HistoryRecorder {
    private HistoryRecorder() {
    }

    /**
     * 记录一次采样数据到历史存储。
     * 将维度、观察者坐标和网络 ID 组合为唯一的历史键，
     * 然后将生产增量、消耗增量、当前库存及物品级别的增量写入 SavedData。
     */
    public static void recordSample(
            ServerLevel level,
            BlockPos observerPos,
            BoundEntry binding,
            long producedDelta,
            long consumedDelta,
            long currentStock,
            Map<String, Long> itemDeltas,
            Map<String, Long> itemCurrentAmounts
    ) {
        String key = historyKey(level, observerPos, binding.networkId());
        ObserverHistorySavedData.get(level).record(
                key,
                level.getGameTime(),
                producedDelta,
                consumedDelta,
                currentStock,
                itemDeltas == null ? Map.of() : itemDeltas,
                itemCurrentAmounts == null ? Map.of() : itemCurrentAmounts
        );
    }

    /**
     * 查询聚合后的图表数据序列。
     * 将所有绑定的网络历史数据合并为统一的图表数据点列表。
     * 支持全局视角（所有物品汇总）和单物品视角（scopeItemId）。
     */
    public static List<ObserverDataPayload.ChartPoint> querySeries(
            ServerLevel level,
            BlockPos observerPos,
            List<BoundEntry> bindings,
            ChartWindow window,
            ChartScope scope,
            String scopeItemId
    ) {
        // 为所有绑定生成对应的历史键
        List<String> keys = new ArrayList<>(bindings.size());
        for (BoundEntry binding : bindings) {
            keys.add(historyKey(level, observerPos, binding.networkId()));
        }
        // 根据作用域确定是否查询单物品数据
        String itemScope = scope == ChartScope.ITEM && scopeItemId != null && !scopeItemId.isBlank()
                ? scopeItemId
                : null;
        return ObserverHistorySavedData.get(level).queryAggregated(keys, window, itemScope);
    }

    public static ObserverDataPayload.KpiWindowStats queryWindowStats(
            ServerLevel level,
            BlockPos observerPos,
            BoundEntry binding,
            ChartWindow window
    ) {
        String key = historyKey(level, observerPos, binding.networkId());
        return ObserverHistorySavedData.get(level).queryWindowStats(key, window);
    }

    /** 构造历史存储键：维度ID | 观察者坐标长整型 | 网络ID */
    private static String historyKey(ServerLevel level, BlockPos observerPos, String networkId) {
        ResourceLocation dimensionId = level.dimension().location();
        return dimensionId + "|" + observerPos.asLong() + "|" + networkId;
    }
}
