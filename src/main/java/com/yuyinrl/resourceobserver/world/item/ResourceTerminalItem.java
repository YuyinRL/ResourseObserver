package com.yuyinrl.resourceobserver.world.item;

import appeng.api.networking.IGrid;
import com.yuyinrl.resourceobserver.integration.CraftingDataCollector;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import com.yuyinrl.resourceobserver.world.block.entity.NetworkRef;
import com.yuyinrl.resourceobserver.world.block.entity.Ae2CellCapacityMetrics;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源终端物品 —— 用于打开 GUI 查看观察者绑定的资源网络数据与图表。
 * <p>
 * 交互流程：
 * 1. 右键点击观察者方块 → 将终端绑定到该观察者（坐标存储在物品 NBT 中）
 * 2. Shift + 右键观察者方块 → 解除终端与观察者的绑定
 * 3. 空中右键使用 → 从绑定的观察者读取数据，构建 Payload 发送到客户端打开 GUI
 * <p>
 * 数据构建：
 * - 从 ObserverBlockEntity 读取所有绑定的网络和统计数据
 * - 从 HistoryRecorder 查询历史图表数据
 * - 从 PlayerUiPrefsSavedData 读取玩家 UI 偏好（关注列表、分组、排序等）
 * - 将以上数据组装为 ObserverDataPayload 通过网络发送给客户端
 */
public class ResourceTerminalItem extends Item {
    // ========== NBT 标签常量 ==========
    public static final String TAG_BOUND = "bound_observer";
    public static final String TAG_HAS_NETWORK = "observer_has_network";
    private static final String TAG_INITIALIZED = "initialized";
    private static final String TAG_X = "observer_x";
    private static final String TAG_Y = "observer_y";
    private static final String TAG_Z = "observer_z";
    private static final String TAG_DIMENSION = "observer_dimension";
    private static final String TAG_BINDINGS = "network_bindings";
    private static final String TAG_BIND_TYPE = "type";
    private static final String TAG_BIND_TARGET_POS = "target_pos";
    private static final String TAG_BIND_BLOCK_ID = "block_id";
    /** 是否优先显示调试视图（DebugTerminalItem 设为 true） */
    private final boolean debugPreferred;

    public ResourceTerminalItem(Properties properties) {
        this(properties, false);
    }

    public ResourceTerminalItem(Properties properties, boolean debugPreferred) {
        super(properties);
        this.debugPreferred = debugPreferred;
    }

