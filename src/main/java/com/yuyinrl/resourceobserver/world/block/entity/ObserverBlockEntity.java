package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.networking.IGrid;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
import com.yuyinrl.resourceobserver.world.history.WebHighPrecisionSampler;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import com.yuyinrl.resourceobserver.world.block.entity.debug.ObserverDebugMessageBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 观察者方块实体 —— 模组数据采集的核心逻辑所在。
 * <p>
 * 职责：
 * 1. 存储一个或多个网络绑定（BoundEntry），每个绑定对应一个受监控的资源网络
 * 2. 按固定间隔（SAMPLE_INTERVAL tick）对所有绑定的网络进行数据采样
 * 3. 根据网络类型分别调用 AE2 API 或 NeoForge 能量 API 读取当前数据
 * 4. 计算生产增量（delta > 0）和消耗增量（delta < 0）
 * 5. 将采样结果记录到 HistoryRecorder 用于历史图表展示
 * 6. 通过 NBT 持久化所有绑定和统计数据，支持存档保存/加载
 */
public class ObserverBlockEntity extends BlockEntity {
    // ========== NBT 标签常量 ==========
    private static final String TAG_BINDINGS = "bindings";
    private static final String TAG_NETWORK_TYPE = "network_type";
    private static final String TAG_NETWORK_ID = "network_id";
    private static final String TAG_TARGET_BLOCK = "target_block";
    private static final String TAG_STATS = "stats";
    private static final String TAG_CURRENT_VALUE = "current_value";
    private static final String TAG_CAPACITY = "capacity";
    private static final String TAG_TOTAL_PRODUCED = "total_produced";
    private static final String TAG_TOTAL_CONSUMED = "total_consumed";
    private static final String TAG_PREV_VALUE = "prev_value";
    /** Observer 放置者 UUID 的 NBT 键（可空：升级前的老存档无此字段）。 */
    private static final String TAG_OWNER = "owner";

    // 常量已收敛到 Ae2Sampler（SAMPLE_INTERVAL 等），容量作用域常量见 Ae2CellProber.AE2_CAPACITY_SCOPE_CELLS_ONLY。

    /** 当前所有网络绑定列表 */
    private final List<BoundEntry> bindings = new ArrayList<>();
    final Ae2DataStore dataStore = new Ae2DataStore();
    /** 每个网络 ID 对应的统计数据 */
    private final Map<String, BindingStats> statsMap = new HashMap<>();
    /** Web detail 图表专用高精度采样的上次采样 tick。 */
    private long lastWebHighPrecisionSampleTick = -1L;
    /** 调试信息映射，记录每个网络的采样状态 */
    final Map<String, String> debugInfoMap = new HashMap<>();
    /** AE2 拓扑解析与容量告警限流器 */
    private final Ae2GridResolver gridResolver = new Ae2GridResolver();
    /** AE2 采样器（持有 dataStore + gridResolver + debugInfoMap，避免反向回调） */
    private final Ae2Sampler sampler = new Ae2Sampler(dataStore, gridResolver, debugInfoMap);
    /** 上次采样的游戏时间，用于控制采样间隔 */
    private long lastSampleTick = -1;

    /** 放置该 Observer 的玩家 UUID；为空表示老存档迁移过来的"无主"方块。 */
    @Nullable
    private UUID ownerUuid = null;

    /** Flux Networks 采样数据容器（v0.8.1 模块化：替代原 fluxSampleResults Map）。 */
    final FluxDataStore fluxStore = new FluxDataStore();
    final FluxSampler fluxSampler = new FluxSampler();

