package com.yuyinrl.resourceobserver.world.history;

import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HistoryRecorder {
    private HistoryRecorder() {
    }

    public static void recordSample(
            ServerLevel level,
            BlockPos observerPos,
            ObserverBlockEntity.BoundEntry binding,
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

    public static List<ObserverDataPayload.ChartPoint> querySeries(
            ServerLevel level,
            BlockPos observerPos,
            List<ObserverBlockEntity.BoundEntry> bindings,
            ChartWindow window,
            ChartScope scope,
            String scopeItemId
    ) {
        List<String> keys = new ArrayList<>(bindings.size());
        for (ObserverBlockEntity.BoundEntry binding : bindings) {
            keys.add(historyKey(level, observerPos, binding.networkId()));
        }
        String itemScope = scope == ChartScope.ITEM && scopeItemId != null && !scopeItemId.isBlank()
                ? scopeItemId
                : null;
        return ObserverHistorySavedData.get(level).queryAggregated(keys, window, itemScope);
    }

    private static String historyKey(ServerLevel level, BlockPos observerPos, String networkId) {
        ResourceLocation dimensionId = level.dimension().location();
        return dimensionId + "|" + observerPos.asLong() + "|" + networkId;
    }
}
