package com.yuyinrl.resourceobserver.world.history;

import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ObserverHistorySavedData extends SavedData {
    private static final String DATA_NAME = "resourceobserver_history";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_HISTORY = "history";
    private static final String TAG_DAY = "day";
    private static final String TAG_WEEK = "week";
    private static final String TAG_DEBUG_10M = "debug_10m";
    private static final String TAG_LATEST_BUCKET = "latest_bucket";
    private static final String TAG_SLOT_BUCKETS = "slot_buckets";
    private static final String TAG_PRODUCED = "produced";
    private static final String TAG_CONSUMED = "consumed";
    private static final String TAG_STOCK = "stock";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_ITEM_ID = "item_id";
    private static final int ITEM_PRUNE_THRESHOLD = 2048;

    private static final Factory<ObserverHistorySavedData> FACTORY =
            new Factory<>(ObserverHistorySavedData::new, ObserverHistorySavedData::load);

    private final Map<String, NetworkHistory> histories = new HashMap<>();

    public static ObserverHistorySavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void record(
            String historyKey,
            long gameTime,
            long producedDelta,
            long consumedDelta,
            long currentStock,
            Map<String, Long> itemDeltas,
            Map<String, Long> itemCurrentAmounts
    ) {
        NetworkHistory history = histories.computeIfAbsent(historyKey, ignored -> new NetworkHistory());
        history.record(gameTime, producedDelta, consumedDelta, currentStock, itemDeltas, itemCurrentAmounts);
        setDirty();
    }

    public List<ObserverDataPayload.ChartPoint> queryAggregated(
            List<String> keys,
            ChartWindow window,
            String scopeItemId
    ) {
        int pointCount = window.bucketCount();
        double[] produced = new double[pointCount];
        double[] consumed = new double[pointCount];
        double[] stock = new double[pointCount];
        boolean[] hasFlow = new boolean[pointCount];
        boolean[] hasStock = new boolean[pointCount];

        for (String key : keys) {
            NetworkHistory history = histories.get(key);
            if (history == null) {
                continue;
            }
            history.accumulate(window, scopeItemId, produced, consumed, stock, hasFlow, hasStock);
        }

        List<ObserverDataPayload.ChartPoint> result = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double p = produced[i];
            double c = consumed[i];
            result.add(new ObserverDataPayload.ChartPoint(i, p, c, p - c, stock[i], hasFlow[i], hasStock[i]));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, NetworkHistory> entry : histories.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString(TAG_KEY, entry.getKey());
            e.put(TAG_HISTORY, entry.getValue().save());
            list.add(e);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    private static ObserverHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ObserverHistorySavedData data = new ObserverHistorySavedData();
        if (!tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            return data;
        }
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String key = e.getString(TAG_KEY);
            if (key.isEmpty() || !e.contains(TAG_HISTORY, Tag.TAG_COMPOUND)) {
                continue;
            }
            data.histories.put(key, NetworkHistory.load(e.getCompound(TAG_HISTORY)));
        }
        return data;
    }

    private static final class NetworkHistory {
        private final EnumMap<ChartWindow, WindowBuffer> windows = new EnumMap<>(ChartWindow.class);

        private NetworkHistory() {
            for (ChartWindow window : ChartWindow.values()) {
                windows.put(window, new WindowBuffer(window));
            }
        }

        private void record(
                long gameTime,
                long producedDelta,
                long consumedDelta,
                long currentStock,
                Map<String, Long> itemDeltas,
                Map<String, Long> itemCurrentAmounts
        ) {
            for (WindowBuffer buffer : windows.values()) {
                buffer.record(gameTime, producedDelta, consumedDelta, currentStock, itemDeltas, itemCurrentAmounts);
            }
        }

        private void accumulate(
                ChartWindow window,
                String scopeItemId,
                double[] produced,
                double[] consumed,
                double[] stock,
                boolean[] hasFlow,
                boolean[] hasStock
        ) {
            WindowBuffer buffer = windows.get(window);
            if (buffer == null) {
                return;
            }
            buffer.accumulate(scopeItemId, produced, consumed, stock, hasFlow, hasStock);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_DAY, windows.get(ChartWindow.DAY_24H_5M).save());
            tag.put(TAG_WEEK, windows.get(ChartWindow.WEEK_7D_30M).save());
            tag.put(TAG_DEBUG_10M, windows.get(ChartWindow.DEBUG_10M_5S).save());
            return tag;
        }

        private static NetworkHistory load(CompoundTag tag) {
            NetworkHistory history = new NetworkHistory();
            if (tag.contains(TAG_DAY, Tag.TAG_COMPOUND)) {
                history.windows.put(ChartWindow.DAY_24H_5M, WindowBuffer.load(ChartWindow.DAY_24H_5M, tag.getCompound(TAG_DAY)));
            }
            if (tag.contains(TAG_WEEK, Tag.TAG_COMPOUND)) {
                history.windows.put(ChartWindow.WEEK_7D_30M, WindowBuffer.load(ChartWindow.WEEK_7D_30M, tag.getCompound(TAG_WEEK)));
            }
            if (tag.contains(TAG_DEBUG_10M, Tag.TAG_COMPOUND)) {
                history.windows.put(ChartWindow.DEBUG_10M_5S, WindowBuffer.load(ChartWindow.DEBUG_10M_5S, tag.getCompound(TAG_DEBUG_10M)));
            }
            return history;
        }
    }

    private static final class WindowBuffer {
        private final ChartWindow window;
        private final int size;
        private long latestBucket = Long.MIN_VALUE;
        private final long[] slotBuckets;
        private final long[] produced;
        private final long[] consumed;
        private final long[] stock;
        private final Map<String, ItemSeries> itemSeries = new HashMap<>();

        private WindowBuffer(ChartWindow window) {
            this.window = window;
            this.size = window.bucketCount();
            this.slotBuckets = filledArray(size, Long.MIN_VALUE);
            this.produced = new long[size];
            this.consumed = new long[size];
            this.stock = new long[size];
        }

        private void record(
                long gameTime,
                long producedDelta,
                long consumedDelta,
                long currentStock,
                Map<String, Long> itemDeltas,
                Map<String, Long> itemCurrentAmounts
        ) {
            long bucket = gameTime / window.bucketTicks();
            latestBucket = Math.max(latestBucket, bucket);
            int slot = slotIndex(bucket, size);
            if (slotBuckets[slot] != bucket) {
                slotBuckets[slot] = bucket;
                produced[slot] = 0L;
                consumed[slot] = 0L;
                stock[slot] = 0L;
            }
            produced[slot] += Math.max(0L, producedDelta);
            consumed[slot] += Math.max(0L, consumedDelta);
            stock[slot] = Math.max(0L, currentStock);

            if (itemCurrentAmounts != null && !itemCurrentAmounts.isEmpty()) {
                for (Map.Entry<String, Long> entry : itemCurrentAmounts.entrySet()) {
                    String itemId = entry.getKey();
                    long amount = Math.max(0L, entry.getValue());
                    long delta = itemDeltas == null ? 0L : itemDeltas.getOrDefault(itemId, 0L);
                    long p = Math.max(0L, delta);
                    long c = Math.max(0L, -delta);
                    ItemSeries series = itemSeries.computeIfAbsent(itemId, ignored -> new ItemSeries(size));
                    series.add(bucket, p, c, amount);
                }
            }

            if (itemDeltas != null && !itemDeltas.isEmpty()) {
                for (Map.Entry<String, Long> entry : itemDeltas.entrySet()) {
                    if (itemCurrentAmounts != null && itemCurrentAmounts.containsKey(entry.getKey())) {
                        continue;
                    }
                    long delta = entry.getValue();
                    long p = delta > 0 ? delta : 0L;
                    long c = delta < 0 ? -delta : 0L;
                    if (p == 0L && c == 0L) {
                        continue;
                    }
                    ItemSeries series = itemSeries.computeIfAbsent(entry.getKey(), ignored -> new ItemSeries(size));
                    series.add(bucket, p, c, 0L);
                }
            }

            if (itemSeries.size() > ITEM_PRUNE_THRESHOLD) {
                pruneStaleItemSeries();
            }
        }

        private void accumulate(
                String scopeItemId,
                double[] producedOut,
                double[] consumedOut,
                double[] stockOut,
                boolean[] hasFlowOut,
                boolean[] hasStockOut
        ) {
            if (latestBucket == Long.MIN_VALUE) {
                return;
            }
            ItemSeries scoped = scopeItemId == null ? null : itemSeries.get(scopeItemId);
            for (int i = 0; i < size; i++) {
                long bucket = latestBucket - (size - 1L - i);
                int slot = slotIndex(bucket, size);
                if (slotBuckets[slot] != bucket) {
                    continue;
                }
                if (scoped == null) {
                    producedOut[i] += produced[slot];
                    consumedOut[i] += consumed[slot];
                    stockOut[i] += stock[slot];
                    hasFlowOut[i] = true;
                    hasStockOut[i] = true;
                } else {
                    if (!scoped.hasBucket(bucket)) {
                        continue;
                    }
                    producedOut[i] += scoped.readProduced(bucket);
                    consumedOut[i] += scoped.readConsumed(bucket);
                    stockOut[i] += scoped.readStock(bucket);
                    hasFlowOut[i] = true;
                    hasStockOut[i] = true;
                }
            }
        }

        private void pruneStaleItemSeries() {
            long minValidBucket = latestBucket - size + 1L;
            Iterator<Map.Entry<String, ItemSeries>> iterator = itemSeries.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isStale(minValidBucket)) {
                    iterator.remove();
                }
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_LATEST_BUCKET, latestBucket);
            tag.putLongArray(TAG_SLOT_BUCKETS, slotBuckets);
            tag.putLongArray(TAG_PRODUCED, produced);
            tag.putLongArray(TAG_CONSUMED, consumed);
            tag.putLongArray(TAG_STOCK, stock);

            ListTag items = new ListTag();
            for (Map.Entry<String, ItemSeries> entry : itemSeries.entrySet()) {
                CompoundTag itemTag = entry.getValue().save();
                itemTag.putString(TAG_ITEM_ID, entry.getKey());
                items.add(itemTag);
            }
            tag.put(TAG_ITEMS, items);
            return tag;
        }

        private static WindowBuffer load(ChartWindow window, CompoundTag tag) {
            WindowBuffer buffer = new WindowBuffer(window);
            buffer.latestBucket = tag.getLong(TAG_LATEST_BUCKET);
            copyInto(tag.getLongArray(TAG_SLOT_BUCKETS), buffer.slotBuckets);
            copyInto(tag.getLongArray(TAG_PRODUCED), buffer.produced);
            copyInto(tag.getLongArray(TAG_CONSUMED), buffer.consumed);
            copyInto(tag.getLongArray(TAG_STOCK), buffer.stock);

            if (tag.contains(TAG_ITEMS, Tag.TAG_LIST)) {
                ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    String itemId = itemTag.getString(TAG_ITEM_ID);
                    if (itemId.isEmpty()) {
                        continue;
                    }
                    buffer.itemSeries.put(itemId, ItemSeries.load(buffer.size, itemTag));
                }
            }
            return buffer;
        }
    }

    private static final class ItemSeries {
        private final int size;
        private final long[] slotBuckets;
        private final long[] produced;
        private final long[] consumed;
        private final long[] stock;

        private ItemSeries(int size) {
            this.size = size;
            this.slotBuckets = filledArray(size, Long.MIN_VALUE);
            this.produced = new long[size];
            this.consumed = new long[size];
            this.stock = new long[size];
        }

        private void add(long bucket, long producedDelta, long consumedDelta, long stockValue) {
            int slot = slotIndex(bucket, size);
            if (slotBuckets[slot] != bucket) {
                slotBuckets[slot] = bucket;
                produced[slot] = 0L;
                consumed[slot] = 0L;
                stock[slot] = 0L;
            }
            produced[slot] += producedDelta;
            consumed[slot] += consumedDelta;
            stock[slot] = Math.max(0L, stockValue);
        }

        private long readProduced(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? produced[slot] : 0L;
        }

        private long readConsumed(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? consumed[slot] : 0L;
        }

        private long readStock(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? stock[slot] : 0L;
        }

        private boolean hasBucket(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket;
        }

        private boolean isStale(long minValidBucket) {
            for (long bucket : slotBuckets) {
                if (bucket >= minValidBucket) {
                    return false;
                }
            }
            return true;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLongArray(TAG_SLOT_BUCKETS, slotBuckets);
            tag.putLongArray(TAG_PRODUCED, produced);
            tag.putLongArray(TAG_CONSUMED, consumed);
            tag.putLongArray(TAG_STOCK, stock);
            return tag;
        }

        private static ItemSeries load(int size, CompoundTag tag) {
            ItemSeries series = new ItemSeries(size);
            copyInto(tag.getLongArray(TAG_SLOT_BUCKETS), series.slotBuckets);
            copyInto(tag.getLongArray(TAG_PRODUCED), series.produced);
            copyInto(tag.getLongArray(TAG_CONSUMED), series.consumed);
            copyInto(tag.getLongArray(TAG_STOCK), series.stock);
            return series;
        }
    }

    private static long[] filledArray(int size, long fill) {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = fill;
        }
        return values;
    }

    private static int slotIndex(long bucket, int size) {
        return (int) Math.floorMod(bucket, size);
    }

    private static void copyInto(long[] from, long[] to) {
        int copy = Math.min(from.length, to.length);
        System.arraycopy(from, 0, to, 0, copy);
    }
}
