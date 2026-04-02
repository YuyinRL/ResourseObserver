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

/**
 * 观察者历史数据持久化存储（SavedData）—— 模组图表系统的核心数据层。
 * <p>
 * 存储结构（分层）：
 * - ObserverHistorySavedData：全局容器，映射 historyKey → NetworkHistory
 * - NetworkHistory：单个网络的历史数据，包含多个时间窗口的 WindowBuffer
 * - WindowBuffer：环形缓冲区，按 bucket（时间桶）存储聚合数据
 * - ItemSeries：单个物品在某时间窗口下的环形缓冲区
 * <p>
 * 时间桶（Bucket）机制：
 * - 将游戏 tick 除以 bucketTicks 得到 bucket 编号
 * - 使用 bucket % size 取模映射到数组槽位（环形覆写）
 * - slotBuckets[] 记录每个槽位当前存储的 bucket 编号，用于判断数据有效性
 * <p>
 * 数据存储于主世界（overworld）的 DataStorage 中，跨维度共享。
 */
public class ObserverHistorySavedData extends SavedData {
    private static final String DATA_NAME = "resourceobserver_history";
    // ========== NBT 标签常量 ==========
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
    /** 物品系列数量超过此阈值时触发清理过期数据 */
    private static final int ITEM_PRUNE_THRESHOLD = 2048;

    private static final Factory<ObserverHistorySavedData> FACTORY =
            new Factory<>(ObserverHistorySavedData::new, ObserverHistorySavedData::load);

    /** 所有网络历史数据的映射（historyKey → NetworkHistory） */
    private final Map<String, NetworkHistory> histories = new HashMap<>();

