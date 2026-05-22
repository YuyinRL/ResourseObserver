package com.yuyinrl.resourceobserver.world.history;

import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 高精度短期采样层。
 * <p>
 * 该层只服务 Web detail 图表，采用内存环形缓冲，不持久化到存档。
 */
public final class WebHighPrecisionSampler {
    private static final long ACTIVE_TTL_MS = 10_000L;
    private static final int ITEM_INTERVAL_TICKS = 2;
    private static final int GLOBAL_INTERVAL_TICKS = 5;
    /** 0.25 秒一格 —— 与 GLOBAL_INTERVAL_TICKS 对齐，detail 档可看到亚秒级细节。 */
    private static final int BUCKET_TICKS = 5;
    /** 1200 格 = 5 分钟缓冲，足以支撑 detail 档拖拽。 */
    private static final int BUFFER_BUCKETS = 1200;
    private static final int MAX_SNAPSHOT_ENTRIES = 8192;
    /** 至少累积 16 格（4 秒）才返回高精度数据，避免冷启动误判。 */
    private static final int MIN_VALID_BUCKETS = 16;
    /** 当前后两次采样间隔超过该阈值时，视为旧 snapshot 失效，避免续命后算出错乱速率。 */
    private static final long MAX_RESUME_GAP_TICKS = 60L;

    /** detail 档前端使用的 bucket 长度（秒）—— 暴露给 HistoryHandler。 */
    public static final double BUCKET_SECONDS = BUCKET_TICKS * 0.05d;

    private static final Map<DemandKey, Long> demands = new ConcurrentHashMap<>();
    private static final Map<NetworkKey, NetworkBuffer> buffers = new ConcurrentHashMap<>();

    private WebHighPrecisionSampler() {
    }

    public static void registerDemand(ServerLevel level, BlockPos observerPos, @Nullable String itemId) {
        long expireAt = System.currentTimeMillis() + ACTIVE_TTL_MS;
        demands.put(new DemandKey(observerKey(level, observerPos), normalizeItemId(itemId)), expireAt);
    }

    public static int requestedInterval(ServerLevel level, BlockPos observerPos) {
        ObserverKey observer = observerKey(level, observerPos);
        long now = System.currentTimeMillis();
        boolean globalActive = false;
        boolean itemActive = false;
        for (Map.Entry<DemandKey, Long> entry : demands.entrySet()) {
            if (entry.getValue() <= now) {
                demands.remove(entry.getKey(), entry.getValue());
                continue;
            }
            if (!entry.getKey().observer().equals(observer)) {
                continue;
            }
            if (entry.getKey().itemId() == null) {
                globalActive = true;
            } else {
                itemActive = true;
            }
        }
        if (itemActive) {
            return ITEM_INTERVAL_TICKS;
        }
        return globalActive ? GLOBAL_INTERVAL_TICKS : -1;
    }

    public static boolean allowSnapshotSize(int size) {
        return size <= 0 || size <= MAX_SNAPSHOT_ENTRIES;
    }

    public static void recordSnapshot(
            ServerLevel level,
            BlockPos observerPos,
            String networkId,
            long gameTime,
            Map<String, Long> snapshot
    ) {
        if (snapshot.isEmpty() || snapshot.size() > MAX_SNAPSHOT_ENTRIES) {
            return;
        }
        NetworkKey key = new NetworkKey(observerKey(level, observerPos), networkId);
        buffers.computeIfAbsent(key, ignored -> new NetworkBuffer()).record(gameTime, snapshot);
    }

    public static @Nullable QueryResult query(
            ServerLevel level,
            BlockPos observerPos,
            List<BoundEntry> bindings,
            @Nullable String itemId
    ) {
        ObserverKey observer = observerKey(level, observerPos);
        long latestBucket = level.getGameTime() / BUCKET_TICKS;
        List<Point> points = new ArrayList<>(BUFFER_BUCKETS);
        int validBuckets = 0;
        String normalizedItemId = normalizeItemId(itemId);
        for (int i = 0; i < BUFFER_BUCKETS; i++) {
            long bucket = latestBucket - (BUFFER_BUCKETS - 1L - i);
            BucketAggregate aggregate = new BucketAggregate();
            for (BoundEntry binding : bindings) {
                if (!"AE2_ITEMS".equals(binding.networkType())) {
                    continue;
                }
                NetworkBuffer buffer = buffers.get(new NetworkKey(observer, binding.networkId()));
                if (buffer != null) {
                    buffer.accumulate(bucket, normalizedItemId, aggregate);
                }
            }
            if (aggregate.observedTicks > 0L) {
                validBuckets++;
            }
            points.add(aggregate.toPoint(i, bucket));
        }
        if (validBuckets < MIN_VALID_BUCKETS) {
            return null;
        }
        return new QueryResult(points, latestBucket);
    }

