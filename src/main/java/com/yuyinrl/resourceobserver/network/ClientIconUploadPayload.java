package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：上传单个物品图标 PNG 字节，供专用服务端的 Web UI 使用。
 *
 * @param itemId   物品 / 图标 ID（含 {@code fluid:} 前缀的流体亦可）
 * @param pngBytes PNG 文件字节（建议 ≤ 16KB，IconRenderer 输出 32x32 一般 1-3KB）
 */
public record ClientIconUploadPayload(String itemId, byte[] pngBytes)
        implements CustomPacketPayload {

    /** 单张图标最大字节数（防止恶意 / 错误客户端发送超大文件）。 */
    public static final int MAX_BYTES = 64 * 1024;

    public static final Type<ClientIconUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "client_icon_upload"));

    public static final StreamCodec<FriendlyByteBuf, ClientIconUploadPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientIconUploadPayload decode(FriendlyByteBuf buf) {
            String id = buf.readUtf(256);
            int len = buf.readVarInt();
            if (len < 0) len = 0;
            if (len > MAX_BYTES) len = MAX_BYTES;
            byte[] data = new byte[len];
            if (len > 0) buf.readBytes(data);
            return new ClientIconUploadPayload(id, data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ClientIconUploadPayload p) {
            buf.writeUtf(PayloadCodecUtils.clampUtf(p.itemId(), 256), 256);
            byte[] data = p.pngBytes() == null ? new byte[0] : p.pngBytes();
            int len = Math.min(data.length, MAX_BYTES);
            buf.writeVarInt(len);
            if (len > 0) buf.writeBytes(data, 0, len);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