    /**
     * 物品进入玩家背包后每 tick 调用。首次进入时打上初始化标记，
     * 使客户端物品属性能够区分"创造栏/合成预览"（无 NBT → 正常贴图）
     * 与"背包中未绑定"（有 NBT → 错误贴图）。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing == null) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(TAG_INITIALIZED, true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * 右键点击方块时的交互逻辑。
     * 仅处理观察者方块：普通右键绑定终端，Shift+右键解除绑定。
     * 点击非观察者方块时返回 PASS，交由 use() 处理空中使用。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        // 仅处理观察者方块，其他方块跳过
        if (!(level.getBlockState(clickedPos).getBlock() instanceof ObserverBlock)
                || !(blockEntity instanceof ObserverBlockEntity observer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        // Shift + 右键 → 解除终端绑定（清除物品 NBT）
        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_unbound"));
            return InteractionResult.SUCCESS;
        }

        // 普通右键 → 将观察者坐标和维度写入物品 NBT
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_BOUND, true);
        tag.putBoolean(TAG_HAS_NETWORK, observer.isBound());
        tag.putInt(TAG_X, clickedPos.getX());
        tag.putInt(TAG_Y, clickedPos.getY());
        tag.putInt(TAG_Z, clickedPos.getZ());
        tag.putString(TAG_DIMENSION, level.dimension().location().toString());
        writeBindingsToTag(tag, observer);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        player.sendSystemMessage(Component.translatable(
                "message.resourceobserver.terminal_bound",
                clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
        player.sendSystemMessage(observer.createStatusMessage());
        return InteractionResult.SUCCESS;
    }

    /**
     * 空中右键使用终端。
     * 从物品 NBT 中读取绑定的观察者坐标，验证维度一致性后发送数据到客户端打开 GUI。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 读取物品 NBT 中的绑定信息
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.copyTag().getBoolean(TAG_BOUND)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_not_bound"));
                return InteractionResultHolder.fail(stack);
            }

            // 验证维度是否一致
            CompoundTag tag = customData.copyTag();
            String dimension = tag.getString(TAG_DIMENSION);
            if (!dimension.equals(level.dimension().location().toString())) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_wrong_dimension"));
                return InteractionResultHolder.fail(stack);
            }

            // 从绑定的观察者方块读取数据并发送到客户端
            BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof ObserverBlockEntity observer)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_missing"));
                return InteractionResultHolder.fail(stack);
            }
            // 观察者未绑定任何网络时拒绝打开 UI，并同步 NBT 状态以更新贴图
            if (!observer.isBound()) {
                syncObserverState(stack, tag, observer);
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_no_network"));
                return InteractionResultHolder.fail(stack);
            }
            syncObserverState(stack, tag, observer);
            if (!sendObserverData(serverPlayer, level, pos, debugPreferred, ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, "")) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_missing"));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 同步观察者方块的网络状态与绑定列表到物品 NBT，用于客户端贴图切换和 Tooltip 显示。
     */
    private static void syncObserverState(ItemStack stack, CompoundTag tag, ObserverBlockEntity observer) {
        tag.putBoolean(TAG_HAS_NETWORK, observer.isBound());
        writeBindingsToTag(tag, observer);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 将观察者的所有网络绑定信息序列化到 NBT 列表中 */
    private static void writeBindingsToTag(CompoundTag tag, ObserverBlockEntity observer) {
        var entries = observer.getBindings();
        var list = new net.minecraft.nbt.ListTag();
        for (var entry : entries) {
            CompoundTag bindTag = new CompoundTag();
            bindTag.putString(TAG_BIND_TYPE, entry.networkType());
            bindTag.putString(TAG_BIND_BLOCK_ID, entry.targetBlockId());
            // 从 networkId 中提取目标坐标
            BlockPos targetPos = extractPosFromNetworkId(entry.networkId());
            if (targetPos != null) {
                bindTag.putString(TAG_BIND_TARGET_POS,
                        targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ());
            }
            list.add(bindTag);
        }
        tag.put(TAG_BINDINGS, list);
    }

    /** 从 networkId（格式 "blockId@posLong"）中解析目标方块坐标 */
    private static BlockPos extractPosFromNetworkId(String networkId) {
        return NetworkRef.extractPos(networkId);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.unbound")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.getBoolean(TAG_BOUND)) {
            tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.unbound")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return;
        }

        // 观察者坐标（始终显示）
        int ox = tag.getInt(TAG_X);
        int oy = tag.getInt(TAG_Y);
        int oz = tag.getInt(TAG_Z);
        tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.observer_pos", ox, oy, oz)
                .withStyle(net.minecraft.ChatFormatting.GRAY));

        // 绑定数量摘要（始终显示）
        if (!tag.contains(TAG_BINDINGS)) {
            tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.no_network")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return;
        }
        var list = tag.getList(TAG_BINDINGS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.no_network")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return;
        }
        tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.networks_header", list.size())
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        // 详细绑定信息仅在按住 Shift 时显示
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (!showDetails) {
            tooltipComponents.add(Component.translatable("tooltip.resourceobserver.terminal.shift_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag bindTag = list.getCompound(i);
            String type = bindTag.getString(TAG_BIND_TYPE);
            String pos = bindTag.getString(TAG_BIND_TARGET_POS);
            String blockId = bindTag.getString(TAG_BIND_BLOCK_ID);
            tooltipComponents.add(Component.literal("  #" + (i + 1) + " ")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                    .append(Component.literal(type)
                            .withStyle(net.minecraft.ChatFormatting.DARK_GREEN))
                    .append(Component.literal(" → " + blockId)
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" (" + pos + ")")
                            .withStyle(net.minecraft.ChatFormatting.DARK_AQUA)));
        }
    }

    /** 简化版数据发送方法，使用默认图表参数 */
    public static boolean sendObserverData(ServerPlayer player, Level level, BlockPos observerPos, boolean debugPreferred) {
        return sendObserverData(player, level, observerPos, debugPreferred, ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, "");
    }

