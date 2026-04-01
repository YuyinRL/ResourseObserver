package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload that carries observer data for the Resource Terminal GUI.
 */
public record ObserverDataPayload(
        BlockPos observerPos,
        boolean isBound,
        boolean debugPreferred,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String chartScopeItemId,
        List<ChartPoint> chartSeries,
        String tableGroupFilterKey,
        TableSortMode tableSortMode,
        boolean tableSortDesc,
        TableStatusFilter tableStatusFilter,
        int watchlistLimit,
        List<String> watchlistItemIds,
        List<GroupEntry> groups,
        List<BindingEntry> bindings
) implements CustomPacketPayload {

    public record GroupEntry(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    /**
     * A single binding entry to display in the terminal.
     */
    public record BindingEntry(
            String networkType,
            String networkId,
            String targetBlockId,
            String displayName,
            String iconSprite,
            long currentValue,
            long capacity,
            long totalProduced,
            long totalConsumed,
            List<ItemDeltaEntry> itemDeltas,
            String debugInfo
    ) {
    }

    /**
     * Per-item current amount and delta since last sample tick window.
     */
    public record ItemDeltaEntry(
            String itemId,
            String displayName,
            String groupKey,
            String iconSprite,
            long amount,
            long delta
    ) {
    }

    public record ChartPoint(
            int slotIndex,
            double production,
            double consumption,
            double net,
            double stock,
            boolean hasFlow,
            boolean hasStock
    ) {
    }

    public static final Type<ObserverDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_data"));

    public static final StreamCodec<FriendlyByteBuf, ObserverDataPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ObserverDataPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    boolean bound = buf.readBoolean();
                    boolean debugPreferred = buf.readBoolean();
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String chartScopeItemId = buf.readBoolean() ? buf.readUtf(256) : "";
                    List<ChartPoint> chartSeries = readChartSeries(buf);
                    String tableGroupFilterKey = buf.readUtf(64);
                    TableSortMode sortMode = TableSortMode.fromId(buf.readVarInt());
                    boolean sortDesc = buf.readBoolean();
                    TableStatusFilter statusFilter = TableStatusFilter.fromId(buf.readVarInt());
                    int watchlistLimit = buf.readVarInt();
                    List<String> watchlistItemIds = readWatchlist(buf);
                    List<GroupEntry> groups = readGroups(buf);
                    int count = buf.readVarInt();
                    List<BindingEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new BindingEntry(
                                buf.readUtf(256),
                                buf.readUtf(512),
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                readItemDeltas(buf),
                                buf.readUtf(512)
                        ));
                    }
                    return new ObserverDataPayload(
                            pos,
                            bound,
                            debugPreferred,
                            chartWindow,
                            chartScope,
                            chartScopeItemId,
                            chartSeries,
                            tableGroupFilterKey,
                            sortMode,
                            sortDesc,
                            statusFilter,
                            watchlistLimit,
                            watchlistItemIds,
                            groups,
                            entries
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverDataPayload payload) {
                    buf.writeBlockPos(payload.observerPos);
                    buf.writeBoolean(payload.isBound);
                    buf.writeBoolean(payload.debugPreferred);
                    buf.writeVarInt(payload.chartWindow.id());
                    buf.writeVarInt(payload.chartScope.id());
                    boolean hasScopeItem = payload.chartScopeItemId != null && !payload.chartScopeItemId.isBlank();
                    buf.writeBoolean(hasScopeItem);
                    if (hasScopeItem) {
                        buf.writeUtf(payload.chartScopeItemId, 256);
                    }
                    writeChartSeries(buf, payload.chartSeries);
                    buf.writeUtf(payload.tableGroupFilterKey == null ? "" : payload.tableGroupFilterKey, 64);
                    buf.writeVarInt(payload.tableSortMode.id());
                    buf.writeBoolean(payload.tableSortDesc);
                    buf.writeVarInt(payload.tableStatusFilter.id());
                    buf.writeVarInt(payload.watchlistLimit);
                    writeWatchlist(buf, payload.watchlistItemIds);
                    writeGroups(buf, payload.groups);
                    buf.writeVarInt(payload.bindings.size());
                    for (BindingEntry entry : payload.bindings) {
                        buf.writeUtf(entry.networkType, 256);
                        buf.writeUtf(entry.networkId, 512);
                        buf.writeUtf(entry.targetBlockId, 256);
                        buf.writeUtf(entry.displayName(), 256);
                        buf.writeUtf(entry.iconSprite(), 256);
                        buf.writeLong(entry.currentValue);
                        buf.writeLong(entry.capacity);
                        buf.writeLong(entry.totalProduced);
                        buf.writeLong(entry.totalConsumed);
                        writeItemDeltas(buf, entry.itemDeltas());
                        buf.writeUtf(entry.debugInfo(), 512);
                    }
                }

                private static List<ItemDeltaEntry> readItemDeltas(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<ItemDeltaEntry> result = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        result.add(new ItemDeltaEntry(
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readUtf(128),
                                buf.readUtf(256),
                                buf.readLong(),
                                buf.readLong()
                        ));
                    }
                    return result;
                }

                private static void writeItemDeltas(FriendlyByteBuf buf, List<ItemDeltaEntry> itemDeltas) {
                    buf.writeVarInt(itemDeltas.size());
                    for (ItemDeltaEntry itemDelta : itemDeltas) {
                        buf.writeUtf(itemDelta.itemId(), 256);
                        buf.writeUtf(itemDelta.displayName(), 256);
                        buf.writeUtf(itemDelta.groupKey(), 128);
                        buf.writeUtf(itemDelta.iconSprite(), 256);
                        buf.writeLong(itemDelta.amount());
                        buf.writeLong(itemDelta.delta());
                    }
                }

                private static List<ChartPoint> readChartSeries(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<ChartPoint> points = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        points.add(new ChartPoint(
                                buf.readVarInt(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        ));
                    }
                    return points;
                }

                private static void writeChartSeries(FriendlyByteBuf buf, List<ChartPoint> chartSeries) {
                    buf.writeVarInt(chartSeries.size());
                    for (ChartPoint point : chartSeries) {
                        buf.writeVarInt(point.slotIndex());
                        buf.writeDouble(point.production());
                        buf.writeDouble(point.consumption());
                        buf.writeDouble(point.net());
                        buf.writeDouble(point.stock());
                        buf.writeBoolean(point.hasFlow());
                        buf.writeBoolean(point.hasStock());
                    }
                }

                private static List<String> readWatchlist(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<String> result = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        result.add(buf.readUtf(512));
                    }
                    return result;
                }

                private static void writeWatchlist(FriendlyByteBuf buf, List<String> watchlistItemIds) {
                    buf.writeVarInt(watchlistItemIds.size());
                    for (String itemId : watchlistItemIds) {
                        buf.writeUtf(itemId, 512);
                    }
                }

                private static List<GroupEntry> readGroups(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<GroupEntry> groups = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        groups.add(new GroupEntry(
                                buf.readUtf(128),
                                buf.readUtf(128),
                                buf.readBoolean()
                        ));
                    }
                    return groups;
                }

                private static void writeGroups(FriendlyByteBuf buf, List<GroupEntry> groups) {
                    buf.writeVarInt(groups.size());
                    for (GroupEntry group : groups) {
                        buf.writeUtf(group.key(), 128);
                        buf.writeUtf(group.displayName(), 128);
                        buf.writeBoolean(group.systemGroup());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