    private static ObserverKey observerKey(ServerLevel level, BlockPos pos) {
        return new ObserverKey(level.dimension().location().toString(), pos.asLong());
    }

    private static @Nullable String normalizeItemId(@Nullable String itemId) {
        return itemId == null || itemId.isBlank() ? null : itemId;
    }

    public record QueryResult(List<Point> points, long latestBucket) {
    }

    public record Point(
            int slotIndex,
            long bucket,
            double producedPerMinute,
            double consumedPerMinute,
            double stock,
            boolean hasFlow,
            boolean hasStock,
            int sampleCount,
            long producedRaw,
            long consumedRaw,
            long observedTicks
    ) {
    }

    private record ObserverKey(String dimension, long pos) {
    }

    private record DemandKey(ObserverKey observer, @Nullable String itemId) {
    }

    private record NetworkKey(ObserverKey observer, String networkId) {
    }

    public static Diagnostics diagnostics(ServerLevel level, BlockPos observerPos) {
        ObserverKey observer = observerKey(level, observerPos);
        long now = System.currentTimeMillis();
        long latestBucket = level.getGameTime() / BUCKET_TICKS;

        boolean globalDemand = false;
        boolean itemDemand = false;
        long maxRemainingMs = 0L;
        for (Map.Entry<DemandKey, Long> entry : demands.entrySet()) {
            if (entry.getValue() <= now || !entry.getKey().observer().equals(observer)) {
                continue;
            }
            long remaining = entry.getValue() - now;
            if (remaining > maxRemainingMs) {
                maxRemainingMs = remaining;
            }
            if (entry.getKey().itemId() == null) {
                globalDemand = true;
            } else {
                itemDemand = true;
            }
        }
        int interval = itemDemand ? ITEM_INTERVAL_TICKS : (globalDemand ? GLOBAL_INTERVAL_TICKS : -1);

        List<NetworkDiagnostics> netDiag = new ArrayList<>();
        for (Map.Entry<NetworkKey, NetworkBuffer> entry : buffers.entrySet()) {
            if (!entry.getKey().observer().equals(observer)) {
                continue;
            }
            NetworkBuffer buffer = entry.getValue();
            int validBuckets = 0;
            int totalSamples = 0;
            List<RecentBucket> recent = new ArrayList<>();
            int recentWanted = 8;
            for (int i = 0; i < BUFFER_BUCKETS; i++) {
                long bucket = latestBucket - (BUFFER_BUCKETS - 1L - i);
                int slot = slotIndex(bucket);
                if (buffer.buckets[slot] != bucket) {
                    continue;
                }
                if (buffer.observedTicks[slot] > 0L) {
                    validBuckets++;
                }
                totalSamples += buffer.sampleCounts[slot];
            }
            for (int i = BUFFER_BUCKETS - 1; i >= 0 && recent.size() < recentWanted; i--) {
                long bucket = latestBucket - (BUFFER_BUCKETS - 1L - i);
                int slot = slotIndex(bucket);
                if (buffer.buckets[slot] != bucket) {
                    continue;
                }
                double producedRate = buffer.observedTicks[slot] > 0L
                        ? (buffer.produced[slot] / (double) buffer.observedTicks[slot]) * 1200.0d : 0.0d;
                double consumedRate = buffer.observedTicks[slot] > 0L
                        ? (buffer.consumed[slot] / (double) buffer.observedTicks[slot]) * 1200.0d : 0.0d;
                recent.add(new RecentBucket(
                        bucket,
                        buffer.sampleCounts[slot],
                        buffer.observedTicks[slot],
                        producedRate,
                        consumedRate,
                        buffer.stock[slot]
                ));
            }
            java.util.Collections.reverse(recent);
            netDiag.add(new NetworkDiagnostics(
                    entry.getKey().networkId(),
                    validBuckets,
                    totalSamples,
                    buffer.previousGameTime,
                    buffer.previousSnapshot.size(),
                    recent
            ));
        }
        return new Diagnostics(
                interval,
                globalDemand,
                itemDemand,
                maxRemainingMs,
                latestBucket,
                BUFFER_BUCKETS,
                BUCKET_TICKS,
                MIN_VALID_BUCKETS,
                netDiag
        );
    }

