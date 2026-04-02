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
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static final String AE2_CAPACITY_SCOPE_CELLS_ONLY = "AE2_CELLS_ONLY";
    private static final String FLUID_KEY_PREFIX = "fluid:";

    /** 当前所有网络绑定列表 */
    private final List<BoundEntry> bindings = new ArrayList<>();
    /** 每个网络 ID 对应的统计数据 */
    private final Map<String, BindingStats> statsMap = new HashMap<>();
    /** AE2 网络中每种物品的当前数量快照（networkId -> {itemId -> amount}） */
    private final Map<String, Map<String, Long>> ae2ItemAmounts = new HashMap<>();
    /** AE2 网络中每种物品的数量变化量（networkId -> {itemId -> delta}） */
    private final Map<String, Map<String, Long>> ae2ItemDeltas = new HashMap<>();
    /** AE2 网络容量指标（networkId -> metrics） */
    private final Map<String, Ae2CellCapacityMetrics> ae2CellCapacityMetrics = new HashMap<>();
    /** 调试信息映射，记录每个网络的采样状态 */
    private final Map<String, String> debugInfoMap = new HashMap<>();
    /** 容量告警签名（networkId -> last signature），避免重复刷日志 */
    private final Map<String, String> capacityWarnSignatureMap = new HashMap<>();
    private static final Map<Class<?>, CellMetricsAccessors> CELL_METRICS_ACCESSORS = new HashMap<>();
    private static final Set<Class<?>> CELL_METRICS_UNSUPPORTED = new HashSet<>();
    /** 上次采样的游戏时间，用于控制采样间隔 */
    private long lastSampleTick = -1;

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
            String scope,
            boolean reliable,
            boolean available
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
                    AE2_CAPACITY_SCOPE_CELLS_ONLY,
                    false,
                    false
            );
        }
    }

    private enum CellChannel {
        ITEM(
                "item",
                "getTotalItemTypes",
                "getStoredItemTypes",
                "getStoredItemCount",
                "getRemainingItemCount"
        ),
        FLUID(
                "fluid",
                "getTotalFluidTypes",
                "getStoredFluidTypes",
                "getStoredFluidCount",
                "getRemainingFluidCount"
        );

        private final String key;
        private final String totalTypesMethodName;
        private final String usedTypesMethodName;
        private final String storedUnitsMethodName;
        private final String remainingUnitsMethodName;

        CellChannel(
                String key,
                String totalTypesMethodName,
                String usedTypesMethodName,
                String storedUnitsMethodName,
                String remainingUnitsMethodName
        ) {
            this.key = key;
            this.totalTypesMethodName = totalTypesMethodName;
            this.usedTypesMethodName = usedTypesMethodName;
            this.storedUnitsMethodName = storedUnitsMethodName;
            this.remainingUnitsMethodName = remainingUnitsMethodName;
        }

        public String key() {
            return key;
        }

        public String totalTypesMethodName() {
            return totalTypesMethodName;
        }

        public String usedTypesMethodName() {
            return usedTypesMethodName;
        }

        public String storedUnitsMethodName() {
            return storedUnitsMethodName;
        }

        public String remainingUnitsMethodName() {
            return remainingUnitsMethodName;
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
        return true;
    }

    /** 清除所有绑定及关联的统计数据、物品快照和调试信息 */
    public void clearAllBindings() {
        bindings.clear();
        statsMap.clear();
        ae2ItemAmounts.clear();
        ae2ItemDeltas.clear();
        ae2CellCapacityMetrics.clear();
        debugInfoMap.clear();
        capacityWarnSignatureMap.clear();
        setChanged();
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
        Map<String, Long> data = ae2ItemAmounts.get(networkId);
        if (data == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(data);
    }

    public Map<String, Long> getAe2ItemDeltasFor(String networkId) {
        Map<String, Long> data = ae2ItemDeltas.get(networkId);
        if (data == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(data);
    }

    public String getDebugInfoFor(String networkId) {
        return debugInfoMap.getOrDefault(networkId, "no debug data yet");
    }

    public Ae2CellCapacityMetrics getAe2CellCapacityMetricsFor(String networkId) {
        return ae2CellCapacityMetrics.getOrDefault(networkId, Ae2CellCapacityMetrics.unavailable());
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
        if (be.lastSampleTick >= 0 && gameTime - be.lastSampleTick < SAMPLE_INTERVAL) return;
        be.lastSampleTick = gameTime;
        be.sampleAll();
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
                newStats = sampleEnergy(targetPos, oldStats);
                ae2CellCapacityMetrics.remove(binding.networkId());
                capacityWarnSignatureMap.remove(binding.networkId());
                debugInfoMap.put(binding.networkId(), "channel=neoforge.energy;target=" + targetPos.toShortString());
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
        Ae2ReadResult readResult = readAe2NetworkItems(targetPos);
        debugInfoMap.put(networkId, readResult.debugInfo());
        ae2CellCapacityMetrics.put(networkId, readResult.cellCapacityMetrics());
        reportAe2CapacityIssue(networkId, targetPos, readResult);
        Map<String, Long> snapshot = readResult.snapshot();
        if (snapshot == null) {
            ae2ItemDeltas.put(networkId, Map.of());
            return oldStats;
        }

        Map<String, Long> previous = ae2ItemAmounts.getOrDefault(networkId, Map.of());
        Map<String, Long> deltas = computeDeltas(previous, snapshot);
        ae2ItemAmounts.put(networkId, snapshot);
        ae2ItemDeltas.put(networkId, deltas);

        long totalAmount = 0;
        long itemTypeCount = 0;
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            if (!isItemEntryId(entry.getKey())) {
                continue;
            }
            totalAmount = saturatingAdd(totalAmount, Math.max(0L, entry.getValue()));
            itemTypeCount++;
        }

        long produced = 0;
        long consumed = 0;
        for (Map.Entry<String, Long> entry : deltas.entrySet()) {
            if (!isItemEntryId(entry.getKey())) {
                continue;
            }
            long delta = entry.getValue();
            if (delta > 0) {
                produced = saturatingAdd(produced, delta);
            } else if (delta < 0) {
                consumed = saturatingAdd(consumed, -delta);
            }
        }

        return oldStats.withDelta(totalAmount, itemTypeCount, produced, consumed);
    }

    private void reportAe2CapacityIssue(String networkId, BlockPos targetPos, Ae2ReadResult readResult) {
        Ae2CellCapacityMetrics metrics = readResult.cellCapacityMetrics();
        if (!metrics.available() || !metrics.reliable()) {
            String signature = readResult.debugInfo();
            String previous = capacityWarnSignatureMap.put(networkId, signature);
            if (!signature.equals(previous)) {
                ResourceObserverMod.LOGGER.warn(
                        "[ResourceObserver] AE2 capacity probe degraded at {} network={} info={}",
                        targetPos.toShortString(),
                        networkId,
                        signature
                );
            }
            return;
        }
        capacityWarnSignatureMap.remove(networkId);
    }

    /**
     * AE2 网络读取结果。
     * @param snapshot  物品快照（itemId -> amount），null 表示读取失败
     * @param debugInfo 调试信息字符串
     */
    private record Ae2ReadResult(
            @Nullable Map<String, Long> snapshot,
            String debugInfo,
            Ae2CellCapacityMetrics cellCapacityMetrics
    ) {
    }

    private record Ae2CapacityReadResult(
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

    private @Nullable IGrid resolveAe2GridAt(BlockPos targetPos) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos, null);
        if (host == null) {
            return null;
        }

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
            return null;
        }
    }

    /**
     * 通过 AE2 API 读取目标方块所在网络的物品库存。
     * 流程：获取 GridNodeHost → 遍历方向获取 GridNode → 获取 Grid → 读取 StorageService 缓存库存。
     * 仅统计 AEItemKey 类型的存储（忽略流体等）。
     */
    private Ae2ReadResult readAe2NetworkItems(BlockPos targetPos) {
        IGrid grid = resolveAe2GridAt(targetPos);
        if (grid == null) {
            return new Ae2ReadResult(
                    null,
                    "channel=ae2.grid;status=unavailable;target=" + targetPos.toShortString(),
                    Ae2CellCapacityMetrics.unavailable()
            );
        }

        IStorageService storageService = grid.getStorageService();
        KeyCounter cachedInventory = storageService.getCachedInventory();
        Map<String, Long> snapshot = new HashMap<>();
        int itemTypeCount = 0;
        int fluidTypeCount = 0;
        for (AEKey key : cachedInventory.keySet()) {
            String entryId = toEntryId(key);
            if (entryId == null) {
                continue;
            }
            long amount = cachedInventory.get(key);
            if (amount <= 0) {
                continue;
            }
            if (isFluidEntryId(entryId)) {
                fluidTypeCount++;
            } else {
                itemTypeCount++;
            }
            snapshot.merge(entryId, amount, Long::sum);
        }
        Ae2CapacityReadResult capacityReadResult = readAe2CellCapacityMetrics(grid);
        Ae2CellCapacityMetrics capacityMetrics = capacityReadResult.metrics();
        String debug = "channel=ae2.cached_inventory;status=ok;types="
                + snapshot.size()
                + ";item_types="
                + itemTypeCount
                + ";fluid_types="
                + fluidTypeCount
                + ";cells="
                + (capacityMetrics.available() ? "present" : "none")
                + ";capacity_reliable="
                + capacityMetrics.reliable()
                + capacityReadResult.debugSuffix();
        return new Ae2ReadResult(snapshot, debug, capacityMetrics);
    }

    private static @Nullable String toEntryId(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.getId().toString();
        }
        if (key instanceof AEFluidKey fluidKey) {
            return FLUID_KEY_PREFIX + fluidKey.getId();
        }
        return null;
    }

    private static boolean isFluidEntryId(String entryId) {
        return entryId != null && entryId.startsWith(FLUID_KEY_PREFIX);
    }

    private static boolean isItemEntryId(String entryId) {
        return !isFluidEntryId(entryId);
    }

    private Ae2CapacityReadResult readAe2CellCapacityMetrics(IGrid grid) {
        long itemUsedBytes = 0L;
        long itemTotalBytes = 0L;
        long itemUsedTypes = 0L;
        long itemTotalTypes = 0L;
        long itemUsedUnits = 0L;
        long itemMaxUnits = 0L;
        long fluidUsedBytes = 0L;
        long fluidTotalBytes = 0L;
        long fluidUsedTypes = 0L;
        long fluidTotalTypes = 0L;
        long fluidUsedUnits = 0L;
        long fluidMaxUnits = 0L;
        boolean reliable = true;
        int cellCount = 0;
        int readableCellCount = 0;
        int itemCellCount = 0;
        int itemReadableCellCount = 0;
        int fluidCellCount = 0;
        int fluidReadableCellCount = 0;
        int deviceCount = 0;
        int poweredDeviceCount = 0;
        int nullCellCount = 0;
        int nullCellOnUnpoweredCount = 0;
        int statusProbeFailedCount = 0;
        int fromChestOrDriveCount = 0;
        int fromStorageProviderCount = 0;
        int fromNodeOwnerCount = 0;
        int fromNodeStorageServiceCount = 0;
        int nodesScanned = 0;
        Map<String, Integer> deviceClasses = new HashMap<>();
        Map<String, Integer> readableCellClasses = new HashMap<>();
        Map<String, Integer> readableItemCellClasses = new HashMap<>();
        Map<String, Integer> readableFluidCellClasses = new HashMap<>();
        Map<String, Integer> slotCellItemIds = new HashMap<>();
        Map<String, Integer> slotCellKeyTypes = new HashMap<>();
        Map<String, Integer> missingMethodCells = new HashMap<>();
        Map<String, Integer> invocationFailedCells = new HashMap<>();
        int slotCellChannelItem = 0;
        int slotCellChannelFluid = 0;
        int slotCellChannelUnknown = 0;

        CapacityMachineCollection machineCollection = collectCapacityMachines(grid);
        Set<IChestOrDrive> machines = machineCollection.machines();
        fromChestOrDriveCount = machineCollection.fromChestOrDriveCount();
        fromStorageProviderCount = machineCollection.fromStorageProviderCount();
        fromNodeOwnerCount = machineCollection.fromNodeOwnerCount();
        fromNodeStorageServiceCount = machineCollection.fromNodeStorageServiceCount();
        nodesScanned = machineCollection.nodesScanned();

        for (IChestOrDrive machine : machines) {
            deviceCount++;
            incrementCount(deviceClasses, machine.getClass().getName());
            boolean powered = safeIsPowered(machine);
            if (powered) {
                poweredDeviceCount++;
            }
            for (int slot = 0; slot < machine.getCellCount(); slot++) {
                if (!probeCellStatus(machine, slot)) {
                    statusProbeFailedCount++;
                }
                StorageCell cell = machine.getOriginalCellInventory(slot);
                if (cell == null) {
                    if (powered) {
                        nullCellCount++;
                    } else {
                        nullCellOnUnpoweredCount++;
                    }
                    continue;
                }
                cellCount++;
                Class<?> cellClass = cell.getClass();
                CellMetricsAccessors accessors = resolveCellMetricsAccessors(cellClass);
                if (accessors == null) {
                    reliable = false;
                    incrementCount(missingMethodCells, cellClass.getName());
                    continue;
                }
                SlotCellProbe slotProbe = detectChannelFromCellItem(machine, slot);
                if (slotProbe.itemId() != null) {
                    incrementCount(slotCellItemIds, slotProbe.itemId());
                }
                if (slotProbe.keyTypeId() != null) {
                    incrementCount(slotCellKeyTypes, slotProbe.keyTypeId());
                }
                CellChannel channel = slotProbe.channel();
                if (channel == null) {
                    slotCellChannelUnknown++;
                    channel = accessors.detectChannel(cell);
                } else if (channel == CellChannel.ITEM) {
                    slotCellChannelItem++;
                } else {
                    slotCellChannelFluid++;
                }
                if (channel == CellChannel.ITEM) {
                    itemCellCount++;
                } else {
                    fluidCellCount++;
                }
                ReflectedCellMetrics cellMetrics;
                try {
                    cellMetrics = accessors.read(cell, channel);
                } catch (ReflectiveOperationException ex) {
                    reliable = false;
                    incrementCount(invocationFailedCells, cellClass.getName());
                    continue;
                }
                readableCellCount++;
                incrementCount(readableCellClasses, cellClass.getName());
                if (cellMetrics.channel() == CellChannel.ITEM) {
                    itemReadableCellCount++;
                    incrementCount(readableItemCellClasses, cellClass.getName());
                    itemUsedBytes = saturatingAdd(itemUsedBytes, Math.max(0L, cellMetrics.usedBytes()));
                    itemTotalBytes = saturatingAdd(itemTotalBytes, Math.max(0L, cellMetrics.totalBytes()));
                    itemUsedTypes = saturatingAdd(itemUsedTypes, Math.max(0L, cellMetrics.usedTypes()));
                    itemTotalTypes = saturatingAdd(itemTotalTypes, Math.max(0L, cellMetrics.totalTypes()));
                    itemUsedUnits = saturatingAdd(itemUsedUnits, Math.max(0L, cellMetrics.usedUnits()));
                    itemMaxUnits = saturatingAdd(itemMaxUnits, Math.max(0L, cellMetrics.maxUnits()));
                } else {
                    fluidReadableCellCount++;
                    incrementCount(readableFluidCellClasses, cellClass.getName());
                    fluidUsedBytes = saturatingAdd(fluidUsedBytes, Math.max(0L, cellMetrics.usedBytes()));
                    fluidTotalBytes = saturatingAdd(fluidTotalBytes, Math.max(0L, cellMetrics.totalBytes()));
                    fluidUsedTypes = saturatingAdd(fluidUsedTypes, Math.max(0L, cellMetrics.usedTypes()));
                    fluidTotalTypes = saturatingAdd(fluidTotalTypes, Math.max(0L, cellMetrics.totalTypes()));
                    fluidUsedUnits = saturatingAdd(fluidUsedUnits, Math.max(0L, cellMetrics.usedUnits()));
                    fluidMaxUnits = saturatingAdd(fluidMaxUnits, Math.max(0L, cellMetrics.maxUnits()));
                }
            }
        }

        Ae2CellCapacityMetrics metrics = new Ae2CellCapacityMetrics(
                itemUsedBytes,
                itemTotalBytes,
                itemUsedTypes,
                itemTotalTypes,
                itemUsedUnits,
                itemMaxUnits,
                fluidUsedBytes,
                fluidTotalBytes,
                fluidUsedTypes,
                fluidTotalTypes,
                fluidUsedUnits,
                fluidMaxUnits,
                AE2_CAPACITY_SCOPE_CELLS_ONLY,
                reliable,
                cellCount > 0
        );
        String debugSuffix = ";capacity_cells_total=" + cellCount
                + ";capacity_cells_readable=" + readableCellCount
                + ";capacity_devices_total=" + deviceCount
                + ";capacity_devices_powered=" + poweredDeviceCount
                + ";capacity_cells_null=" + nullCellCount
                + ";capacity_cells_null_unpowered=" + nullCellOnUnpoweredCount
                + ";capacity_cell_status_probe_failed=" + statusProbeFailedCount
                + ";capacity_item_cells_total=" + itemCellCount
                + ";capacity_item_cells_readable=" + itemReadableCellCount
                + ";capacity_fluid_cells_total=" + fluidCellCount
                + ";capacity_fluid_cells_readable=" + fluidReadableCellCount
                + ";capacity_devices_from_iChestOrDrive=" + fromChestOrDriveCount
                + ";capacity_devices_from_storage_provider=" + fromStorageProviderCount
                + ";capacity_devices_from_node_owner=" + fromNodeOwnerCount
                + ";capacity_devices_from_node_storage_service=" + fromNodeStorageServiceCount
                + ";capacity_nodes_scanned=" + nodesScanned
                + ";capacity_device_classes=" + summarizeFailureClasses(deviceClasses)
                + ";capacity_cells_readable_classes=" + summarizeFailureClasses(readableCellClasses)
                + ";capacity_item_cells_readable_classes=" + summarizeFailureClasses(readableItemCellClasses)
                + ";capacity_fluid_cells_readable_classes=" + summarizeFailureClasses(readableFluidCellClasses)
                + ";capacity_slot_cell_items=" + summarizeFailureClasses(slotCellItemIds)
                + ";capacity_slot_cell_keytypes=" + summarizeFailureClasses(slotCellKeyTypes)
                + ";capacity_slot_channel_item=" + slotCellChannelItem
                + ";capacity_slot_channel_fluid=" + slotCellChannelFluid
                + ";capacity_slot_channel_unknown=" + slotCellChannelUnknown
                + ";capacity_cells_missing_methods=" + summarizeFailureClasses(missingMethodCells)
                + ";capacity_cells_invocation_failed=" + summarizeFailureClasses(invocationFailedCells);
        return new Ae2CapacityReadResult(metrics, debugSuffix);
    }

    private record CapacityMachineCollection(
            Set<IChestOrDrive> machines,
            int fromChestOrDriveCount,
            int fromStorageProviderCount,
            int fromNodeOwnerCount,
            int fromNodeStorageServiceCount,
            int nodesScanned
    ) {
    }

    private static CapacityMachineCollection collectCapacityMachines(IGrid grid) {
        Set<IChestOrDrive> result = Collections.newSetFromMap(new IdentityHashMap<>());
        int fromChestOrDriveCount = 0;
        int fromStorageProviderCount = 0;
        int fromNodeOwnerCount = 0;
        int fromNodeStorageServiceCount = 0;
        int nodesScanned = 0;

        for (IChestOrDrive machine : grid.getMachines(IChestOrDrive.class)) {
            fromChestOrDriveCount++;
            result.add(machine);
        }
        for (IStorageProvider provider : grid.getMachines(IStorageProvider.class)) {
            if (provider instanceof IChestOrDrive chestOrDrive) {
                fromStorageProviderCount++;
                result.add(chestOrDrive);
            }
        }

        for (IGridNode node : grid.getNodes()) {
            nodesScanned++;
            Object owner = node.getOwner();
            if (owner instanceof IChestOrDrive chestOrDrive) {
                fromNodeOwnerCount++;
                result.add(chestOrDrive);
            }
            IStorageProvider service = node.getService(IStorageProvider.class);
            if (service instanceof IChestOrDrive chestOrDrive) {
                fromNodeStorageServiceCount++;
                result.add(chestOrDrive);
            }
        }

        return new CapacityMachineCollection(
                result,
                fromChestOrDriveCount,
                fromStorageProviderCount,
                fromNodeOwnerCount,
                fromNodeStorageServiceCount,
                nodesScanned
        );
    }

    private record SlotCellProbe(
            @Nullable CellChannel channel,
            @Nullable String itemId,
            @Nullable String keyTypeId
    ) {
    }

    private static SlotCellProbe detectChannelFromCellItem(IChestOrDrive machine, int slot) {
        try {
            Item cellItem = machine.getCellItem(slot);
            if (cellItem == null) {
                return new SlotCellProbe(null, null, null);
            }
            String itemId = BuiltInRegistries.ITEM.getKey(cellItem).toString();
            if (cellItem instanceof IBasicCellItem basicCellItem) {
                AEKeyType keyType = basicCellItem.getKeyType();
                String keyTypeId = keyType == null ? null : keyType.getId().toString();
                CellChannel channel = parseChannelFromKeyType(keyType);
                if (channel == null) {
                    channel = parseChannelFromItemId(itemId);
                }
                return new SlotCellProbe(channel, itemId, keyTypeId);
            }
            return new SlotCellProbe(parseChannelFromItemId(itemId), itemId, null);
        } catch (RuntimeException ex) {
            return new SlotCellProbe(null, null, null);
        }
    }

    private static boolean probeCellStatus(IChestOrDrive machine, int slot) {
        try {
            machine.getCellStatus(slot);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean safeIsPowered(IChestOrDrive machine) {
        try {
            return machine.isPowered();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static void incrementCount(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private static String summarizeFailureClasses(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int compareCount = Integer.compare(right.getValue(), left.getValue());
            if (compareCount != 0) {
                return compareCount;
            }
            return left.getKey().compareTo(right.getKey());
        });
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, entries.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(',');
            }
            Map.Entry<String, Integer> entry = entries.get(i);
            builder.append(simpleClassName(entry.getKey())).append('(').append(entry.getValue()).append(')');
        }
        if (entries.size() > limit) {
            builder.append(",+").append(entries.size() - limit).append(" more");
        }
        return builder.toString();
    }

    private static String simpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= className.length() - 1) {
            return className;
        }
        return className.substring(lastDot + 1);
    }

    private static @Nullable CellMetricsAccessors resolveCellMetricsAccessors(Class<?> cellClass) {
        CellMetricsAccessors cached = CELL_METRICS_ACCESSORS.get(cellClass);
        if (cached != null) {
            return cached;
        }
        if (CELL_METRICS_UNSUPPORTED.contains(cellClass)) {
            return null;
        }
        try {
            CellMetricsAccessors created = CellMetricsAccessors.create(cellClass);
            CELL_METRICS_ACCESSORS.put(cellClass, created);
            return created;
        } catch (ReflectiveOperationException ex) {
            CELL_METRICS_UNSUPPORTED.add(cellClass);
            return null;
        }
    }

    private static long invokeLong(Method method, Object target) throws ReflectiveOperationException {
        Object value = method.invoke(target);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new ReflectiveOperationException("Method did not return a numeric value: " + method.getName());
    }

    private static Method findCellMetricMethod(Class<?> cellClass, String name) throws ReflectiveOperationException {
        try {
            return cellClass.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            Class<?> current = cellClass;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException ignoredSetAccessibleFailure) {
                        // Keep method as-is; invocation might still work if it is effectively accessible.
                    }
                    return method;
                } catch (NoSuchMethodException innerIgnored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchMethodException(cellClass.getName() + "#" + name);
        }
    }

    private static @Nullable Method findOptionalCellMetricMethod(Class<?> cellClass, String name) {
        try {
            return findCellMetricMethod(cellClass, name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable CellChannel parseChannelFromObject(@Nullable Object channelObject) {
        if (channelObject == null) {
            return null;
        }
        String typeName = channelObject.getClass().getName().toLowerCase(Locale.ROOT);
        String value = channelObject.toString().toLowerCase(Locale.ROOT);
        if (typeName.contains("fluid") || value.contains("fluid")) {
            return CellChannel.FLUID;
        }
        if (typeName.contains("item") || value.contains("item")) {
            return CellChannel.ITEM;
        }
        return null;
    }

    private static @Nullable CellChannel parseChannelFromKeyType(@Nullable Object keyTypeObject) {
        if (keyTypeObject == null) {
            return null;
        }
        if (keyTypeObject instanceof AEKeyType keyType) {
            if (keyType == AEKeyType.fluids()) {
                return CellChannel.FLUID;
            }
            if (keyType == AEKeyType.items()) {
                return CellChannel.ITEM;
            }
            String id = keyType.getId().toString().toLowerCase(Locale.ROOT);
            if (id.contains("fluid")) {
                return CellChannel.FLUID;
            }
            if (id.contains("item")) {
                return CellChannel.ITEM;
            }
        }
        String text = keyTypeObject.toString().toLowerCase(Locale.ROOT);
        if (text.contains("fluid")) {
            return CellChannel.FLUID;
        }
        if (text.contains("item")) {
            return CellChannel.ITEM;
        }
        return null;
    }

    private static @Nullable CellChannel parseChannelFromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalized = itemId.toLowerCase(Locale.ROOT);
        if (normalized.contains("fluid")) {
            return CellChannel.FLUID;
        }
        if (normalized.contains("item")) {
            return CellChannel.ITEM;
        }
        return null;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record ReflectedCellMetrics(
            CellChannel channel,
            long totalBytes,
            long usedBytes,
            long totalTypes,
            long usedTypes,
            long usedUnits,
            long maxUnits
    ) {
    }

    private record CellMetricsAccessors(
            Method totalBytesMethod,
            Method usedBytesMethod,
            @Nullable Method channelMethod,
            @Nullable Method keyTypeMethod,
            @Nullable Field keyTypeField,
            @Nullable ChannelMetricsAccessors itemMetricsAccessors,
            @Nullable ChannelMetricsAccessors fluidMetricsAccessors
    ) {
        private static CellMetricsAccessors create(Class<?> cellClass) throws ReflectiveOperationException {
            Method totalBytesMethod = findCellMetricMethod(cellClass, "getTotalBytes");
            Method usedBytesMethod = findCellMetricMethod(cellClass, "getUsedBytes");
            Method channelMethod = findOptionalCellMetricMethod(cellClass, "getChannel");
            Method keyTypeMethod = findOptionalCellMetricMethod(cellClass, "getKeyType");
            Field keyTypeField = findOptionalCellMetricField(cellClass, "keyType");
            ChannelMetricsAccessors itemAccessors = ChannelMetricsAccessors.tryCreate(cellClass, CellChannel.ITEM);
            ChannelMetricsAccessors fluidAccessors = ChannelMetricsAccessors.tryCreate(cellClass, CellChannel.FLUID);
            if (itemAccessors == null && fluidAccessors == null) {
                throw new ReflectiveOperationException(
                        "Unable to resolve item/fluid metrics methods for " + cellClass.getName()
                );
            }
            return new CellMetricsAccessors(
                    totalBytesMethod,
                    usedBytesMethod,
                    channelMethod,
                    keyTypeMethod,
                    keyTypeField,
                    itemAccessors,
                    fluidAccessors
            );
        }

        private CellChannel detectChannel(Object cell) {
            CellChannel keyTypeChannel = parseChannelFromKeyType(readKeyType(cell));
            if (keyTypeChannel != null) {
                return keyTypeChannel;
            }
            if (channelMethod != null) {
                try {
                    CellChannel parsed = parseChannelFromObject(channelMethod.invoke(cell));
                    if (parsed != null) {
                        return parsed;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall through to method-availability based inference.
                }
            }
            if (itemMetricsAccessors != null && fluidMetricsAccessors == null) {
                return CellChannel.ITEM;
            }
            if (fluidMetricsAccessors != null && itemMetricsAccessors == null) {
                return CellChannel.FLUID;
            }
            return CellChannel.ITEM;
        }

        private ReflectedCellMetrics read(Object cell, CellChannel preferredChannel) throws ReflectiveOperationException {
            long totalBytes = Math.max(0L, invokeLong(totalBytesMethod, cell));
            long usedBytes = Math.max(0L, invokeLong(usedBytesMethod, cell));

            ChannelMetricsAccessors accessors = metricsFor(preferredChannel);
            boolean usingPreferredAccessors = accessors != null;
            if (accessors == null) {
                accessors = metricsFor(preferredChannel == CellChannel.ITEM ? CellChannel.FLUID : CellChannel.ITEM);
            }
            if (accessors == null) {
                throw new ReflectiveOperationException("No metrics accessors available");
            }

            ChannelRead selected = readChannel(cell, accessors);
            CellChannel resolvedChannel = usingPreferredAccessors ? selected.channel() : preferredChannel;
            ChannelMetricsAccessors alternateAccessors = metricsFor(
                    accessors.channel() == CellChannel.ITEM ? CellChannel.FLUID : CellChannel.ITEM
            );
            if (alternateAccessors != null) {
                try {
                    ChannelRead alternate = readChannel(cell, alternateAccessors);
                    if (selected.signal() == 0L && alternate.signal() > 0L) {
                        selected = alternate;
                        resolvedChannel = alternate.channel();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Keep primary channel when alternate read path fails.
                }
            }
            return new ReflectedCellMetrics(
                    resolvedChannel,
                    totalBytes,
                    usedBytes,
                    selected.totalTypes(),
                    selected.usedTypes(),
                    selected.storedUnits(),
                    selected.maxUnits()
            );
        }

        private @Nullable ChannelMetricsAccessors metricsFor(CellChannel channel) {
            return channel == CellChannel.FLUID ? fluidMetricsAccessors : itemMetricsAccessors;
        }

        private @Nullable Object readKeyType(Object cell) {
            if (keyTypeMethod != null) {
                try {
                    return keyTypeMethod.invoke(cell);
                } catch (ReflectiveOperationException ignored) {
                    // Try field fallback below.
                }
            }
            if (keyTypeField != null) {
                try {
                    return keyTypeField.get(cell);
                } catch (IllegalAccessException ignored) {
                    return null;
                }
            }
            return null;
        }

        private record ChannelMetricsAccessors(
                CellChannel channel,
                Method totalTypesMethod,
                Method storedTypesMethod,
                Method storedUnitsMethod,
                Method remainingUnitsMethod
        ) {
            private static @Nullable ChannelMetricsAccessors tryCreate(Class<?> cellClass, CellChannel channel) {
                Method totalTypesMethod = findOptionalCellMetricMethod(cellClass, channel.totalTypesMethodName());
                Method storedTypesMethod = findOptionalCellMetricMethod(cellClass, channel.usedTypesMethodName());
                Method storedUnitsMethod = findOptionalCellMetricMethod(cellClass, channel.storedUnitsMethodName());
                Method remainingUnitsMethod = findOptionalCellMetricMethod(cellClass, channel.remainingUnitsMethodName());
                if (totalTypesMethod == null
                        || storedTypesMethod == null
                        || storedUnitsMethod == null
                        || remainingUnitsMethod == null) {
                    return null;
                }
                return new ChannelMetricsAccessors(
                        channel,
                        totalTypesMethod,
                        storedTypesMethod,
                        storedUnitsMethod,
                        remainingUnitsMethod
                );
            }
        }

        private static ChannelRead readChannel(Object cell, ChannelMetricsAccessors accessors) throws ReflectiveOperationException {
            long totalTypes = Math.max(0L, invokeLong(accessors.totalTypesMethod(), cell));
            long usedTypes = Math.max(0L, invokeLong(accessors.storedTypesMethod(), cell));
            long storedUnits = Math.max(0L, invokeLong(accessors.storedUnitsMethod(), cell));
            long remainingUnits = Math.max(0L, invokeLong(accessors.remainingUnitsMethod(), cell));
            long maxUnits = saturatingAdd(storedUnits, remainingUnits);
            return new ChannelRead(accessors.channel(), totalTypes, usedTypes, storedUnits, maxUnits);
        }

        private record ChannelRead(
                CellChannel channel,
                long totalTypes,
                long usedTypes,
                long storedUnits,
                long maxUnits
        ) {
            private long signal() {
                return saturatingAdd(totalTypes, maxUnits);
            }
        }
    }

    private static @Nullable Field findOptionalCellMetricField(Class<?> cellClass, String name) {
        Class<?> current = cellClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                try {
                    field.setAccessible(true);
                } catch (RuntimeException ignoredSetAccessibleFailure) {
                    // Keep field as-is; direct read might still be possible.
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 计算两个物品快照之间的差异。
     * 遍历当前快照中的物品计算新增/变化量，
     * 同时检查上次快照中已消失的物品（完全消耗）。
     * @return 物品增量映射（itemId -> delta），正值表示增加，负值表示减少
     */
    private static Map<String, Long> computeDeltas(Map<String, Long> previous, Map<String, Long> current) {
        Map<String, Long> deltas = new HashMap<>();
        for (Map.Entry<String, Long> entry : current.entrySet()) {
            long oldValue = previous.getOrDefault(entry.getKey(), 0L);
            long delta = entry.getValue() - oldValue;
            if (delta != 0) {
                deltas.put(entry.getKey(), delta);
            }
        }
        for (Map.Entry<String, Long> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey()) && entry.getValue() != 0) {
                deltas.put(entry.getKey(), -entry.getValue());
            }
        }
        return deltas;
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

        return oldStats.withSample(energy.getEnergyStored(), energy.getMaxEnergyStored());
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
        ae2ItemAmounts.clear();
        ae2ItemDeltas.clear();
        ae2CellCapacityMetrics.clear();
        debugInfoMap.clear();
        capacityWarnSignatureMap.clear();
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
