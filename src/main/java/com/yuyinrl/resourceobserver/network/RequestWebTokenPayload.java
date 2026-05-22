package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求 Web Dashboard 访问 Token。
 *
 * @param regenerate true 表示作废旧 Token 后重新签发；false 表示获取/复用现有 Token
 */
public record RequestWebTokenPayload(boolean regenerate) implements CustomPacketPayload {
    public static final Type<RequestWebTokenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "request_web_token"));

    public static final StreamCodec<FriendlyByteBuf, RequestWebTokenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestWebTokenPayload decode(FriendlyByteBuf buf) {
            return new RequestWebTokenPayload(buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, RequestWebTokenPayload payload) {
            buf.writeBoolean(payload.regenerate());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
