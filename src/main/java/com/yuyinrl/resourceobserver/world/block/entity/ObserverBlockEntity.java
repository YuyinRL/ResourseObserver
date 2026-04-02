package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 当前所有网络绑定列表 */
    private final List<BoundEntry> bindings = new ArrayList<>();
    /** 每个网络 ID 对应的统计数据 */
    private final Map<String, BindingStats> statsMap = new HashMap<>();
    /** AE2 网络中每种物品的当前数量快照（networkId -> {itemId -> amount}） */
    private final Map<String, Map<String, Long>> ae2ItemAmounts = new HashMap<>();
    /** AE2 网络中每种物品的数量变化量（networkId -> {itemId -> delta}） */
    private final Map<String, Map<String, Long>> ae2ItemDeltas = new HashMap<>();
    /** 调试信息映射，记录每个网络的采样状态 */
    private final Map<String, String> debugInfoMap = new HashMap<>();
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
        debugInfoMap.clear();
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
        for (long value : snapshot.values()) {
            totalAmount += value;
        }

        long produced = 0;
        long consumed = 0;
        for (long delta : deltas.values()) {
            if (delta > 0) {
                produced += delta;
            } else if (delta < 0) {
                consumed += -delta;
            }
        }

        return oldStats.withDelta(totalAmount, snapshot.size(), produced, consumed);
    }

    /**
     * AE2 网络读取结果。
     * @param snapshot  物品快照（itemId -> amount），null 表示读取失败
     * @param debugInfo 调试信息字符串
     */
    private record Ae2ReadResult(@Nullable Map<String, Long> snapshot, String debugInfo) {
    }

    /**
     * 通过 AE2 API 读取目标方块所在网络的物品库存。
     * 流程：获取 GridNodeHost → 遍历方向获取 GridNode → 获取 Grid → 读取 StorageService 缓存库存。
     * 仅统计 AEItemKey 类型的存储（忽略流体等）。
     */
    private Ae2ReadResult readAe2NetworkItems(BlockPos targetPos) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos, null);
        if (host == null) {
            return new Ae2ReadResult(null, "channel=ae2.in_world_grid_node_host;status=missing_host;target=" + targetPos.toShortString());
        }

        IGridNode node = null;
        for (Direction dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null) {
                break;
            }
        }
        if (node == null) {
            return new Ae2ReadResult(null, "channel=ae2.grid_node;status=missing_node;target=" + targetPos.toShortString());
        }

        final IGrid grid;
        try {
            grid = node.getGrid();
        } catch (IllegalStateException ex) {
            return new Ae2ReadResult(null, "channel=ae2.grid;status=offline;error=" + ex.getMessage());
        }

        IStorageService storageService = grid.getStorageService();
        KeyCounter cachedInventory = storageService.getCachedInventory();
        Map<String, Long> snapshot = new HashMap<>();
        for (AEKey key : cachedInventory.keySet()) {
            if (!(key instanceof AEItemKey itemKey)) {
                continue;
            }
            long amount = cachedInventory.get(key);
            if (amount <= 0) {
                continue;
            }
            String itemId = itemKey.getId().toString();
            snapshot.merge(itemId, amount, Long::sum);
        }
        String debug = "channel=ae2.cached_inventory;status=ok;types=" + snapshot.size();
        return new Ae2ReadResult(snapshot, debug);
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
        debugInfoMap.clear();
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
