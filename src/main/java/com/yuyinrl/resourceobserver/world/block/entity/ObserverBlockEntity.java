package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.AECapabilities;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.IBasicCellItem;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
import com.yuyinrl.resourceobserver.world.history.WebHighPrecisionSampler;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** 采样间隔（单位：游戏 tick），10 tick = 0.5 秒 */
    private static final int SAMPLE_INTERVAL = 10;
    /** 双缓冲滑动窗口的每个子窗口采样数（12 秒），两个子窗口合计覆盖 12–24 秒的历史数据 */
    private static final int RATE_WINDOW_SAMPLES = 24;
    /**
     * 启动预热采样次数 —— 首次采样后额外跳过的样本数。
     * 等待 AE2 缓存库存从部分加载变为完全加载，避免大量物品"突然出现"导致虚假增量。
     * 4 次 × 0.5 秒 = 2 秒预热期。
     */
    private static final int WARMUP_SAMPLES = 4;
    /**
     * 速率 DEBUG 开关 —— 启动 JVM 时加 -Dresobs.ratedebug=true 可启用。
     * 启用后每个窗口周期（12 秒）输出一条 [RateDebug] 日志，用于诊断速率偏差。
     */
    private static final boolean RATE_DEBUG = Boolean.getBoolean("resobs.ratedebug");
    private static final String AE2_CAPACITY_SCOPE_CELLS_ONLY = "AE2_CELLS_ONLY";
    private static final String FLUID_KEY_PREFIX = "fluid:";

    /** 当前所有网络绑定列表 */
    private final List<BoundEntry> bindings = new ArrayList<>();
    final Ae2DataStore dataStore = new Ae2DataStore();
    private final Ae2Sampler sampler = new Ae2Sampler();
    /** 每个网络 ID 对应的统计数据 */
    private final Map<String, BindingStats> statsMap = new HashMap<>();
    /** Web detail 图表专用高精度采样的上次采样 tick。 */
    private long lastWebHighPrecisionSampleTick = -1L;
    /**
     * AE2 网络中每种物品的平滑净速率（networkId -> {itemId -> ratePerMin}），单位：每分钟。
     * 由双缓冲滑动窗口计算得出，正值=净生产，负值=净消耗。
     */
    /** AE2 网络中每种物品的平滑生产速率（networkId -> {itemId -> prodRatePerMin}），单位：每分钟，≥0 */
    private final Map<String, Map<String, Double>> ae2ItemProdRatesPerMin = new HashMap<>();
    /** AE2 网络中每种物品的平滑消耗速率（networkId -> {itemId -> consRatePerMin}），单位：每分钟，≥0 */
    private final Map<String, Map<String, Double>> ae2ItemConsRatesPerMin = new HashMap<>();
    /** 服务端速率 EMA 状态 —— 平滑窗口噪声，α=0.10 */
    private final Map<String, Map<String, Double>> ae2ItemProdRateEma = new HashMap<>();
    private final Map<String, Map<String, Double>> ae2ItemConsRateEma = new HashMap<>();
    private static final double SERVER_RATE_EMA_ALPHA = 0.10;
    // ── 双缓冲滑动窗口字段 ────────────────────────────────────────────────────────
    /** 当前窗口内每物品累计消耗量 */
    private final Map<String, Map<String, Long>> ae2ItemConsCurrent  = new HashMap<>();
    /** 当前窗口内每物品累计生产量 */
    private final Map<String, Map<String, Long>> ae2ItemProdCurrent  = new HashMap<>();
    /** 上一完整窗口的每物品累计消耗量 */
    private final Map<String, Map<String, Long>> ae2ItemConsPrev     = new HashMap<>();
    /** 上一完整窗口的每物品累计生产量 */
    private final Map<String, Map<String, Long>> ae2ItemProdPrev     = new HashMap<>();
    /** 当前窗口已累积的采样数（networkId -> count） */
    private final Map<String, Integer> ae2ItemWindowSampleCount      = new HashMap<>();
    /** 上一完整窗口的采样数（networkId -> count） */
    private final Map<String, Integer> ae2ItemPrevWindowSampleCount  = new HashMap<>();
    /** 启动预热计数器 —— 跳过前几次采样，等待 AE2 缓存库存稳定后再开始累积增量 */
    private final Map<String, Integer> ae2WarmupRemaining            = new HashMap<>();
    // ── KPI 聚合双缓冲（实时速率，不依赖 HistorySavedData 环形缓冲区） ────────
    /** 上一完整窗口 KPI 聚合 */
    private final Map<String, long[]> kpiAccumPrev = new HashMap<>();
    /** 调试信息映射，记录每个网络的采样状态 */
    final Map<String, String> debugInfoMap = new HashMap<>();
    /** 容量告警签名（networkId -> last signature），避免重复刷日志 */
    private final Map<String, String> capacityWarnSignatureMap = new HashMap<>();
    /** 容量告警时间戳（networkId -> 上次输出 WARN 的 System.nanoTime()），用于频率限制 */
    private final Map<String, Long> capacityWarnTimestampMap = new HashMap<>();
    /** 容量告警日志最小间隔（纳秒），同一网络 5 分钟内最多输出一次 WARN */
    private static final long CAPACITY_WARN_INTERVAL_NS = 5L * 60L * 1_000_000_000L;
    /** 上次采样的游戏时间，用于控制采样间隔 */
    private long lastSampleTick = -1;

    /** Flux Networks 采样结果缓存（networkId -> FluxSampleResult） */
    private final Map<String, FluxNetworksIntegration.FluxSampleResult> fluxSampleResults = new HashMap<>();

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
    public record BoundEntry(String networkType, String networkId, String targetBlockId) {
    }

    public record Ae2CellCapacityMetrics(
            long itemUsedBytes,
            long itemTotalBytes,
            long itemUsedTypes,
            long itemTotalTypes,
            long itemUsedUnits,
            long itemMaxUnits,
            long fluidUsedBytes,
            long fluidTotalBytes,
            long fluidUsedTypes,
            long fluidTotalTypes,
            long fluidUsedUnits,
            long fluidMaxUnits,
            long externalItemUsedUnits,
            long externalItemTotalUnits,
            long externalFluidUsedUnits,
            long externalFluidTotalUnits,
            String scope,
            boolean reliable,
            boolean available,
            boolean externalReliable,
            boolean externalAvailable
    ) {
        public static Ae2CellCapacityMetrics unavailable() {
            return new Ae2CellCapacityMetrics(
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    AE2_CAPACITY_SCOPE_CELLS_ONLY,
                    false,
                    false,
                    false,
                    false
            );
        }
    }


    /**
     * 绑定统计数据 —— 记录某个网络绑定的累计采样结果。
     * 采用不可变 record，每次采样生成新的实例。
     * @param currentValue  当前值（物品总量/能量存储量）
     * @param capacity      容量/种类数
     * @param totalProduced 累计生产量（所有增量之和）
     * @param totalConsumed 累计消耗量（所有减量绝对值之和）
     * @param previousValue 上次采样值，用于计算 delta（-1 表示首次采样）
     */
    public record BindingStats(
            long currentValue,
            long capacity,
            long totalProduced,
            long totalConsumed,
            long previousValue
    ) {
        /**
         * 根据新采样值计算更新后的统计数据（简单差值模式）。
         * 首次采样（previousValue < 0）时不计入生产/消耗，只记录基准值。
         * 正差值计入生产量，负差值绝对值计入消耗量。
         */
        public BindingStats withSample(long newValue, long newCapacity) {
            if (previousValue < 0) {
                return new BindingStats(newValue, newCapacity, 0, 0, newValue);
            }
            long delta = newValue - previousValue;
            long produced = totalProduced;
            long consumed = totalConsumed;
            if (delta > 0) {
                produced += delta;
            } else if (delta < 0) {
                consumed += (-delta);
            }
            return new BindingStats(newValue, newCapacity, produced, consumed, newValue);
        }

        /**
         * 根据已计算好的生产/消耗增量更新统计数据（AE2 逐物品统计模式）。
         * 增量值强制非负，避免负增量污染累计数据。
         */
        public BindingStats withDelta(long newValue, long newCapacity, long producedDelta, long consumedDelta) {
            return new BindingStats(
                    newValue,
                    newCapacity,
                    totalProduced + Math.max(0, producedDelta),
                    totalConsumed + Math.max(0, consumedDelta),
                    newValue
            );
        }

        /** 创建空的初始统计，previousValue = -1 表示尚未进行首次采样 */
        public static BindingStats empty() {
            return new BindingStats(0, 0, 0, 0, -1);
        }
    }

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
        fluxSampleResults.clear();
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
        return dataStore.computeKpiStats(networkId, SAMPLE_INTERVAL);
    }

    public String getDebugInfoFor(String networkId) {
        return debugInfoMap.getOrDefault(networkId, "no debug data yet");
    }

    public Ae2CellCapacityMetrics getAe2CellCapacityMetricsFor(String networkId) {
        return dataStore.cellCapacityMetrics.getOrDefault(networkId, Ae2CellCapacityMetrics.unavailable());
    }

    /**
     * 获取指定网络 ID 的 Flux Networks 采样结果。
     *
     * @param networkId 网络标识
     * @return Flux 采样结果，如果不存在或非 Flux 网络则返回 null
     */
    @Nullable
    public FluxNetworksIntegration.FluxSampleResult getFluxSampleResultFor(String networkId) {
        return fluxSampleResults.get(networkId);
    }

    public @Nullable IGrid resolveAe2GridForNetwork(String networkId) {
        if (level == null) {
            return null;
        }
        BlockPos targetPos = extractPos(networkId);
        if (targetPos == null || !level.isLoaded(targetPos)) {
            return null;
        }
        return resolveAe2GridAt(targetPos);
    }

    /**
     * 服务端 tick 回调 —— 驱动周期性采样。
     * 仅在服务端执行，跳过客户端和无绑定的情况。
     * 按照 SAMPLE_INTERVAL 控制采样频率，防止每 tick 都执行。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, ObserverBlockEntity be) {
        if (level.isClientSide || be.bindings.isEmpty()) return;
        long gameTime = level.getGameTime();
        boolean mainSampleDue = be.lastSampleTick < 0 || gameTime - be.lastSampleTick >= SAMPLE_INTERVAL;
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
            Map<String, Long> cached = dataStore.itemAmounts.get(binding.networkId());
            if (cached != null && !WebHighPrecisionSampler.allowSnapshotSize(cached.size())) {
                continue;
            }
            BlockPos targetPos = extractPos(binding.networkId());
            if (targetPos == null || !level.isLoaded(targetPos)) {
                continue;
            }
            Ae2ReadResult readResult = readAe2NetworkItems(targetPos);
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
                newStats = sampleAe2Network(binding.networkId(), targetPos, oldStats);
                itemDeltaSnapshot = getAe2ItemDeltasFor(binding.networkId());
                itemAmountSnapshot = getAe2ItemAmountsFor(binding.networkId());
            } else if ("FLUX_ENERGY".equals(binding.networkType())) {
                // 优先使用 Flux Networks 原生 API 获取丰富的网络数据
                if (FluxNetworksIntegration.isAvailable()) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    FluxNetworksIntegration.FluxSampleResult fluxResult = FluxNetworksIntegration.readFluxNetwork(targetBe);
                    if (fluxResult != null) {
                        fluxSampleResults.put(binding.networkId(), fluxResult);
                        // 使用 Flux API 的真实每 tick 输入/输出作为生产/消耗增量
                        long inputPerTick = fluxResult.energyInput();
                        long outputPerTick = fluxResult.energyOutput();
                        // 容量使用 totalMaxEnergyStorage（储能方块的真实最大容量），
                        // 回退到 totalBuffer（非储能设备缓冲合计）+ totalEnergy（储能设备当前值）
                        long capacity = fluxResult.totalMaxEnergyStorage();
                        if (capacity <= 0L) {
                            capacity = fluxResult.totalBuffer() + fluxResult.totalEnergy();
                        }
                        newStats = oldStats.withDelta(
                                fluxResult.totalEnergy(),
                                capacity,
                                inputPerTick,
                                outputPerTick
                        );
                        // 构建能量指标的增量/数量快照（供 HistoryRecorder 使用）
                        itemDeltaSnapshot = Map.of(
                                "flux.input_per_tick", inputPerTick,
                                "flux.output_per_tick", outputPerTick
                        );
                        itemAmountSnapshot = Map.ofEntries(
                                Map.entry("flux.input_per_tick", inputPerTick),
                                Map.entry("flux.output_per_tick", outputPerTick),
                                Map.entry("flux.energy_stored", fluxResult.totalEnergy()),
                                Map.entry("flux.total_buffer", fluxResult.totalBuffer()),
                                Map.entry("flux.max_energy_storage", fluxResult.totalMaxEnergyStorage()),
                                Map.entry("flux.plug_count", (long) fluxResult.plugCount()),
                                Map.entry("flux.point_count", (long) fluxResult.pointCount()),
                                Map.entry("flux.storage_count", (long) fluxResult.storageCount()),
                                Map.entry("flux.controller_count", (long) fluxResult.controllerCount())
                        );
                        debugInfoMap.put(binding.networkId(),
                                "channel=flux.api;status=ok;target=" + targetPos.toShortString()
                                        + ";network=" + fluxResult.networkName()
                                        + ";id=" + fluxResult.networkId()
                                        + ";input=" + inputPerTick
                                        + ";output=" + outputPerTick
                                        + ";stored=" + fluxResult.totalEnergy()
                                        + ";buffer=" + fluxResult.totalBuffer()
                                        + ";maxCapacity=" + fluxResult.totalMaxEnergyStorage()
                                        + ";plugs=" + fluxResult.plugCount()
                                        + ";points=" + fluxResult.pointCount()
                                        + ";storages=" + fluxResult.storageCount()
                                        + ";controllers=" + fluxResult.controllerCount()
                                        + ";devices=" + fluxResult.devices().size());
                    } else {
                        // Flux API 返回 null，回退到基础 IEnergyStorage
                        fluxSampleResults.remove(binding.networkId());
                        newStats = sampleEnergy(targetPos, oldStats);
                        debugInfoMap.put(binding.networkId(),
                                "channel=flux.api;status=fallback_to_energy;target=" + targetPos.toShortString());
                    }
                } else {
                    // Flux Networks 未加载，使用通用 IEnergyStorage
                    fluxSampleResults.remove(binding.networkId());
                    newStats = sampleEnergy(targetPos, oldStats);
                    debugInfoMap.put(binding.networkId(),
                            "channel=neoforge.energy;target=" + targetPos.toShortString());
                }
                dataStore.cellCapacityMetrics.remove(binding.networkId());
                capacityWarnSignatureMap.remove(binding.networkId());
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
     * 采样 AE2 物品存储网络。
     * 读取网络中所有物品的当前数量，与上次快照对比计算每种物品的增量，
     * 正增量累加到生产量，负增量绝对值累加到消耗量。
     */
    private BindingStats sampleAe2Network(String networkId, BlockPos targetPos, BindingStats oldStats) {
        return sampler.sampleAe2Network(this, networkId, targetPos, oldStats);
    }

    void reportAe2CapacityIssue(String networkId, BlockPos targetPos, Ae2ReadResult readResult) {
        Ae2CellCapacityMetrics metrics = readResult.cellCapacityMetrics();
        if (!metrics.available() || !metrics.reliable()) {
            String signature = readResult.debugInfo();
            String previous = capacityWarnSignatureMap.put(networkId, signature);
            if (!signature.equals(previous)) {
                // 频率限制：同一网络 5 分钟内最多输出一次 WARN
                long now = System.nanoTime();
                Long lastWarn = capacityWarnTimestampMap.get(networkId);
                if (lastWarn == null || (now - lastWarn) >= CAPACITY_WARN_INTERVAL_NS) {
                    capacityWarnTimestampMap.put(networkId, now);
                    ResourceObserverMod.LOGGER.warn(
                            "[ResourceObserver] AE2 capacity probe degraded at {} network={}"
                                    + " reliable={} available={}",
                            targetPos.toShortString(),
                            networkId,
                            metrics.reliable(),
                            metrics.available()
                    );
                }
                // 完整诊断信息降级为 DEBUG，需要时可通过日志配置启用
                ResourceObserverMod.LOGGER.debug(
                        "[ResourceObserver] AE2 capacity probe detail at {} network={} info={}",
                        targetPos.toShortString(),
                        networkId,
                        signature
                );
            }
            return;
        }
        capacityWarnSignatureMap.remove(networkId);
        capacityWarnTimestampMap.remove(networkId);
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
        IGrid leftGrid = resolveAe2GridForNetwork(leftNetworkId);
        if (leftGrid == null) {
            return false;
        }
        IGrid rightGrid = resolveAe2GridForNetwork(rightNetworkId);
        return rightGrid != null && leftGrid == rightGrid;
    }

    /**
     * 解析目标方块所在的 AE2 网络 Grid。
     * <p>
     * AE2 节点具有方向性，需遍历所有 6 个面方向（上/下/东/西/南/北）
     * 尝试获取有效的 GridNode。找到第一个非 null 节点后停止遍历。
     * getGrid() 可能在网络处于无效/拆卸状态时抛出 IllegalStateException，
     * 此时安全返回 null。
     */
    @Nullable IGrid resolveAe2GridAt(BlockPos targetPos) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos, null);
        if (host == null) {
            return null;
        }

        // 遍历 6 个面方向查找可用 GridNode（AE2 连接具有方向性）
        IGridNode node = null;
        for (Direction dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null) {
                break;
            }
        }
        if (node == null) {
            return null;
        }

        try {
            return node.getGrid();
        } catch (IllegalStateException ex) {
            // 网络处于无效状态（如正在拆卸），安全返回 null
            return null;
        }
    }

    /**
     * 通过 AE2 API 读取目标方块所在网络的物品库存。
     * 流程：获取 GridNodeHost → 遍历方向获取 GridNode → 获取 Grid → 读取 StorageService 缓存库存。
     * 仅统计 AEItemKey 类型的存储（忽略流体等）。
     */
    Ae2ReadResult readAe2NetworkItems(BlockPos targetPos) {
        return sampler.readAe2NetworkItems(this, targetPos);
    }

    private static @Nullable String toEntryId(AEKey key) {
        return Ae2Sampler.toEntryId(key);
    }

    private static boolean isFluidEntryId(String entryId) {
        return Ae2Sampler.isFluidEntryId(entryId);
    }

    private static boolean isItemEntryId(String entryId) {
        return Ae2Sampler.isItemEntryId(entryId);
    }

    /**
     * 读取 AE2 网络中所有存储单元（Cell）的容量指标。
     * <p>
     * 处理流程（7 层嵌套条件链）：
     * 设备遍历 → 通电检查 → 插槽遍历 → Cell 为空判断 → 反射解析 Cell 类并获取访问器
     * → 通道推断（ITEM/FLUID） → 读取指标并累加到对应通道的总和。
     * <p>
     * 使用 saturatingAdd 防止 long 溢出到负数。20+ 个计数器用于构建调试诊断信息。
     * 任何反射读取失败都会标记 reliable=false 而非直接抛异常，保证容量探针的鲁棒性。
     */
    Ae2CapacityReadResult readAe2CellCapacityMetrics(IGrid grid) {
        return Ae2CellProber.readCellCapacityMetrics(grid);
    }

    private static Map<String, Long> computeDeltas(Map<String, Long> previous, Map<String, Long> current) {
        return Ae2Sampler.computeDeltas(previous, current);
    }

    /**
     * 采样普通物品容器（NeoForge IItemHandler 能力）。
     * 遍历容器所有槽位统计物品总数。
     * 注意：此方法在 MVP 中未被直接使用（AE2 走专用 API），保留作为扩展。
     */
    private BindingStats sampleItems(BlockPos targetPos, BindingStats oldStats) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, null);
        if (handler == null) {
            for (Direction dir : Direction.values()) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, dir);
                if (handler != null) break;
            }
        }
        if (handler == null) return oldStats;

        long totalItems = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            totalItems += handler.getStackInSlot(i).getCount();
        }
        return oldStats.withSample(totalItems, handler.getSlots());
    }

    /**
     * 采样能量存储（NeoForge IEnergyStorage 能力，适用于 Flux Networks）。
     * 先尝试无方向获取，失败后遍历所有方向尝试。
     * 读取当前能量存储量和最大容量。
     */
    private BindingStats sampleEnergy(BlockPos targetPos, BindingStats oldStats) {
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, null);
        if (energy == null) {
            for (Direction dir : Direction.values()) {
                energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, dir);
                if (energy != null) break;
            }
        }
        if (energy == null) return oldStats;

        // IEnergyStorage 接口返回 int，超过 Integer.MAX_VALUE (~2.1B) 时会溢出为负数。
        // 使用 Integer.toUnsignedLong() 将 32-bit 无符号整数正确转换为 long，支持最大 ~4.29B FE。
        long stored = Integer.toUnsignedLong(energy.getEnergyStored());
        long capacity = Integer.toUnsignedLong(energy.getMaxEnergyStored());
        return oldStats.withSample(stored, capacity);
    }

    /**
     * 从 networkId 字符串中解析目标方块的坐标。
     * networkId 格式为 "blockId@posLong"，通过 @ 分割提取 BlockPos.of() 编码。
     */
    private static BlockPos extractPos(String networkId) {
        int atIdx = networkId.lastIndexOf('@');
        if (atIdx < 0) return null;
        try {
            return BlockPos.of(Long.parseLong(networkId.substring(atIdx + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
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
        List<Component> lines = new ArrayList<>();
        if (bindings.isEmpty()) {
            lines.add(Component.translatable("message.resourceobserver.debug.none"));
            return lines;
        }

        lines.add(Component.translatable("message.resourceobserver.debug.header", bindings.size()));
        for (int i = 0; i < bindings.size(); i++) {
            BoundEntry entry = bindings.get(i);
            BindingStats stats = statsMap.getOrDefault(entry.networkId(), BindingStats.empty());
            lines.add(Component.translatable(
                    "message.resourceobserver.debug.binding",
                    i + 1,
                    entry.networkType(),
                    entry.targetBlockId()
            ));
            lines.add(Component.translatable(
                    "message.resourceobserver.debug.stats",
                    stats.currentValue(),
                    stats.capacity(),
                    stats.totalProduced(),
                    stats.totalConsumed()
            ));
            if ("AE2_ITEMS".equals(entry.networkType())) {
                Ae2CellCapacityMetrics metrics = getAe2CellCapacityMetricsFor(entry.networkId());
                lines.add(Component.translatable(
                        "message.resourceobserver.debug.cells",
                        metrics.itemUsedBytes(),
                        metrics.itemTotalBytes(),
                        metrics.itemUsedTypes(),
                        metrics.itemTotalTypes(),
                        metrics.itemUsedUnits(),
                        metrics.itemMaxUnits(),
                        metrics.fluidUsedBytes(),
                        metrics.fluidTotalBytes(),
                        metrics.fluidUsedTypes(),
                        metrics.fluidTotalTypes(),
                        metrics.fluidUsedUnits(),
                        metrics.fluidMaxUnits(),
                        boolComponent(metrics.reliable()),
                        boolComponent(metrics.available())
                ));
                lines.add(Component.translatable(
                        "message.resourceobserver.debug.external",
                        metrics.externalItemUsedUnits(),
                        metrics.externalItemTotalUnits(),
                        metrics.externalFluidUsedUnits(),
                        metrics.externalFluidTotalUnits(),
                        boolComponent(metrics.externalReliable()),
                        boolComponent(metrics.externalAvailable())
                ));
            }
            appendFormattedProbeLines(lines, getDebugInfoFor(entry.networkId()));
        }
        return lines;
    }

    private static void appendFormattedProbeLines(List<Component> lines, String debugInfo) {
        Map<String, String> fields = parseProbeFields(debugInfo);
        if (fields.isEmpty()) {
            lines.add(Component.translatable("message.resourceobserver.debug.probe_none"));
            return;
        }

        String channel = probeField(fields, "channel");
        String status = probeField(fields, "status");
        lines.add(Component.translatable("message.resourceobserver.debug.probe_header", channel, status));

        if (!"ae2.cached_inventory".equals(channel)) {
            addWrappedDebugLine(
                    lines,
                    "        ",
                    Component.translatable("message.resourceobserver.debug.probe_raw", debugInfo)
            );
            return;
        }

        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_inventory",
                        probeField(fields, "types"),
                        probeField(fields, "item_types"),
                        probeField(fields, "fluid_types"),
                        probeField(fields, "cells"),
                        probeField(fields, "capacity_reliable")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_cells",
                        probeField(fields, "capacity_cells_total"),
                        probeField(fields, "capacity_cells_readable"),
                        probeField(fields, "capacity_item_cells_total"),
                        probeField(fields, "capacity_item_cells_readable"),
                        probeField(fields, "capacity_fluid_cells_total"),
                        probeField(fields, "capacity_fluid_cells_readable"),
                        probeField(fields, "capacity_cells_null"),
                        probeField(fields, "capacity_cells_null_unpowered"),
                        probeField(fields, "capacity_cell_status_probe_failed")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_external",
                        probeField(fields, "capacity_external_storage_bus_total"),
                        probeField(fields, "capacity_external_storage_bus_readable"),
                        probeField(fields, "capacity_external_item_used"),
                        probeField(fields, "capacity_external_item_total"),
                        probeField(fields, "capacity_external_fluid_used"),
                        probeField(fields, "capacity_external_fluid_total"),
                        probeField(fields, "capacity_external_reliable"),
                        probeField(fields, "capacity_external_available")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_devices",
                        probeField(fields, "capacity_devices_total"),
                        probeField(fields, "capacity_devices_powered"),
                        probeField(fields, "capacity_devices_from_iChestOrDrive"),
                        probeField(fields, "capacity_devices_from_storage_provider"),
                        probeField(fields, "capacity_devices_from_node_owner"),
                        probeField(fields, "capacity_devices_from_node_storage_service"),
                        probeField(fields, "capacity_nodes_scanned")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_classes",
                        probeField(fields, "capacity_device_classes"),
                        probeField(fields, "capacity_cells_readable_classes"),
                        probeField(fields, "capacity_item_cells_readable_classes"),
                        probeField(fields, "capacity_fluid_cells_readable_classes")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_slot_channels",
                        probeField(fields, "capacity_slot_cell_items"),
                        probeField(fields, "capacity_slot_cell_keytypes"),
                        probeField(fields, "capacity_slot_channel_item"),
                        probeField(fields, "capacity_slot_channel_fluid"),
                        probeField(fields, "capacity_slot_channel_unknown")
                )
        );
        addWrappedDebugLine(
                lines,
                "        ",
                Component.translatable(
                        "message.resourceobserver.debug.probe_failures",
                        probeField(fields, "capacity_cells_missing_methods"),
                        probeField(fields, "capacity_cells_invocation_failed")
                )
        );
    }

    private static Component boolComponent(boolean value) {
        return Component.translatable(
                value ? "message.resourceobserver.debug.bool.true" : "message.resourceobserver.debug.bool.false"
        );
    }

    private static Map<String, String> parseProbeFields(String debugInfo) {
        Map<String, String> fields = new HashMap<>();
        if (debugInfo == null || debugInfo.isBlank()) {
            return fields;
        }
        String[] tokens = debugInfo.split(";");
        for (String token : tokens) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                fields.put(trimmed, "");
                continue;
            }
            fields.put(trimmed.substring(0, equalsIndex), trimmed.substring(equalsIndex + 1));
        }
        return fields;
    }

    private static String probeField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        return value;
    }

    private static void addWrappedDebugLine(List<Component> lines, String indent, Component content) {
        if (content == null) {
            return;
        }
        lines.add(Component.literal(indent).append(content));
    }

    /**
     * NBT 保存 —— 将所有绑定和统计数据序列化到 CompoundTag。
     * 每个绑定条目包含网络类型、网络ID、目标方块ID 和对应的统计数据。
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
        fluxSampleResults.clear();
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
