package com.yuyinrl.resourceobserver.world.block;

import com.mojang.serialization.MapCodec;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.item.BindingToolItem;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 观察者方块 —— 模组的核心功能方块。
 * <p>
 * 该方块继承 BaseEntityBlock，拥有关联的方块实体（ObserverBlockEntity），
 * 负责存储网络绑定数据和执行周期性采样。
 * <p>
 * 交互行为：
 * - 手持绑定工具或资源终端右键：跳过默认方块交互，交由物品逻辑处理
 * - 空手右键：向玩家发送当前绑定状态消息
 * - 服务端每 tick 触发方块实体的采样逻辑
 */
public class ObserverBlock extends BaseEntityBlock {
    /** 方块序列化编解码器，用于方块状态的序列化与反序列化 */
    public static final MapCodec<ObserverBlock> CODEC = simpleCodec(ObserverBlock::new);

    public ObserverBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * 手持物品右键方块时的交互逻辑。
     * 当玩家持有绑定工具或资源终端时，跳过默认方块交互，
     * 以便将交互控制权交给物品自身的 useOn() 方法。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof BindingToolItem || stack.getItem() instanceof ResourceTerminalItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 空手右键方块时的交互逻辑。
     * 服务端向玩家发送观察者方块的当前绑定状态信息（未绑定/已绑定的网络列表）。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ObserverBlockEntity observer) {
                if (player.isShiftKeyDown()) {
                    boolean debugEnabled = PlayerUiPrefsSavedData.get(serverPlayer.serverLevel())
                            .isObserverDebugEnabled(serverPlayer.getUUID());
                    if (!debugEnabled) {
                        return InteractionResult.sidedSuccess(level.isClientSide);
                    }
                    for (var line : observer.createDebugStatusMessages()) {
                        serverPlayer.sendSystemMessage(line);
                    }
                } else {
                    serverPlayer.sendSystemMessage(observer.createStatusMessage());
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 创建并返回与此方块关联的方块实体实例 */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.OBSERVER_BLOCK_ENTITY.get().create(pos, state);
    }

    /**
     * 返回服务端方块实体 tick 处理器。
     * 仅在服务端执行，用于驱动观察者的周期性数据采样。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.OBSERVER_BLOCK_ENTITY.get(), ObserverBlockEntity::tick);
        }
        return null;
    }

    /** 使用标准模型渲染方式（而非隐形/自定义渲染） */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
