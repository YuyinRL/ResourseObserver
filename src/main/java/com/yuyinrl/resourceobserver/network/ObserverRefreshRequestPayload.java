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
public record ObserverRefreshRequestPayload(BlockPos observerPos) implements CustomPacketPayload {
    public static final Type<ObserverRefreshRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_refresh_request"));

    public static final StreamCodec<FriendlyByteBuf, ObserverRefreshRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ObserverRefreshRequestPayload decode(FriendlyByteBuf buf) {
                    return new ObserverRefreshRequestPayload(buf.readBlockPos());
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverRefreshRequestPayload payload) {
                    buf.writeBlockPos(payload.observerPos());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

