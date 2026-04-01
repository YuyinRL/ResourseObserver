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

public class ObserverBlockEntity extends BlockEntity {
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
    private static final int SAMPLE_INTERVAL = 10;

    private final List<BoundEntry> bindings = new ArrayList<>();
    private final Map<String, BindingStats> statsMap = new HashMap<>();
    private final Map<String, Map<String, Long>> ae2ItemAmounts = new HashMap<>();
    private final Map<String, Map<String, Long>> ae2ItemDeltas = new HashMap<>();
    private final Map<String, String> debugInfoMap = new HashMap<>();
    private long lastSampleTick = -1;

    public record BoundEntry(String networkType, String networkId, String targetBlockId) {
    }

    public record BindingStats(
            long currentValue,
            long capacity,
            long totalProduced,
            long totalConsumed,
            long previousValue
    ) {
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

        public BindingStats withDelta(long newValue, long newCapacity, long producedDelta, long consumedDelta) {
            return new BindingStats(
                    newValue,
                    newCapacity,
                    totalProduced + Math.max(0, producedDelta),
                    totalConsumed + Math.max(0, consumedDelta),
                    newValue
            );
        }

        public static BindingStats empty() {
            return new BindingStats(0, 0, 0, 0, -1);
        }
    }

    public ObserverBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OBSERVER_BLOCK_ENTITY.get(), pos, blockState);
    }

    /**
     * Add a new binding. Returns false if this exact networkId is already bound (duplicate).
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

    public void clearAllBindings() {
        bindings.clear();
        statsMap.clear();
        ae2ItemAmounts.clear();
        ae2ItemDeltas.clear();
        debugInfoMap.clear();
        setChanged();
    }

    public boolean isBound() {
        return !bindings.isEmpty();
    }

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

    public static void tick(Level level, BlockPos pos, BlockState state, ObserverBlockEntity be) {
        if (level.isClientSide || be.bindings.isEmpty()) return;
        long gameTime = level.getGameTime();
        if (be.lastSampleTick >= 0 && gameTime - be.lastSampleTick < SAMPLE_INTERVAL) return;
        be.lastSampleTick = gameTime;
        be.sampleAll();
    }

    private void sampleAll() {
        if (level == null) return;
        for (BoundEntry binding : bindings) {
            BlockPos targetPos = extractPos(binding.networkId());
            if (targetPos == null || !level.isLoaded(targetPos)) continue;

            BindingStats oldStats = statsMap.getOrDefault(binding.networkId(), BindingStats.empty());
            BindingStats newStats;
            Map<String, Long> itemDeltaSnapshot = Map.of();
            Map<String, Long> itemAmountSnapshot = Map.of();

            if ("AE2_ITEMS".equals(binding.networkType())) {
                newStats = sampleAe2Network(binding.networkId(), targetPos, oldStats);
                itemDeltaSnapshot = getAe2ItemDeltasFor(binding.networkId());
                itemAmountSnapshot = getAe2ItemAmountsFor(binding.networkId());
            } else if ("FLUX_ENERGY".equals(binding.networkType())) {
                newStats = sampleEnergy(targetPos, oldStats);
                debugInfoMap.put(binding.networkId(), "channel=neoforge.energy;target=" + targetPos.toShortString());
            } else {
                continue;
            }
            statsMap.put(binding.networkId(), newStats);

            if (level instanceof ServerLevel serverLevel) {
                long producedDelta = Math.max(0L, newStats.totalProduced() - oldStats.totalProduced());
                long consumedDelta = Math.max(0L, newStats.totalConsumed() - oldStats.totalConsumed());
                HistoryRecorder.recordSample(
                        serverLevel,
                        worldPosition,
                        binding,
                        producedDelta,
                        consumedDelta,
                        newStats.currentValue(),
                        itemDeltaSnapshot,
                        itemAmountSnapshot
                );
            }
        }
        setChanged();
    }

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

    private record Ae2ReadResult(@Nullable Map<String, Long> snapshot, String debugInfo) {
    }

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

    private static BlockPos extractPos(String networkId) {
        int atIdx = networkId.lastIndexOf('@');
        if (atIdx < 0) return null;
        try {
            return BlockPos.of(Long.parseLong(networkId.substring(atIdx + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

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
