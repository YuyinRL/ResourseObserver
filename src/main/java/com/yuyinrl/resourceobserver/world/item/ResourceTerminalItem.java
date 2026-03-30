package com.yuyinrl.resourceobserver.world.item;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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


public class ResourceTerminalItem extends Item {
    private static final String TAG_BOUND = "bound_observer";
    private static final String TAG_X = "observer_x";
    private static final String TAG_Y = "observer_y";
    private static final String TAG_Z = "observer_z";
    private static final String TAG_DIMENSION = "observer_dimension";
    private final boolean debugPreferred;

    public ResourceTerminalItem(Properties properties) {
        this(properties, false);
    }

    public ResourceTerminalItem(Properties properties, boolean debugPreferred) {
        super(properties);
        this.debugPreferred = debugPreferred;
    }

    /**
     * Right-click on a block:
     * - ObserverBlock → bind this terminal to that observer (store pos in item data)
     * - Shift + ObserverBlock → unbind terminal
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

        // Only interact with Observer Blocks
        if (!(level.getBlockState(clickedPos).getBlock() instanceof ObserverBlock)
                || !(blockEntity instanceof ObserverBlockEntity observer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();

        // Shift + right-click → unbind terminal
        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_unbound"));
            return InteractionResult.SUCCESS;
        }

        // Right-click → bind terminal to this observer
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
     * Right-click in air: if terminal is bound to an observer, open the terminal GUI.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.copyTag().getBoolean(TAG_BOUND)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_not_bound"));
                return InteractionResultHolder.fail(stack);
            }

            CompoundTag tag = customData.copyTag();
            String dimension = tag.getString(TAG_DIMENSION);

            if (!dimension.equals(level.dimension().location().toString())) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_wrong_dimension"));
                return InteractionResultHolder.fail(stack);
            }

            BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            if (!sendObserverData(serverPlayer, level, pos, debugPreferred)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_missing"));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean sendObserverData(ServerPlayer player, Level level, BlockPos observerPos, boolean debugPreferred) {
        BlockEntity blockEntity = level.getBlockEntity(observerPos);
        if (!(blockEntity instanceof ObserverBlockEntity observer)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, buildPayload(observerPos, observer, debugPreferred));
        return true;
    }

    private static ObserverDataPayload buildPayload(BlockPos observerPos, ObserverBlockEntity observer, boolean debugPreferred) {
        List<ObserverDataPayload.BindingEntry> entries = new ArrayList<>();
        for (ObserverBlockEntity.BoundEntry binding : observer.getBindings()) {
            ObserverBlockEntity.BindingStats stats = observer.getStatsFor(binding.networkId());
            List<ObserverDataPayload.ItemDeltaEntry> itemDeltas = new ArrayList<>();
            if ("AE2_ITEMS".equals(binding.networkType())) {
                Map<String, Long> amounts = observer.getAe2ItemAmountsFor(binding.networkId());
                Map<String, Long> deltas = observer.getAe2ItemDeltasFor(binding.networkId());
                for (Map.Entry<String, Long> amountEntry : amounts.entrySet()) {
                    String itemId = amountEntry.getKey();
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            itemId,
                            amountEntry.getValue(),
                            deltas.getOrDefault(itemId, 0L)
                    ));
                }
                for (Map.Entry<String, Long> deltaEntry : deltas.entrySet()) {
                    String itemId = deltaEntry.getKey();
                    if (amounts.containsKey(itemId)) {
                        continue;
                    }
                    itemDeltas.add(new ObserverDataPayload.ItemDeltaEntry(
                            itemId,
                            0L,
                            deltaEntry.getValue()
                    ));
                }
                itemDeltas.sort(Comparator.comparing(ObserverDataPayload.ItemDeltaEntry::itemId));
            }
            entries.add(new ObserverDataPayload.BindingEntry(
                    binding.networkType(),
                    binding.networkId(),
                    binding.targetBlockId(),
                    stats.currentValue(),
                    stats.capacity(),
                    stats.totalProduced(),
                    stats.totalConsumed(),
                    itemDeltas,
                    observer.getDebugInfoFor(binding.networkId())
            ));
        }
        return new ObserverDataPayload(observerPos, observer.isBound(), debugPreferred, entries);
    }
}