    /**
     * 从观察者方块实体读取数据，构建 ObserverDataPayload 并通过网络发送给指定玩家。
     * 支持指定图表时间窗口、作用域（全局/单物品）和作用域物品 ID。
     * @return 是否成功发送（观察者方块不存在时返回 false）
     */
    public static boolean sendObserverData(
            ServerPlayer player,
            Level level,
            BlockPos observerPos,
            boolean debugPreferred,
            ChartWindow chartWindow,
            ChartScope chartScope,
            String scopeItemId
    ) {
        BlockEntity blockEntity = level.getBlockEntity(observerPos);
        if (!(blockEntity instanceof ObserverBlockEntity observer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, buildPayload(player, serverLevel, observerPos, observer, debugPreferred, chartWindow, chartScope, scopeItemId));
        return true;
    }

    /**
     * 构建完整的观察者数据负载（ObserverDataPayload）。
     * <p>
     * 数据组装流程：
     * 1. 读取玩家 UI 偏好（排序、筛选、分组、关注列表）
     * 2. 遍历所有网络绑定，为每个绑定构建 BindingEntry（含统计和物品增量）
     * 3. 查询历史图表数据点
     * 4. 构建分组定义列表
     * 5. 组装最终的 Payload 对象
     */
    private static ObserverDataPayload buildPayload(
            ServerPlayer player,
            ServerLevel level,
            BlockPos observerPos,
            ObserverBlockEntity observer,
            boolean debugPreferred,
            ChartWindow chartWindow,
            ChartScope chartScope,
            String scopeItemId
    ) {
        // 获取玩家 UI 偏好快照（关注列表、分组、排序模式等）
        PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs = PlayerUiPrefsSavedData.get(level).getSnapshot(player.getUUID());
        List<BoundEntry> effectiveBindings = deduplicateBindingsForPayload(observer);

        // 构建各绑定网络的数据条目
        List<ObserverDataPayload.BindingEntry> entries = new ArrayList<>();
        List<ObserverDataPayload.CraftingBindingData> craftingBindings = new ArrayList<>();
        for (BoundEntry binding : effectiveBindings) {
            BindingStats stats = observer.getStatsFor(binding.networkId());
            List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = buildItemDeltas(binding, observer, uiPrefs);
            ObserverDataPayload.KpiWindowStats kpiWindowStats = observer.getKpiStats(binding.networkId());
            entries.add(new ObserverDataPayload.BindingEntry(
                    binding.networkType(),
                    binding.networkId(),
                    binding.targetBlockId(),
                    resolveBindingDisplayName(binding, uiPrefs),
                    bindingIcon(binding),
                    stats.currentValue(),
                    stats.capacity(),
                    toCellCapacityMetrics(observer, binding),
                    stats.totalProduced(),
                    stats.totalConsumed(),
                    kpiWindowStats,
                    itemDeltas,
                    observer.getDebugInfoFor(binding.networkId())
            ));
            // AE2_ITEMS 绑定追加合成数据快照
            if ("AE2_ITEMS".equals(binding.networkType())) {
                craftingBindings.add(buildCraftingBindingData(observer, binding.networkId()));
            }
        }

        // 查询历史图表数据点
        ChartSeriesResult chartResult = buildChartSeries(level, observerPos, effectiveBindings, chartWindow, chartScope, scopeItemId);

        // 构建分组定义列表
        List<ObserverDataPayload.GroupEntry> groups = buildGroupEntries(uiPrefs);

        // 组装最终数据负载
        return new ObserverDataPayload(
                observerPos,
                observer.isBound(),
                debugPreferred,
                chartWindow,
                chartScope,
                scopeItemId == null ? "" : scopeItemId,
                chartResult.itemSeries,
                chartResult.energySeries,
                uiPrefs.groupFilterKey(),
                uiPrefs.sortMode(),
                uiPrefs.sortDesc(),
                uiPrefs.statusFilter(),
                PlayerUiPrefsSavedData.WATCHLIST_LIMIT,
                uiPrefs.watchlistItemIds(),
                groups,
                entries,
                craftingBindings
        );
    }

    /** 采集单个 AE2 绑定网络的合成数据快照（反射，失败返回空）。 */
    private static ObserverDataPayload.CraftingBindingData buildCraftingBindingData(
            ObserverBlockEntity observer,
            String networkId
    ) {
        IGrid grid = observer.resolveAe2GridForNetwork(networkId);
        if (grid == null) {
            return ObserverDataPayload.CraftingBindingData.empty(networkId);
        }
        List<CraftingDataCollector.CraftableEntry> craftables = CraftingDataCollector.collectCraftables(grid);
        List<CraftingDataCollector.CraftingJobEntry> jobs = CraftingDataCollector.collectActiveJobs(grid);
        CraftingDataCollector.CraftingStorageMetrics storage = CraftingDataCollector.collectStorageMetrics(grid);

        // Payload 侧限制到 256 条避免爆包
        final int CRAFTABLES_LIMIT = 256;
        int craftableCount = Math.min(craftables.size(), CRAFTABLES_LIMIT);
        List<ObserverDataPayload.CraftableSnapshot> craftableSnapshots = new ArrayList<>(craftableCount);
        for (int i = 0; i < craftableCount; i++) {
            CraftingDataCollector.CraftableEntry c = craftables.get(i);
            craftableSnapshots.add(new ObserverDataPayload.CraftableSnapshot(c.itemId(), c.displayName()));
        }

        List<ObserverDataPayload.CraftingJobSnapshot> jobSnapshots = new ArrayList<>(jobs.size());
        for (CraftingDataCollector.CraftingJobEntry j : jobs) {
            jobSnapshots.add(new ObserverDataPayload.CraftingJobSnapshot(
                    j.cpuName() == null ? "" : j.cpuName(),
                    j.outputItemId() == null ? "" : j.outputItemId(),
                    j.outputDisplayName() == null ? "" : j.outputDisplayName(),
                    j.totalAmount(),
                    j.remainingAmount(),
                    j.busy(),
                    j.jobId() == null ? "" : j.jobId(),
                    j.storageBytes(),
                    j.coProcessors(),
                    j.progressFraction(),
                    j.elapsedMillis(),
                    j.treeId() == null ? "" : j.treeId()
            ));
        }
        ObserverDataPayload.CraftingStorageSnapshot storageSnapshot = new ObserverDataPayload.CraftingStorageSnapshot(
                storage.cpuCount(),
                storage.busyCpuCount(),
                storage.totalStorageBytes(),
                storage.totalCoProcessors(),
                storage.reliable()
        );
        return new ObserverDataPayload.CraftingBindingData(networkId, jobSnapshots, craftableSnapshots, storageSnapshot);
    }

    /** 图表数据查询结果 —— 包含物品图表和能量图表两组数据系列 */
    private record ChartSeriesResult(
            List<ObserverDataPayload.ChartPoint> itemSeries,
            List<ObserverDataPayload.ChartPoint> energySeries
    ) {
    }

    /** 查询历史图表数据点，按物品绑定和能量绑定分别聚合 */
    private static ChartSeriesResult buildChartSeries(
            ServerLevel level,
            BlockPos observerPos,
            List<BoundEntry> effectiveBindings,
            ChartWindow chartWindow,
            ChartScope chartScope,
            String scopeItemId
    ) {
        List<BoundEntry> itemBindings = new ArrayList<>();
        List<BoundEntry> energyBindings = new ArrayList<>();
        for (BoundEntry binding : effectiveBindings) {
            if ("FLUX_ENERGY".equals(binding.networkType())) {
                energyBindings.add(binding);
            } else {
                itemBindings.add(binding);
            }
        }
        List<ObserverDataPayload.ChartPoint> chartSeries = HistoryRecorder.querySeries(
                level, observerPos, itemBindings, chartWindow, chartScope, scopeItemId
        );
        List<ObserverDataPayload.ChartPoint> energyChartSeries = HistoryRecorder.querySeries(
                level, observerPos, energyBindings, chartWindow, chartScope, null
        );
        return new ChartSeriesResult(chartSeries, energyChartSeries);
    }

    /** 构建分组定义列表（包含系统默认分组和玩家自定义分组） */
    private static List<ObserverDataPayload.GroupEntry> buildGroupEntries(
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        List<ObserverDataPayload.GroupEntry> groups = new ArrayList<>();
        for (PlayerUiPrefsSavedData.GroupDefinition group : uiPrefs.groups()) {
            groups.add(new ObserverDataPayload.GroupEntry(group.key(), group.displayName(), group.systemGroup()));
        }
        return groups;
    }

    /**
     * 根据绑定网络类型构建物品增量列表。
     * AE2_ITEMS：从观察者读取库存和速率数据；FLUX_ENERGY：从采样结果构建能量指标。
     */
    private static List<ObserverDataPayload.ItemDeltaEntry> buildItemDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return buildAe2ItemDeltas(binding, observer, uiPrefs);
        } else if ("FLUX_ENERGY".equals(binding.networkType())) {
            return buildFluxEnergyDeltas(binding, observer);
        }
        return new ArrayList<>();
    }

    /** 构建 AE2 物品存储网络的每种物品增量数据 */
    private static List<ObserverDataPayload.ItemDeltaEntry> buildAe2ItemDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer,
            PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs
    ) {
        List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = new ArrayList<>();
        Map<String, Long> amounts = observer.getAe2ItemAmountsFor(binding.networkId());
        // 使用 EMA 平滑后的每分钟速率（而非瞬时 delta），避免合成机脉冲式采样导致大量零值
        Map<String, Double> ratesPerMin = observer.getAe2ItemRatesPerMinFor(binding.networkId());
        Map<String, Double> prodRatesPerMin = observer.getAe2ItemProdRatesPerMinFor(binding.networkId());
        Map<String, Double> consRatesPerMin = observer.getAe2ItemConsRatesPerMinFor(binding.networkId());
        // 遍历当前库存中的所有物品
        for (Map.Entry<String, Long> amountEntry : amounts.entrySet()) {
            String itemId = amountEntry.getKey();
            ObserverDataPayload.EntryType entryType = entryTypeForId(itemId);
            itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                    entryType,
                    itemId,
                    toDisplayName(itemId, entryType),
                    uiPrefs.groupKeyForItem(itemId),
                    iconSpriteForEntryType(entryType),
                    amountEntry.getValue(),
                    ratesPerMin.getOrDefault(itemId, 0.0),
                    prodRatesPerMin.getOrDefault(itemId, 0.0),
                    consRatesPerMin.getOrDefault(itemId, 0.0)
            ));
        }
        // 添加已消失但有速率记录的物品（当前数量为 0，仍有消耗记录）
        for (Map.Entry<String, Double> rateEntry : ratesPerMin.entrySet()) {
            String itemId = rateEntry.getKey();
            if (amounts.containsKey(itemId)) {
                continue;
            }
            ObserverDataPayload.EntryType entryType = entryTypeForId(itemId);
            itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                    entryType,
                    itemId,
                    toDisplayName(itemId, entryType),
                    uiPrefs.groupKeyForItem(itemId),
                    iconSpriteForEntryType(entryType),
                    0L,
                    rateEntry.getValue(),
                    prodRatesPerMin.getOrDefault(itemId, 0.0),
                    consRatesPerMin.getOrDefault(itemId, 0.0)
            ));
        }
        // 按物品 ID 排序保证顺序一致性
        itemDeltas.sort(
                Comparator.comparing(ObserverDataPayload.ItemDeltaEntry::entryType)
                        .thenComparing(ObserverDataPayload.ItemDeltaEntry::itemId)
        );
        return itemDeltas;
    }

    /** 构建 Flux Networks 能量网络的能量指标和设备级增量数据 */
    private static List<ObserverDataPayload.ItemDeltaEntry> buildFluxEnergyDeltas(
            BoundEntry binding,
            ObserverBlockEntity observer
    ) {
        List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = new ArrayList<>();
        FluxNetworksIntegration.FluxSampleResult fluxResult = observer.getFluxSampleResultFor(binding.networkId());
        if (fluxResult == null) {
            return itemDeltas;
        }
        // 网络级能量指标
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.input_per_tick", "Input/t",
                "flux_metrics", "resourceobserver:terminal/kpi_production",
                fluxResult.energyInput(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.output_per_tick", "Output/t",
                "flux_metrics", "resourceobserver:terminal/kpi_consumption",
                fluxResult.energyOutput(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.energy_stored", "Energy Stored",
                "flux_metrics", "resourceobserver:terminal/kpi_storage",
                fluxResult.totalEnergy(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.total_buffer", "Total Buffer",
                "flux_metrics", "resourceobserver:terminal/kpi_storage",
                fluxResult.totalBuffer(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.max_energy_storage", "Max Energy Storage",
                "flux_metrics", "resourceobserver:terminal/kpi_storage",
                fluxResult.totalMaxEnergyStorage(), 0L, 0L, 0L
        ));
        // 设备连接器数量
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.plug_count", "Plugs (Input)",
                "flux_connectors", "resourceobserver:terminal/kpi_production",
                fluxResult.plugCount(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.point_count", "Points (Output)",
                "flux_connectors", "resourceobserver:terminal/kpi_consumption",
                fluxResult.pointCount(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.storage_count", "Storage Blocks",
                "flux_connectors", "resourceobserver:terminal/kpi_storage",
                fluxResult.storageCount(), 0L, 0L, 0L
        ));
        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                ObserverDataPayload.EntryType.ITEM,
                "flux.controller_count", "Controllers",
                "flux_connectors", "resourceobserver:terminal/kpi_efficiency",
                fluxResult.controllerCount(), 0L, 0L, 0L
        ));
        // 每个设备的详细传输数据
        appendFluxDeviceDeltas(itemDeltas, fluxResult);
        return itemDeltas;
    }

    /** 追加 Flux 设备级详细传输数据和外部能量存储信息 */
    private static void appendFluxDeviceDeltas(
            List<ObserverDataPayload.ItemDeltaEntry> itemDeltas,
            FluxNetworksIntegration.FluxSampleResult fluxResult
    ) {
        for (int devIdx = 0; devIdx < fluxResult.devices().size(); devIdx++) {
            FluxNetworksIntegration.FluxDeviceSnapshot device = fluxResult.devices().get(devIdx);
            String deviceKey = "flux.device." + devIdx + "." + device.deviceType();
            String deviceName = device.customName().isEmpty()
                    ? device.deviceType().toUpperCase() + " @ " + device.posKey()
                    : device.customName();
            itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                    ObserverDataPayload.EntryType.ITEM,
                    deviceKey, deviceName,
                    "flux_devices", "resourceobserver:terminal/kpi_consumption",
                    Math.abs(device.transferBuffer()),
                    device.transferChange(), 0L, 0L
            ));
            // 外部容器能量数据（仅 PLUG/POINT 有效）
            if (device.externalEnergyCapacity() > 0) {
                itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                        ObserverDataPayload.EntryType.ITEM,
                        "flux.device." + devIdx + ".ext_stored",
                        deviceName + " [Ext]",
                        "flux_devices", "resourceobserver:terminal/kpi_storage",
                        device.externalEnergyStored(),
                        0L, 0L, 0L
                ));
                itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                        ObserverDataPayload.EntryType.ITEM,
                        "flux.device." + devIdx + ".ext_cap",
                        deviceName + " [Ext Cap]",
                        "flux_devices", "resourceobserver:terminal/kpi_storage",
                        device.externalEnergyCapacity(),
                        0L, 0L, 0L
                ));
            }
            // 新版外储设备明细（每个外储设备独立上报，支持多接口分组去重）
            if (device.externalRefs() != null && !device.externalRefs().isEmpty()) {
                for (FluxNetworksIntegration.ExternalEnergyRef ref : device.externalRefs()) {
                    String extId = ref.extId() == null ? "" : ref.extId();
                    if (extId.isBlank()) {
                        continue;
                    }
                    String extName = ref.displayName() == null || ref.displayName().isBlank()
                            ? extId
                            : ref.displayName();
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            ObserverDataPayload.EntryType.ITEM,
                            "flux.device." + devIdx + ".ext." + extId + ".stored",
                            extName,
                            "flux_devices", "resourceobserver:terminal/kpi_storage",
                            ref.stored(),
                            0L, 0L, 0L
                    ));
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            ObserverDataPayload.EntryType.ITEM,
                            "flux.device." + devIdx + ".ext." + extId + ".cap",
                            extName + " [Cap]",
                            "flux_devices", "resourceobserver:terminal/kpi_storage",
                            ref.capacity(),
                            0L, 0L, 0L
                    ));
                    // 设备最大接受速率（用于计算负载利用率）
                    if (ref.maxAcceptPerTick() > 0L) {
                        itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                                ObserverDataPayload.EntryType.ITEM,
                                "flux.device." + devIdx + ".ext." + extId + ".maxAccept",
                                extName + " [MaxAccept]",
                                "flux_devices", "resourceobserver:terminal/kpi_storage",
                                ref.maxAcceptPerTick(),
                                0L, 0L, 0L
                        ));
                    }
                }
            }
        }
    }

    /** 根据网络类型返回绑定的显示名称 */
    private static String bindingDisplayName(BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "Storage Network";     // AE2 物品存储网络
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "Power Network";       // Flux 能量网络
        }
        return "Linked Network";          // 未知类型的默认名称
    }

    /** 优先使用玩家自定义名称，无自定义名称时回退到默认名称 */
    private static String resolveBindingDisplayName(BoundEntry binding,
                                                     PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs) {
        String custom = uiPrefs.customNameForNetwork(binding.networkId());
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return bindingDisplayName(binding);
    }

    /** 根据网络类型返回对应的图标精灵路径 */
    private static String bindingIcon(BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_storage";
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_consumption";
        }
        return "resourceobserver:terminal/kpi_efficiency";
    }

    private static List<BoundEntry> deduplicateBindingsForPayload(ObserverBlockEntity observer) {
        List<BoundEntry> source = observer.getBindings();
        if (source.size() <= 1) {
            return source;
        }

        Set<IGrid> seenAe2Grids = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BoundEntry> deduped = new ArrayList<>(source.size());
        for (BoundEntry binding : source) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                deduped.add(binding);
                continue;
            }
            IGrid grid = observer.resolveAe2GridForNetwork(binding.networkId());
            if (grid == null || seenAe2Grids.add(grid)) {
                deduped.add(binding);
            }
        }
        return deduped;
    }

    private static ObserverDataPayload.CellCapacityMetrics toCellCapacityMetrics(
            ObserverBlockEntity observer,
            BoundEntry binding
    ) {
        if (!"AE2_ITEMS".equals(binding.networkType())) {
            return ObserverDataPayload.CellCapacityMetrics.unavailable();
        }
        Ae2CellCapacityMetrics metrics = observer.getAe2CellCapacityMetricsFor(binding.networkId());
        return new ObserverDataPayload.CellCapacityMetrics(
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
                metrics.externalItemUsedUnits(),
                metrics.externalItemTotalUnits(),
                metrics.externalFluidUsedUnits(),
                metrics.externalFluidTotalUnits(),
                metrics.scope(),
                metrics.reliable(),
                metrics.available(),
                metrics.externalReliable(),
                metrics.externalAvailable()
        );
    }

    /**
     * 将物品 ID（如 "minecraft:iron_ingot"）转换为可读的显示名称（如 "Iron Ingot"）。
     * 提取冒号后的路径部分，按下划线分割后首字母大写拼接。
     */
    private static ObserverDataPayload.EntryType entryTypeForId(String itemId) {
        if (itemId != null && itemId.startsWith("fluid:")) {
            return ObserverDataPayload.EntryType.FLUID;
        }
        return ObserverDataPayload.EntryType.ITEM;
    }

    private static String iconSpriteForEntryType(ObserverDataPayload.EntryType entryType) {
        if (entryType == ObserverDataPayload.EntryType.FLUID) {
            return "resourceobserver:terminal/watch_item";
        }
        return "resourceobserver:terminal/table_item";
    }

    private static String toDisplayName(String itemId, ObserverDataPayload.EntryType entryType) {
        String id = itemId == null ? "" : itemId;
        if (entryType == ObserverDataPayload.EntryType.FLUID && id.startsWith("fluid:")) {
            id = id.substring("fluid:".length());
        }
        int idx = id.indexOf(':');
        String path = idx >= 0 ? id.substring(idx + 1) : id;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        if (sb.length() == 0) {
            return itemId;
        }
        return sb.toString();
    }
}
