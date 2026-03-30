package com.yuyinrl.resourceobserver.world.item;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
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

public class BindingToolItem extends Item {
    private static final String PLAYER_BIND_KEY = ResourceObserverMod.MODID + "_binding";
    private static final String TAG_SELECTED = "has_selected_observer";
    private static final String TAG_X = "observer_x";
    private static final String TAG_Y = "observer_y";
    private static final String TAG_Z = "observer_z";
    private static final String TAG_DIMENSION = "observer_dimension";

    public BindingToolItem(Properties properties) {
        super(properties);
    }

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
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockEntity clickedBlockEntity = level.getBlockEntity(clickedPos);

        if (context.getPlayer().isShiftKeyDown() && clickedBlockEntity instanceof ObserverBlockEntity observer) {
            observer.clearAllBindings();
            clearSelectedObserver(player);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.unbound_observer"));
            return InteractionResult.SUCCESS;
        }

        if (clickedState.getBlock() instanceof ObserverBlock) {
            selectObserver(player, clickedPos, level.dimension().location().toString());
            player.sendSystemMessage(Component.translatable("message.resourceobserver.selected_observer", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
            return InteractionResult.SUCCESS;
        }

        SelectedObserver selected = getSelectedObserver(player);
        if (selected == null) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.no_observer_selected"));
            return InteractionResult.FAIL;
        }

        if (!selected.dimension().equals(level.dimension().location().toString())) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.wrong_dimension"));
            return InteractionResult.FAIL;
        }

        BlockEntity selectedEntity = level.getBlockEntity(selected.pos());
        if (!(selectedEntity instanceof ObserverBlockEntity observer)) {
            clearSelectedObserver(player);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.observer_not_found"));
            return InteractionResult.FAIL;
        }

        ResourceLocation targetBlockId = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
        String networkType = resolveNetworkType(targetBlockId);
        if (networkType == null) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.unsupported_target", targetBlockId.toString()));
            return InteractionResult.FAIL;
        }

        String networkId = targetBlockId + "@" + clickedPos.asLong();
        boolean added = observer.addBinding(networkType, networkId, targetBlockId);
        clearSelectedObserver(player);

        if (!added) {
            player.sendSystemMessage(Component.translatable("message.resourceobserver.bind_duplicate"));
            return InteractionResult.FAIL;
        }

        player.sendSystemMessage(Component.translatable("message.resourceobserver.bind_success", networkType, targetBlockId.toString()));
        return InteractionResult.SUCCESS;
    }

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

    private static void selectObserver(ServerPlayer player, BlockPos pos, String dimensionId) {
        CompoundTag tag = getOrCreateBindTag(player);
        tag.putBoolean(TAG_SELECTED, true);
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        tag.putString(TAG_DIMENSION, dimensionId);
    }

    private static void clearSelectedObserver(ServerPlayer player) {
        CompoundTag tag = getOrCreateBindTag(player);
        tag.putBoolean(TAG_SELECTED, false);
        tag.remove(TAG_X);
        tag.remove(TAG_Y);
        tag.remove(TAG_Z);
        tag.remove(TAG_DIMENSION);
    }

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

    private static CompoundTag getOrCreateBindTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(PLAYER_BIND_KEY)) {
            persistent.put(PLAYER_BIND_KEY, new CompoundTag());
        }
        return persistent.getCompound(PLAYER_BIND_KEY);
    }

    private record SelectedObserver(BlockPos pos, String dimension) {
    }
}
