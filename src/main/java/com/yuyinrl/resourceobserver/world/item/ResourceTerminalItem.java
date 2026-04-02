package com.yuyinrl.resourceobserver.world.item;

import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    private static final String TAG_BOUND = "bound_observer";
    private static final String TAG_X = "observer_x";
    private static final String TAG_Y = "observer_y";
    private static final String TAG_Z = "observer_z";
    private static final String TAG_DIMENSION = "observer_dimension";
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
        tag.putInt(TAG_X, clickedPos.getX());
        tag.putInt(TAG_Y, clickedPos.getY());
        tag.putInt(TAG_Z, clickedPos.getZ());
        tag.putString(TAG_DIMENSION, level.dimension().location().toString());
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
            if (!sendObserverData(serverPlayer, level, pos, debugPreferred, ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, "")) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_missing"));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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

        // 构建各绑定网络的数据条目
        List<ObserverDataPayload.BindingEntry> entries = new ArrayList<>();
        for (ObserverBlockEntity.BoundEntry binding : observer.getBindings()) {
            ObserverBlockEntity.BindingStats stats = observer.getStatsFor(binding.networkId());
            List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = new ArrayList<>();
            // AE2 网络需要构建每种物品的增量数据
            if ("AE2_ITEMS".equals(binding.networkType())) {
                Map<String, Long> amounts = observer.getAe2ItemAmountsFor(binding.networkId());
                Map<String, Long> deltas = observer.getAe2ItemDeltasFor(binding.networkId());
                // 遍历当前库存中的所有物品
                for (Map.Entry<String, Long> amountEntry : amounts.entrySet()) {
                    String itemId = amountEntry.getKey();
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            itemId,
                            toDisplayName(itemId),   // 将 item_id 转为可读显示名
                            uiPrefs.groupKeyForItem(itemId), // 获取玩家自定义分组
                            "resourceobserver:terminal/table_item",
                            amountEntry.getValue(),
                            deltas.getOrDefault(itemId, 0L)
                    ));
                }
                // 添加已消失但有增量变化的物品（当前数量为 0）
                for (Map.Entry<String, Long> deltaEntry : deltas.entrySet()) {
                    String itemId = deltaEntry.getKey();
                    if (amounts.containsKey(itemId)) {
                        continue; // 已在上面处理过
                    }
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            itemId,
                            toDisplayName(itemId),
                            uiPrefs.groupKeyForItem(itemId),
                            "resourceobserver:terminal/table_item",
                            0L,
                            deltaEntry.getValue()
                    ));
                }
                // 按物品 ID 排序保证顺序一致性
                itemDeltas.sort(Comparator.comparing(ObserverDataPayload.ItemDeltaEntry::itemId));
            }
            entries.add(new ObserverDataPayload.BindingEntry(
                    binding.networkType(),
                    binding.networkId(),
                    binding.targetBlockId(),
                    bindingDisplayName(binding),
                    bindingIcon(binding),
                    stats.currentValue(),
                    stats.capacity(),
                    stats.totalProduced(),
                    stats.totalConsumed(),
                    itemDeltas,
                    observer.getDebugInfoFor(binding.networkId())
            ));
        }

        // 查询历史图表数据点（聚合所有绑定的数据）
        List<ObserverDataPayload.ChartPoint> chartSeries = HistoryRecorder.querySeries(
                level,
                observerPos,
                observer.getBindings(),
                chartWindow,
                chartScope,
                scopeItemId
        );

        // 构建分组定义列表（包含系统默认分组和玩家自定义分组）
        List<ObserverDataPayload.GroupEntry> groups = new ArrayList<>();
        for (PlayerUiPrefsSavedData.GroupDefinition group : uiPrefs.groups()) {
            groups.add(new ObserverDataPayload.GroupEntry(group.key(), group.displayName(), group.systemGroup()));
        }

        // 组装最终数据负载
        return new ObserverDataPayload(
                observerPos,
                observer.isBound(),
                debugPreferred,
                chartWindow,
                chartScope,
                scopeItemId == null ? "" : scopeItemId,
                chartSeries,
                uiPrefs.groupFilterKey(),
                uiPrefs.sortMode(),
                uiPrefs.sortDesc(),
                uiPrefs.statusFilter(),
                PlayerUiPrefsSavedData.WATCHLIST_LIMIT,
                uiPrefs.watchlistItemIds(),
                groups,
                entries
        );
    }

    /** 根据网络类型返回绑定的显示名称 */
    private static String bindingDisplayName(ObserverBlockEntity.BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "Storage Network";     // AE2 物品存储网络
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "Power Network";       // Flux 能量网络
        }
        return "Linked Network";          // 未知类型的默认名称
    }

    /** 根据网络类型返回对应的图标精灵路径 */
    private static String bindingIcon(ObserverBlockEntity.BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_storage";
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_consumption";
        }
        return "resourceobserver:terminal/kpi_efficiency";
    }

    /**
     * 将物品 ID（如 "minecraft:iron_ingot"）转换为可读的显示名称（如 "Iron Ingot"）。
     * 提取冒号后的路径部分，按下划线分割后首字母大写拼接。
     */
    private static String toDisplayName(String itemId) {
        int idx = itemId.indexOf(':');
        String path = idx >= 0 ? itemId.substring(idx + 1) : itemId;
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
