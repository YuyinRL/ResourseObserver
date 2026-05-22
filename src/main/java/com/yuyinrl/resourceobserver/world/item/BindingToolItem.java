package com.yuyinrl.resourceobserver.world.item;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.block.entity.NetworkRef;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 绑定工具物品 —— 实现两步式网络绑定流程的核心交互逻辑。
 * <p>
 * 绑定流程（状态机）：
 * 1. IDLE（空闲）→ 右键点击观察者方块 → OBSERVER_SELECTED（已选择观察者）
 * 2. OBSERVER_SELECTED → 右键点击受支持的网络目标方块 → 绑定成功 → 回到 IDLE
 * <p>
 * 其他操作：
 * - Shift + 右键观察者方块：解除该观察者的所有绑定
 * <p>
 * 选择状态存储在玩家的 PersistentData（NBT）中，跨会话保留。
 */
public class BindingToolItem extends Item {
    /** 玩家 NBT 持久数据中存储绑定工具状态的键名 */
    private static final String PLAYER_BIND_KEY = ResourceObserverMod.MODID + "_binding";
    private static final String TAG_SELECTED = "has_selected_observer";
    private static final String TAG_X = "observer_x";
    private static final String TAG_Y = "observer_y";
    private static final String TAG_Z = "observer_z";
    private static final String TAG_DIMENSION = "observer_dimension";

    public BindingToolItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键点击方块时的核心交互逻辑。
     * 依次处理以下情况：
     * 1. Shift + 右键观察者 → 解绑
     * 2. 右键观察者 → 选择该观察者
     * 3. 右键其他方块 → 尝试将已选择的观察者绑定到该方块
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS; // 客户端直接返回成功，实际逻辑在服务端执行
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockEntity clickedBlockEntity = level.getBlockEntity(clickedPos);

        // ===== 操作 1：Shift + 右键观察者方块 → 解除绑定 =====
        if (context.getPlayer().isShiftKeyDown() && clickedBlockEntity instanceof ObserverBlockEntity observer) {
            observer.clearAllBindings();
            clearSelectedObserver(player);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.unbound_observer"));
            return InteractionResult.SUCCESS;
        }

        // ===== 操作 2：右键观察者方块 → 选择（记住）该观察者 =====
        if (clickedState.getBlock() instanceof ObserverBlock) {
            selectObserver(player, clickedPos, level.dimension().location().toString());
            player.sendSystemMessage(Component.translatable("message.resourceobserver.selected_observer", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
            return InteractionResult.SUCCESS;
        }

        // ===== 操作 3：右键其他方块 → 尝试绑定 =====
        // 检查是否已选择观察者
        SelectedObserver selected = getSelectedObserver(player);
        if (selected == null) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.no_observer_selected"));
            return InteractionResult.FAIL;
        }

        // 检查维度是否一致
        if (!selected.dimension().equals(level.dimension().location().toString())) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.wrong_dimension"));
            return InteractionResult.FAIL;
        }

        // 验证之前选择的观察者方块是否仍然存在
        BlockEntity selectedEntity = level.getBlockEntity(selected.pos());
        if (!(selectedEntity instanceof ObserverBlockEntity observer)) {
            clearSelectedObserver(player);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.observer_not_found"));
            return InteractionResult.FAIL;
        }

        // 根据目标方块的命名空间判断网络类型（AE2 / Flux Networks）
        ResourceLocation targetBlockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
        String networkType = resolveNetworkType(targetBlockId);
        if (networkType == null) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.unsupported_target", targetBlockId.toString()));
            return InteractionResult.FAIL;
        }

        // 生成网络唯一 ID 并尝试添加绑定
        String networkId = NetworkRef.format(targetBlockId.toString(), clickedPos);
        boolean added = observer.addBinding(networkType, networkId, targetBlockId);
        clearSelectedObserver(player); // 无论成功与否，清除选择状态

        if (!added) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.bind_duplicate"));
            return InteractionResult.FAIL;
        }

        player.sendSystemMessage(Component.translatable("message.resourceobserver.bind_success", networkType, targetBlockId.toString()));
        return InteractionResult.SUCCESS;
    }

    /**
     * 根据目标方块的注册 ID 判断网络类型。
     * - ae2 命名空间 → AE2_ITEMS（AE2 物品存储网络）
     * - fluxnetworks 命名空间且路径包含 "controller" → FLUX_ENERGY（Flux 能量网络）
     * - 其他 → 不支持（返回 null）
     */
    private static String resolveNetworkType(ResourceLocation targetBlockId) {
        String namespace = targetBlockId.getNamespace();
        String path = targetBlockId.getPath();

        if ("ae2".equals(namespace)) {
            return "AE2_ITEMS";
        }

        if ("fluxnetworks".equals(namespace) && path.contains("controller")) {
            return "FLUX_ENERGY";
        }

        return null;
    }

    /** 将选定的观察者坐标和维度存储到玩家的 NBT 持久数据中 */
    private static void selectObserver(ServerPlayer player, BlockPos pos, String dimensionId) {
        CompoundTag tag = getOrCreateBindTag(player);
        tag.putBoolean(TAG_SELECTED, true);
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        tag.putString(TAG_DIMENSION, dimensionId);
    }

    /** 清除玩家的观察者选择状态 */
    private static void clearSelectedObserver(ServerPlayer player) {
        CompoundTag tag = getOrCreateBindTag(player);
        tag.putBoolean(TAG_SELECTED, false);
        tag.remove(TAG_X);
        tag.remove(TAG_Y);
        tag.remove(TAG_Z);
        tag.remove(TAG_DIMENSION);
    }

    /** 从玩家 NBT 中读取已选择的观察者信息，未选择则返回 null */
    private static SelectedObserver getSelectedObserver(ServerPlayer player) {
        CompoundTag tag = getOrCreateBindTag(player);
        if (!tag.getBoolean(TAG_SELECTED)) {
            return null;
        }

        BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
        String dimension = tag.getString(TAG_DIMENSION);
        if (dimension.isEmpty()) {
            return null;
        }
        return new SelectedObserver(pos, dimension);
    }

    /** 获取或创建玩家 NBT 中用于存储绑定工具状态的子标签 */
    private static CompoundTag getOrCreateBindTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(PLAYER_BIND_KEY)) {
            persistent.put(PLAYER_BIND_KEY, new CompoundTag());
        }
        return persistent.getCompound(PLAYER_BIND_KEY);
    }

    /** 已选择的观察者信息记录 */
    private record SelectedObserver(BlockPos pos, String dimension) {
    }
}