    public record Diagnostics(
            int requestedIntervalTicks,
            boolean globalDemandActive,
            boolean itemDemandActive,
            long demandRemainingMs,
            long latestBucket,
            int bufferBuckets,
            int bucketTicks,
            int minValidBuckets,
            List<NetworkDiagnostics> networks
    ) {
    }

    public record NetworkDiagnostics(
            String networkId,
            int validBuckets,
            int totalSamples,
            long lastSampleGameTime,
            int previousSnapshotSize,
            List<RecentBucket> recentBuckets
    ) {
    }

    public record RecentBucket(
            long bucket,
            int sampleCount,
            long observedTicks,
            double producedPerMinute,
            double consumedPerMinute,
            long stock
    ) {
    }

    private static final class NetworkBuffer {
        private final long[] buckets = filledLongArray();
        private final long[] produced = new long[BUFFER_BUCKETS];
        private final long[] consumed = new long[BUFFER_BUCKETS];
        private final long[] stock = new long[BUFFER_BUCKETS];
        private final long[] observedTicks = new long[BUFFER_BUCKETS];
        private final int[] sampleCounts = new int[BUFFER_BUCKETS];
        private final Map<String, ItemBuffer> itemBuffers = new ConcurrentHashMap<>();
        private Map<String, Long> previousSnapshot = Map.of();
        private long previousGameTime = Long.MIN_VALUE;

        private void record(long gameTime, Map<String, Long> snapshot) {
            long bucket = gameTime / BUCKET_TICKS;
            int slot = slotIndex(bucket);
            resetIfNeeded(slot, bucket);

            if (!previousSnapshot.isEmpty() && previousGameTime != Long.MIN_VALUE) {
                long elapsedTicks = gameTime - previousGameTime;
                if (elapsedTicks <= 0L || elapsedTicks > MAX_RESUME_GAP_TICKS) {
                    // 间隔过大（采样停摆后续命 / 时间倒退）—— 重置基线，本次只刷新快照。
                    stock[slot] = totalStock(snapshot);
                    previousSnapshot = new LinkedHashMap<>(snapshot);
                    previousGameTime = gameTime;
                    return;
                }
                DeltaSummary summary = summarizeDelta(previousSnapshot, snapshot);
                produced[slot] = saturatingAdd(produced[slot], summary.produced());
                consumed[slot] = saturatingAdd(consumed[slot], summary.consumed());
                observedTicks[slot] = saturatingAdd(observedTicks[slot], elapsedTicks);
                sampleCounts[slot]++;

                for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
                    String itemId = entry.getKey();
                    long current = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
                    long delta = current - Math.max(0L, previousSnapshot.getOrDefault(itemId, 0L));
                    if (delta == 0L && current <= 0L) {
                        continue;
                    }
                    itemBuffers.computeIfAbsent(itemId, ignored -> new ItemBuffer())
                            .record(bucket, delta, current, elapsedTicks);
                }
                for (Map.Entry<String, Long> entry : previousSnapshot.entrySet()) {
                    if (snapshot.containsKey(entry.getKey())) {
                        continue;
                    }
                    long previous = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
                    if (previous <= 0L) {
                        continue;
                    }
                    itemBuffers.computeIfAbsent(entry.getKey(), ignored -> new ItemBuffer())
                            .record(bucket, -previous, 0L, elapsedTicks);
                }
            }

            stock[slot] = totalStock(snapshot);
            previousSnapshot = new LinkedHashMap<>(snapshot);
            previousGameTime = gameTime;
        }

        private void accumulate(long bucket, @Nullable String itemId, BucketAggregate out) {
            int slot = slotIndex(bucket);
            if (buckets[slot] != bucket) {
                return;
            }
            if (itemId == null) {
                out.produced = saturatingAdd(out.produced, produced[slot]);
                out.consumed = saturatingAdd(out.consumed, consumed[slot]);
                out.stock = saturatingAdd(out.stock, stock[slot]);
                // 多网络聚合时 observedTicks 取 MAX 而非 SUM —— 各网络在同一 wall-clock tick
                // 上同步采样，时间不能叠加；produced/consumed 是各网络的 item 数，要相加。
                out.observedTicks = Math.max(out.observedTicks, observedTicks[slot]);
                out.sampleCount = Math.max(out.sampleCount, sampleCounts[slot]);
                return;
            }
            ItemBuffer item = itemBuffers.get(itemId);
            if (item != null) {
                item.accumulate(bucket, out);
            }
        }

