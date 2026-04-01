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
        if (!(level.getBlockState(clickedPos).getBlock() instanceof ObserverBlock)
                || !(blockEntity instanceof ObserverBlockEntity observer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            player.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_unbound"));
            return InteractionResult.SUCCESS;
        }

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
            if (!sendObserverData(serverPlayer, level, pos, debugPreferred, ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, "")) {
                serverPlayer.sendSystemMessage(Component.translatable("message.resourceobserver.terminal_observer_missing"));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean sendObserverData(ServerPlayer player, Level level, BlockPos observerPos, boolean debugPreferred) {
        return sendObserverData(player, level, observerPos, debugPreferred, ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, "");
    }

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
        PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot uiPrefs = PlayerUiPrefsSavedData.get(level).getSnapshot(player.getUUID());

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
                            toDisplayName(itemId),
                            uiPrefs.groupKeyForItem(itemId),
                            "resourceobserver:terminal/table_item",
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
                            toDisplayName(itemId),
                            uiPrefs.groupKeyForItem(itemId),
                            "resourceobserver:terminal/table_item",
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

        List<ObserverDataPayload.ChartPoint> chartSeries = HistoryRecorder.querySeries(
                level,
                observerPos,
                observer.getBindings(),
                chartWindow,
                chartScope,
                scopeItemId
        );

        List<ObserverDataPayload.GroupEntry> groups = new ArrayList<>();
        for (PlayerUiPrefsSavedData.GroupDefinition group : uiPrefs.groups()) {
            groups.add(new ObserverDataPayload.GroupEntry(group.key(), group.displayName(), group.systemGroup()));
        }

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

    private static String bindingDisplayName(ObserverBlockEntity.BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "Storage Network";
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "Power Network";
        }
        return "Linked Network";
    }

    private static String bindingIcon(ObserverBlockEntity.BoundEntry binding) {
        if ("AE2_ITEMS".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_storage";
        }
        if ("FLUX_ENERGY".equals(binding.networkType())) {
            return "resourceobserver:terminal/kpi_consumption";
        }
        return "resourceobserver:terminal/kpi_efficiency";
    }

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
