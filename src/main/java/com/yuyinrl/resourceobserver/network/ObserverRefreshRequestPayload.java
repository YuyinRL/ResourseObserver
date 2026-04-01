package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server refresh request while terminal GUI is open.
 */
public record ObserverRefreshRequestPayload(
        BlockPos observerPos,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String scopeItemId
) implements CustomPacketPayload {
    public static final Type<ObserverRefreshRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_refresh_request"));

    public static final StreamCodec<FriendlyByteBuf, ObserverRefreshRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ObserverRefreshRequestPayload decode(FriendlyByteBuf buf) {
                    BlockPos observerPos = buf.readBlockPos();
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String scopeItemId = buf.readBoolean() ? buf.readUtf(256) : "";
                    return new ObserverRefreshRequestPayload(observerPos, chartWindow, chartScope, scopeItemId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverRefreshRequestPayload payload) {
                    buf.writeBlockPos(payload.observerPos());
                    buf.writeVarInt(payload.chartWindow().id());
                    buf.writeVarInt(payload.chartScope().id());
                    boolean hasScope = payload.scopeItemId() != null && !payload.scopeItemId().isBlank();
                    buf.writeBoolean(hasScope);
                    if (hasScope) {
                        buf.writeUtf(payload.scopeItemId(), 256);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