    /** 活跃的 Observer 方块实体注册表 —— 供 Web API 枚举使用。弱引用集合，避免阻止卸载。 */
    private static final Set<ObserverBlockEntity> LOADED =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 返回当前所有已加载的 Observer（副本快照）。 */
    public static List<ObserverBlockEntity> loadedObservers() {
        return new ArrayList<>(LOADED);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    /**
     * 网络绑定条目 —— 记录一个已绑定的资源网络的基本信息。
     * @param networkType  网络类型标识（如 "AE2_ITEMS"、"FLUX_ENERGY"）
     * @param networkId    网络唯一标识（格式：方块ID@方块坐标长整型编码）
     * @param targetBlockId 目标方块的注册 ID（如 "ae2:controller"）
     */
    public ObserverBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OBSERVER_BLOCK_ENTITY.get(), pos, blockState);
    }

    /**
     * 添加一个新的网络绑定。
     * 如果该 networkId 已存在则拒绝添加（防止重复绑定）。
     * @return 是否成功添加
     */
    public boolean addBinding(String networkType, String networkId, ResourceLocation targetBlockId) {
        for (BoundEntry entry : bindings) {
            if (entry.networkId().equals(networkId)) {
                return false;
            }
            if ("AE2_ITEMS".equals(networkType)
                    && "AE2_ITEMS".equals(entry.networkType())
                    && isSameAe2Grid(networkId, entry.networkId())) {
                return false;
            }
        }
        bindings.add(new BoundEntry(networkType, networkId, targetBlockId.toString()));
        setChanged();
        syncConnectedState();
        return true;
    }

    /** 清除所有绑定及关联的统计数据、物品快照和调试信息 */
    public void clearAllBindings() {
        bindings.clear();
        statsMap.clear();
        dataStore.clearAll();
        fluxStore.clearAll();
        setChanged();
        syncConnectedState();
    }

    /** 同步方块的 CONNECTED 状态与当前绑定状态一致 */
    private void syncConnectedState() {
        if (level != null && !level.isClientSide) {
            boolean connected = !bindings.isEmpty();
            BlockState current = getBlockState();
            if (current.getValue(ObserverBlock.CONNECTED) != connected) {
                level.setBlock(worldPosition, current.setValue(ObserverBlock.CONNECTED, connected), 3);
            }
        }
    }

    /** 判断观察者是否已绑定至少一个网络 */
    public boolean isBound() {
        return !bindings.isEmpty();
    }

    /** 返回所有绑定条目的不可变副本 */
    public List<BoundEntry> getBindings() {
        return List.copyOf(bindings);
    }

    public int getBindingCount() {
        return bindings.size();
    }

    public BindingStats getStatsFor(String networkId) {
        return statsMap.getOrDefault(networkId, BindingStats.empty());
    }

    public Map<String, Long> getAe2ItemAmountsFor(String networkId) {
        return dataStore.getItemAmounts(networkId);
    }

    public Map<String, Long> getAe2ItemDeltasFor(String networkId) {
        return dataStore.getItemDeltas(networkId);
    }

    /**
     * 返回指定 AE2 网络中每种物品的平滑净速率（单位：物品/分钟，正=净生产，负=净消耗）。
     */
    public Map<String, Double> getAe2ItemRatesPerMinFor(String networkId) {
        return dataStore.getItemRatesPerMin(networkId);
    }

    /**
     * 返回指定 AE2 网络中每种物品的平滑生产速率（单位：物品/分钟，≥0）。
     */
    public Map<String, Double> getAe2ItemProdRatesPerMinFor(String networkId) {
        return dataStore.getItemProdRatesPerMin(networkId);
    }

    /**
     * 返回指定 AE2 网络中每种物品的平滑消耗速率（单位：物品/分钟，≥0）。
     */
    public Map<String, Double> getAe2ItemConsRatesPerMinFor(String networkId) {
        return dataStore.getItemConsRatesPerMin(networkId);
    }

    /**
     * 计算指定网络绑定的实时 KPI 统计数据。
     * <p>
     * 使用与逐物品速率相同的双缓冲滑动窗口（每窗口 12 秒），
     * 合并当前窗口 + 上一完整窗口计算平滑速率（12–24 秒响应延迟）。
     * 不依赖 {@code ObserverHistorySavedData} 的环形缓冲区。
     */
    public ObserverDataPayload.KpiWindowStats getKpiStats(String networkId) {
        return dataStore.computeKpiStats(networkId, Ae2Sampler.SAMPLE_INTERVAL);
    }

    public String getDebugInfoFor(String networkId) {
        return debugInfoMap.getOrDefault(networkId, "no debug data yet");
    }

    public Ae2CellCapacityMetrics getAe2CellCapacityMetricsFor(String networkId) {
        return dataStore.getCellCapacityMetrics(networkId);
    }

    /**
     * 获取指定网络 ID 的 Flux Networks 采样结果。
     *
     * @param networkId 网络标识
     * @return Flux 采样结果，如果不存在或非 Flux 网络则返回 null
     */
    @Nullable
    public FluxNetworksIntegration.FluxSampleResult getFluxSampleResultFor(String networkId) {
        return fluxStore.get(networkId);
    }

    public @Nullable IGrid resolveAe2GridForNetwork(String networkId) {
        return gridResolver.resolveForNetwork(level, networkId);
    }

    /**
     * 服务端 tick 回调 —— 驱动周期性采样。
     * 仅在服务端执行，跳过客户端和无绑定的情况。
     * 按照 SAMPLE_INTERVAL 控制采样频率，防止每 tick 都执行。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, ObserverBlockEntity be) {
        if (level.isClientSide || be.bindings.isEmpty()) return;
        long gameTime = level.getGameTime();
        boolean mainSampleDue = be.lastSampleTick < 0 || gameTime - be.lastSampleTick >= Ae2Sampler.SAMPLE_INTERVAL;
        if (level instanceof ServerLevel serverLevel) {
            int webInterval = WebHighPrecisionSampler.requestedInterval(serverLevel, pos);
            // 高精度采样按自身节奏触发，不再因主采样到期就跳过。
            // 之前的 !mainSampleDue 约束会让 HP 每隔一个 bucket 漏一次采样，
            // 导致 detail 档速率被系统性低估约 50%（1920 ≈ 3840/2）。
            boolean webSampleDue = webInterval > 0
                    && (be.lastWebHighPrecisionSampleTick < 0
                    || gameTime - be.lastWebHighPrecisionSampleTick >= webInterval);
            if (webSampleDue) {
                be.lastWebHighPrecisionSampleTick = gameTime;
                be.sampleWebHighPrecision(serverLevel, gameTime);
            }
        }
        if (!mainSampleDue) return;
        be.lastSampleTick = gameTime;
        be.sampleAll();
    }

    /** Web detail 图表专用的短期高精度采样，不写入持久化历史。 */
    private void sampleWebHighPrecision(ServerLevel serverLevel, long gameTime) {
        if (level == null) return;
        for (BoundEntry binding : bindings) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                continue;
            }
            int cachedSize = dataStore.cachedSnapshotSize(binding.networkId());
            if (cachedSize > 0 && !WebHighPrecisionSampler.allowSnapshotSize(cachedSize)) {
                continue;
            }
            BlockPos targetPos = extractPos(binding.networkId());
            if (targetPos == null || !level.isLoaded(targetPos)) {
                continue;
            }
            Ae2ReadResult readResult = sampler.readAe2NetworkItems(level, targetPos);
            Map<String, Long> snapshot = readResult.snapshot();
            if (snapshot == null || !WebHighPrecisionSampler.allowSnapshotSize(snapshot.size())) {
                continue;
            }
            WebHighPrecisionSampler.recordSnapshot(serverLevel, worldPosition, binding.networkId(), gameTime, snapshot);
        }
    }

    /**
     * 对所有绑定的网络执行一次采样。
     * 根据网络类型分发到对应的采样方法（AE2 或能量），
     * 计算生产/消耗增量后记录到历史数据中。
     */
    private void sampleAll() {
        if (level == null) return;
        for (BoundEntry binding : bindings) {
            // 从 networkId 中解析目标方块坐标
            BlockPos targetPos = extractPos(binding.networkId());
            if (targetPos == null || !level.isLoaded(targetPos)) continue;

            BindingStats oldStats = statsMap.getOrDefault(binding.networkId(), BindingStats.empty());
            BindingStats newStats;
            Map<String, Long> itemDeltaSnapshot = Map.of();
            Map<String, Long> itemAmountSnapshot = Map.of();

            // 根据网络类型选择不同的采样策略
            if ("AE2_ITEMS".equals(binding.networkType())) {
                newStats = sampler.sample(level, binding.networkId(), targetPos, oldStats);
                itemDeltaSnapshot = getAe2ItemDeltasFor(binding.networkId());
                itemAmountSnapshot = getAe2ItemAmountsFor(binding.networkId());
            } else if ("FLUX_ENERGY".equals(binding.networkType())) {
                FluxSampler.SampleOutput out = fluxSampler.sample(this, binding, targetPos, oldStats);
                newStats = out.newStats();
                itemDeltaSnapshot = out.itemDeltaSnapshot();
                itemAmountSnapshot = out.itemAmountSnapshot();
                debugInfoMap.put(binding.networkId(), out.debugInfo());
            } else {
                continue; // 不支持的网络类型，跳过
            }
            statsMap.put(binding.networkId(), newStats);

            // 将采样增量数据记录到世界级历史存储中（用于图表展示）
            if (level instanceof ServerLevel serverLevel) {
                long producedDelta = Math.max(0L, newStats.totalProduced() - oldStats.totalProduced());
                long consumedDelta = Math.max(0L, newStats.totalConsumed() - oldStats.totalConsumed());
                HistoryRecorder.recordSample(
                        serverLevel, worldPosition, binding,
                        producedDelta, consumedDelta, newStats.currentValue(),
                        itemDeltaSnapshot, itemAmountSnapshot
                );
            }
        }
        setChanged(); // 标记方块实体已修改，触发 NBT 保存
    }

    /**
     * 采样 AE2 物品存储网络（保留为内部 API，便于将来追加跨绑定后处理）。
     */
    private BindingStats sampleAe2Network(String networkId, BlockPos targetPos, BindingStats oldStats) {
        return sampler.sample(level, networkId, targetPos, oldStats);
    }

    /**
     * AE2 网络读取结果。
     * @param snapshot  物品快照（itemId -> amount），null 表示读取失败
     * @param debugInfo 调试信息字符串
     */
    record Ae2ReadResult(
            @Nullable Map<String, Long> snapshot,
            String debugInfo,
            Ae2CellCapacityMetrics cellCapacityMetrics
    ) {
    }

    record Ae2CapacityReadResult(
            Ae2CellCapacityMetrics metrics,
            String debugSuffix
    ) {
    }

    private boolean isSameAe2Grid(String leftNetworkId, String rightNetworkId) {
        return gridResolver.isSameGrid(level, leftNetworkId, rightNetworkId);
    }

    /** Flux 采样器使用的访问点：清理 AE2 容量告警签名缓存（Flux 切换后调用）。 */
    void clearCapacityWarnSignature(String networkId) {
        gridResolver.clearCapacityWarnSignature(networkId);
    }

    /**
     * 从 networkId 字符串中解析目标方块的坐标，委托 {@link NetworkRef#extractPos(String)}。
     */
    private static BlockPos extractPos(String networkId) {
        return NetworkRef.extractPos(networkId);
    }

    /**
     * 创建观察者状态消息组件，用于玩家右键查看时显示。
     * 未绑定时显示"未绑定"，已绑定时列出所有绑定的网络类型和目标方块。
     */
    public Component createStatusMessage() {
        if (bindings.isEmpty()) {
            return Component.translatable("message.resourceobserver.observer_status_unbound");
        }

        MutableComponent msg = Component.translatable(
                "message.resourceobserver.observer_status_header", bindings.size());

        for (int i = 0; i < bindings.size(); i++) {
            BoundEntry entry = bindings.get(i);
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable(
                    "message.resourceobserver.observer_status_entry",
                    i + 1, entry.networkType(), entry.targetBlockId()));
        }
        return msg;
    }

    public List<Component> createDebugStatusMessages() {
        return ObserverDebugMessageBuilder.build(this);
    }

    /**
     * NBT 保存 —— 将所有绑定和统计数据序列化到 CompoundTag。
     * 每个绑定条目包含网络类型、网络ID、目标方块ID 和对应的统计数据。
     */
    /** 返回放置者 UUID；老存档可能为 {@code null}，调用方需用 legacyObserverPolicy 处理。 */
    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /** 设置放置者 UUID —— 由 {@code ObserverBlock#setPlacedBy} 在玩家放下方块时调用。 */
    public void setOwnerUuid(@Nullable UUID owner) {
        if (java.util.Objects.equals(this.ownerUuid, owner)) return;
        this.ownerUuid = owner;
        setChanged();
    }

    /**
     * 是否允许指定查看者访问本 Observer。
     * <p>规则：admin 总是允许；ownerUuid 与 viewer 匹配允许；否则按 legacyAllowsAnyone 决定。
     * 当 ownerUuid 为空（老存档迁移）时使用 legacyAllowsAnyone 决定 —— 由调用方读取
     * {@link com.yuyinrl.resourceobserver.web.WebServerConfig#LEGACY_OBSERVER_POLICY} 后传入。</p>
     */
    public boolean canView(@Nullable UUID viewer, boolean admin, boolean legacyAllowsAnyone) {
        if (admin) return true;
        if (this.ownerUuid == null) return legacyAllowsAnyone;
        return this.ownerUuid.equals(viewer);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
        ListTag listTag = new ListTag();
        for (BoundEntry entry : bindings) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_NETWORK_TYPE, entry.networkType());
            entryTag.putString(TAG_NETWORK_ID, entry.networkId());
            entryTag.putString(TAG_TARGET_BLOCK, entry.targetBlockId());
            BindingStats stats = statsMap.get(entry.networkId());
            if (stats != null) {
                CompoundTag statsTag = new CompoundTag();
                statsTag.putLong(TAG_CURRENT_VALUE, stats.currentValue());
                statsTag.putLong(TAG_CAPACITY, stats.capacity());
                statsTag.putLong(TAG_TOTAL_PRODUCED, stats.totalProduced());
                statsTag.putLong(TAG_TOTAL_CONSUMED, stats.totalConsumed());
                statsTag.putLong(TAG_PREV_VALUE, stats.previousValue());
                entryTag.put(TAG_STATS, statsTag);
            }
            listTag.add(entryTag);
        }
        tag.put(TAG_BINDINGS, listTag);
    }

    /**
     * NBT 加载 —— 从 CompoundTag 反序列化恢复绑定和统计数据。
     * 在区块加载或存档读取时调用，完全重建运行时状态。
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bindings.clear();
        statsMap.clear();
        dataStore.clearAll();
        fluxStore.clearAll();
        if (tag.hasUUID(TAG_OWNER)) {
            ownerUuid = tag.getUUID(TAG_OWNER);
        } else {
            ownerUuid = null;
        }
        if (tag.contains(TAG_BINDINGS, Tag.TAG_LIST)) {
            ListTag listTag = tag.getList(TAG_BINDINGS, Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag entryTag = listTag.getCompound(i);
                String networkId = entryTag.getString(TAG_NETWORK_ID);
                bindings.add(new BoundEntry(
                        entryTag.getString(TAG_NETWORK_TYPE),
                        networkId,
                        entryTag.getString(TAG_TARGET_BLOCK)));
                if (entryTag.contains(TAG_STATS, Tag.TAG_COMPOUND)) {
                    CompoundTag statsTag = entryTag.getCompound(TAG_STATS);
                    statsMap.put(networkId, new BindingStats(
                            statsTag.getLong(TAG_CURRENT_VALUE),
                            statsTag.getLong(TAG_CAPACITY),
                            statsTag.getLong(TAG_TOTAL_PRODUCED),
                            statsTag.getLong(TAG_TOTAL_CONSUMED),
                            statsTag.getLong(TAG_PREV_VALUE)));
                }
            }
        }
    }
}
