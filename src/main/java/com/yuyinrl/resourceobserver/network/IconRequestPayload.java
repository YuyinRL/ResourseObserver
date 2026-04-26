package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：请求客户端渲染并上传一批图标 PNG。
 * <p>
 * 触发场景：Web UI 请求 {@code /api/icon/...} 但服务端磁盘缓存未命中，且当前为
 * 专用服务端无法本地渲染 → 通过本 payload 让在线客户端代为渲染并通过
 * {@link ClientIconUploadPayload} 回传。
 */
public record IconRequestPayload(List<String> itemIds) implements CustomPacketPayload {

    public static final int MAX_BATCH = 64;

    public static final Type<IconRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "icon_request"));

    public static final StreamCodec<FriendlyByteBuf, IconRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IconRequestPayload decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_BATCH) n = Math.max(0, Math.min(n, MAX_BATCH));
            List<String> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readUtf(256));
            return new IconRequestPayload(ids);
        }

        @Override
        public void encode(FriendlyByteBuf buf, IconRequestPayload p) {
            int n = Math.min(p.itemIds().size(), MAX_BATCH);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeUtf(PayloadCodecUtils.clampUtf(p.itemIds().get(i), 256), 256);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