    /** 获取当前世界的历史数据实例（始终存储在主世界） */
    public static ObserverHistorySavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 记录一条采样数据。
     * 将增量数据分发到对应 historyKey 的 NetworkHistory 中的所有时间窗口。
     */
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
        setDirty(); // 标记数据已修改，触发自动保存
    }

    /**
     * 聚合查询多个网络的图表数据。
     * 将多个 historyKey 的数据累加到统一的数组中，
     * 最终转换为 ChartPoint 列表供客户端图表渲染。
     * @param scopeItemId 不为 null 时仅查询该物品的独立数据系列
     */
    public List<ObserverDataPayload.ChartPoint> queryAggregated(
            List<String> keys,
            ChartWindow window,
            String scopeItemId
    ) {
        int pointCount = window.bucketCount();
        // 初始化聚合数组
        double[] produced = new double[pointCount];
        double[] consumed = new double[pointCount];
        double[] stock = new double[pointCount];
        boolean[] hasFlow = new boolean[pointCount];   // 标记该数据点是否有流量数据
        boolean[] hasStock = new boolean[pointCount];  // 标记该数据点是否有库存数据

        // 遍历所有网络，将各自的数据累加到聚合数组
        for (String key : keys) {
            NetworkHistory history = histories.get(key);
            if (history == null) {
                continue;
            }
            history.accumulate(window, scopeItemId, produced, consumed, stock, hasFlow, hasStock);
        }

        // 将聚合数组转换为 ChartPoint 列表，net = produced - consumed
        List<ObserverDataPayload.ChartPoint> result = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double p = produced[i];
            double c = consumed[i];
            result.add(new ObserverDataPayload.ChartPoint(i, p, c, p - c, stock[i], hasFlow[i], hasStock[i]));
        }
        return result;
    }

    /** NBT 保存 —— 序列化所有网络历史数据 */
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

    /** NBT 加载 —— 从存档反序列化恢复历史数据 */
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

    /**
     * 单个网络的历史数据容器。
     * 为每个 ChartWindow 时间窗口维护独立的 WindowBuffer。
     */
    private static final class NetworkHistory {
        /** 各时间窗口的环形缓冲区映射 */
        private final EnumMap<ChartWindow, WindowBuffer> windows = new EnumMap<>(ChartWindow.class);

        /** 构造时为每种时间窗口创建对应的缓冲区 */
        private NetworkHistory() {
            for (ChartWindow window : ChartWindow.values()) {
                windows.put(window, new WindowBuffer(window));
            }
        }

        /** 将采样数据分发到所有时间窗口的缓冲区 */
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

        /** 从指定时间窗口累加数据到输出数组 */
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

        /** NBT 保存 —— 序列化所有时间窗口的缓冲区 */
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_DAY, windows.get(ChartWindow.DAY_24H_5M).save());
            tag.put(TAG_WEEK, windows.get(ChartWindow.WEEK_7D_30M).save());
            tag.put(TAG_DEBUG_10M, windows.get(ChartWindow.DEBUG_10M_5S).save());
            return tag;
        }

        /** NBT 加载 —— 反序列化恢复各时间窗口的缓冲区 */
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

    /**
     * 时间窗口环形缓冲区 —— 历史数据的核心存储结构。
     * <p>
     * 使用固定大小的数组模拟环形缓冲区：
     * - slotBuckets[i]：记录第 i 个槽位当前存储的 bucket 编号
     * - produced[i]/consumed[i]/stock[i]：对应槽位的聚合数据
     * - 新 bucket 到来时，通过取模覆写旧数据，实现自动滚动
     * <p>
     * 同时维护每种物品的独立 ItemSeries，用于支持单物品图表视角。
     */
    private static final class WindowBuffer {
        private final ChartWindow window;
        /** 缓冲区大小（= 时间窗口的 bucket 数量） */
        private final int size;
        /** 最新 bucket 编号，Long.MIN_VALUE 表示尚无数据 */
        private long latestBucket = Long.MIN_VALUE;
        /** 每个槽位对应的 bucket 编号（用于验证数据有效性） */
        private final long[] slotBuckets;
        /** 每个槽位的累计生产量 */
        private final long[] produced;
        /** 每个槽位的累计消耗量 */
        private final long[] consumed;
        /** 每个槽位的最新库存快照 */
        private final long[] stock;
        /** 每种物品的独立数据系列 */
        private final Map<String, ItemSeries> itemSeries = new HashMap<>();

        private WindowBuffer(ChartWindow window) {
            this.window = window;
            this.size = window.bucketCount();
            this.slotBuckets = filledArray(size, Long.MIN_VALUE);
            this.produced = new long[size];
            this.consumed = new long[size];
            this.stock = new long[size];
        }

        /**
         * 记录一条采样数据到缓冲区。
         * <p>
         * 处理流程：
         * 1. 将 gameTime 转换为 bucket 编号
         * 2. 通过取模获取对应槽位，如果是新 bucket 则重置槽位
         * 3. 累加生产/消耗增量，更新库存快照
         * 4. 遍历物品级数据，更新各物品的 ItemSeries
         * 5. 物品数量超过阈值时清理过期数据，防止内存无限增长
         */
        private void record(
                long gameTime,
                long producedDelta,
                long consumedDelta,
                long currentStock,
                Map<String, Long> itemDeltas,
                Map<String, Long> itemCurrentAmounts
        ) {
            // 将游戏 tick 转换为 bucket 编号（向下取整除法）
            long bucket = gameTime / window.bucketTicks();
            latestBucket = Math.max(latestBucket, bucket);
            // 通过取模映射到数组槽位
            int slot = slotIndex(bucket, size);
            // 新 bucket 到来时重置槽位数据
            if (slotBuckets[slot] != bucket) {
                slotBuckets[slot] = bucket;
                produced[slot] = 0L;
                consumed[slot] = 0L;
                stock[slot] = 0L;
            }
            // 累加聚合数据
            produced[slot] += Math.max(0L, producedDelta);
            consumed[slot] += Math.max(0L, consumedDelta);
            stock[slot] = Math.max(0L, currentStock);

            // 记录每种物品的独立数据（来自 itemCurrentAmounts 的物品）
            if (itemCurrentAmounts != null && !itemCurrentAmounts.isEmpty()) {
                for (Map.Entry<String, Long> entry : itemCurrentAmounts.entrySet()) {
                    String itemId = entry.getKey();
                    long amount = Math.max(0L, entry.getValue());
                    long delta = itemDeltas == null ? 0L : itemDeltas.getOrDefault(itemId, 0L);
                    long p = Math.max(0L, delta);   // 正增量 → 生产
                    long c = Math.max(0L, -delta);  // 负增量 → 消耗
                    ItemSeries series = itemSeries.computeIfAbsent(itemId, ignored -> new ItemSeries(size));
                    series.add(bucket, p, c, amount);
                }
            }

            // 记录仅有增量但无当前数量的物品（已完全消耗的物品）
            if (itemDeltas != null && !itemDeltas.isEmpty()) {
                for (Map.Entry<String, Long> entry : itemDeltas.entrySet()) {
                    if (itemCurrentAmounts != null && itemCurrentAmounts.containsKey(entry.getKey())) {
                        continue; // 已在上面处理过
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

            // 物品系列数量超阈值时清理过期数据，防止内存无限增长
            if (itemSeries.size() > ITEM_PRUNE_THRESHOLD) {
                pruneStaleItemSeries();
            }
        }

        /**
         * 将缓冲区数据累加到输出数组。
         * 按时间顺序（从最早到最新 bucket）遍历，将有效槽位的数据累加到对应索引。
         * 若指定了 scopeItemId，则仅读取该物品的 ItemSeries 数据。
         */
        private void accumulate(
                String scopeItemId,
                double[] producedOut,
                double[] consumedOut,
                double[] stockOut,
                boolean[] hasFlowOut,
                boolean[] hasStockOut
        ) {
            if (latestBucket == Long.MIN_VALUE) {
                return; // 无数据
            }
            ItemSeries scoped = scopeItemId == null ? null : itemSeries.get(scopeItemId);
            for (int i = 0; i < size; i++) {
                // 计算第 i 个数据点对应的 bucket 编号（从最早到最新）
                long bucket = latestBucket - (size - 1L - i);
                int slot = slotIndex(bucket, size);
                // 验证槽位中的数据确实属于该 bucket（未被覆写）
                if (slotBuckets[slot] != bucket) {
                    continue;
                }
                if (scoped == null) {
                    // 全局视角：使用聚合数据
                    producedOut[i] += produced[slot];
                    consumedOut[i] += consumed[slot];
                    stockOut[i] += stock[slot];
                    hasFlowOut[i] = true;
                    hasStockOut[i] = true;
                } else {
                    // 单物品视角：使用物品独立系列数据
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

        /** 清理过期物品系列 —— 移除所有槽位 bucket 均早于最小有效 bucket 的系列 */
        private void pruneStaleItemSeries() {
            long minValidBucket = latestBucket - size + 1L;
            Iterator<Map.Entry<String, ItemSeries>> iterator = itemSeries.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isStale(minValidBucket)) {
                    iterator.remove();
                }
            }
        }

        /** NBT 保存 */
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

        /** NBT 加载 */
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

    /**
     * 单物品数据系列 —— 与 WindowBuffer 结构类似的环形缓冲区。
     * 记录单种物品在某时间窗口下的生产、消耗和库存数据。
     * 用于支持图表的单物品视角（ChartScope.ITEM）。
     */
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

        /** 向指定 bucket 添加采样数据（新 bucket 重置，同 bucket 累加） */
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

        /** 读取指定 bucket 的生产数据 */
        private long readProduced(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? produced[slot] : 0L;
        }

        /** 读取指定 bucket 的消耗数据 */
        private long readConsumed(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? consumed[slot] : 0L;
        }

        /** 读取指定 bucket 的库存数据 */
        private long readStock(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket ? stock[slot] : 0L;
        }

        /** 判断指定 bucket 是否有效（槽位数据未过期） */
        private boolean hasBucket(long bucket) {
            int slot = slotIndex(bucket, size);
            return slotBuckets[slot] == bucket;
        }

        /** 判断该系列是否过期（所有槽位的 bucket 都早于 minValidBucket） */
        private boolean isStale(long minValidBucket) {
            for (long bucket : slotBuckets) {
                if (bucket >= minValidBucket) {
                    return false;
                }
            }
            return true;
        }

        /** NBT 保存 */
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLongArray(TAG_SLOT_BUCKETS, slotBuckets);
            tag.putLongArray(TAG_PRODUCED, produced);
            tag.putLongArray(TAG_CONSUMED, consumed);
            tag.putLongArray(TAG_STOCK, stock);
            return tag;
        }

        /** NBT 加载 */
        private static ItemSeries load(int size, CompoundTag tag) {
            ItemSeries series = new ItemSeries(size);
            copyInto(tag.getLongArray(TAG_SLOT_BUCKETS), series.slotBuckets);
            copyInto(tag.getLongArray(TAG_PRODUCED), series.produced);
            copyInto(tag.getLongArray(TAG_CONSUMED), series.consumed);
            copyInto(tag.getLongArray(TAG_STOCK), series.stock);
            return series;
        }
    }

    /** 创建填充指定值的 long 数组 */
    private static long[] filledArray(int size, long fill) {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = fill;
        }
        return values;
    }

    /** 将 bucket 编号映射到数组槽位索引（取模运算，支持负数） */
    private static int slotIndex(long bucket, int size) {
        return (int) Math.floorMod(bucket, size);
    }

    /** 安全复制数组内容（处理长度不一致的情况） */
    private static void copyInto(long[] from, long[] to) {
        int copy = Math.min(from.length, to.length);
        System.arraycopy(from, 0, to, 0, copy);
    }
}