        private void resetIfNeeded(int slot, long bucket) {
            if (buckets[slot] == bucket) {
                return;
            }
            buckets[slot] = bucket;
            produced[slot] = 0L;
            consumed[slot] = 0L;
            stock[slot] = 0L;
            observedTicks[slot] = 0L;
            sampleCounts[slot] = 0;
        }
    }

    private static final class ItemBuffer {
        private final long[] buckets = filledLongArray();
        private final long[] produced = new long[BUFFER_BUCKETS];
        private final long[] consumed = new long[BUFFER_BUCKETS];
        private final long[] stock = new long[BUFFER_BUCKETS];
        private final long[] observedTicks = new long[BUFFER_BUCKETS];
        private final int[] sampleCounts = new int[BUFFER_BUCKETS];

        private void record(long bucket, long delta, long currentStock, long elapsedTicks) {
            int slot = slotIndex(bucket);
            if (buckets[slot] != bucket) {
                buckets[slot] = bucket;
                produced[slot] = 0L;
                consumed[slot] = 0L;
                stock[slot] = 0L;
                observedTicks[slot] = 0L;
                sampleCounts[slot] = 0;
            }
            if (delta > 0L) {
                produced[slot] = saturatingAdd(produced[slot], delta);
            } else if (delta < 0L) {
                consumed[slot] = saturatingAdd(consumed[slot], delta == Long.MIN_VALUE ? Long.MAX_VALUE : -delta);
            }
            stock[slot] = Math.max(0L, currentStock);
            observedTicks[slot] = saturatingAdd(observedTicks[slot], elapsedTicks);
            sampleCounts[slot]++;
        }

        private void accumulate(long bucket, BucketAggregate out) {
            int slot = slotIndex(bucket);
            if (buckets[slot] != bucket) {
                return;
            }
            out.produced = saturatingAdd(out.produced, produced[slot]);
            out.consumed = saturatingAdd(out.consumed, consumed[slot]);
            out.stock = saturatingAdd(out.stock, stock[slot]);
            // 同一物品出现在多个网络时同样取 MAX，避免把同一 wall-clock 时间累加多次。
            out.observedTicks = Math.max(out.observedTicks, observedTicks[slot]);
            out.sampleCount = Math.max(out.sampleCount, sampleCounts[slot]);
        }
    }

    private static final class BucketAggregate {
        private long produced;
        private long consumed;
        private long stock;
        private long observedTicks;
        private int sampleCount;

        private Point toPoint(int slotIndex, long bucket) {
            double producedRate = observedTicks > 0L ? (produced / (double) observedTicks) * 1200.0d : 0.0d;
            double consumedRate = observedTicks > 0L ? (consumed / (double) observedTicks) * 1200.0d : 0.0d;
            boolean hasData = observedTicks > 0L;
            return new Point(slotIndex, bucket, producedRate, consumedRate, stock, hasData, hasData, sampleCount,
                    produced, consumed, observedTicks);
        }
    }

    private record DeltaSummary(long produced, long consumed) {
    }

    private static DeltaSummary summarizeDelta(Map<String, Long> previous, Map<String, Long> current) {
        long produced = 0L;
        long consumed = 0L;
        for (Map.Entry<String, Long> entry : current.entrySet()) {
            long now = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            long before = Math.max(0L, previous.getOrDefault(entry.getKey(), 0L));
            long delta = now - before;
            if (delta > 0L) {
                produced = saturatingAdd(produced, delta);
            } else if (delta < 0L) {
                consumed = saturatingAdd(consumed, delta == Long.MIN_VALUE ? Long.MAX_VALUE : -delta);
            }
        }
        for (Map.Entry<String, Long> entry : previous.entrySet()) {
            if (current.containsKey(entry.getKey())) {
                continue;
            }
            long before = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            consumed = saturatingAdd(consumed, before);
        }
        return new DeltaSummary(produced, consumed);
    }

    private static long totalStock(Map<String, Long> snapshot) {
        long total = 0L;
        for (Long value : snapshot.values()) {
            total = saturatingAdd(total, Math.max(0L, value == null ? 0L : value));
        }
        return total;
    }

    private static int slotIndex(long bucket) {
        return Math.floorMod(bucket, BUFFER_BUCKETS);
    }

    private static long[] filledLongArray() {
        long[] out = new long[BUFFER_BUCKETS];
        java.util.Arrays.fill(out, Long.MIN_VALUE);
        return out;
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0L) {
            return Long.MAX_VALUE;
        }
        return result;
    }
}
