package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
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
        List<BindingEntry> bindings
) implements CustomPacketPayload {

    /**
     * A single binding entry to display in the terminal.
     */
    public record BindingEntry(String networkType, String networkId, String targetBlockId,
                               long currentValue, long capacity, long totalProduced, long totalConsumed,
                               List<ItemDeltaEntry> itemDeltas,
                               String debugInfo) {
    }

    /**
     * Per-item current amount and delta since last sample tick window.
     */
    public record ItemDeltaEntry(String itemId, long amount, long delta) {
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
                    int count = buf.readVarInt();
                    List<BindingEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new BindingEntry(
                                buf.readUtf(256),
                                buf.readUtf(512),
                                buf.readUtf(256),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                readItemDeltas(buf),
                                buf.readUtf(512)
                        ));
                    }
                    return new ObserverDataPayload(pos, bound, debugPreferred, entries);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverDataPayload payload) {
                    buf.writeBlockPos(payload.observerPos);
                    buf.writeBoolean(payload.isBound);
                    buf.writeBoolean(payload.debugPreferred);
                    buf.writeVarInt(payload.bindings.size());
                    for (BindingEntry entry : payload.bindings) {
                        buf.writeUtf(entry.networkType, 256);
                        buf.writeUtf(entry.networkId, 512);
                        buf.writeUtf(entry.targetBlockId, 256);
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
                        buf.writeLong(itemDelta.amount());
                        buf.writeLong(itemDelta.delta());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

