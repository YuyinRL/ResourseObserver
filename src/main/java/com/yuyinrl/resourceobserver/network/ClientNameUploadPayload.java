package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端：上传一批 (itemId, displayName) 给服务端缓存，用于 Web UI 在
 * 专用服务端没有 zh_cn 资源时的本地化展示。
 * <p>
 * 客户端在登录服务器后扫描 BuiltInRegistries.ITEM 全表，按客户端当前语言解析
 * displayName，分批（每批最多 {@link #MAX_BATCH}）通过本 payload 发送。
 *
 * @param language     客户端当前语言代码（如 "zh_cn"），便于服务端按语言归类缓存
 * @param itemIds      物品 ID 列表
 * @param displayNames 与 itemIds 一一对应的本地化显示名
 */
public record ClientNameUploadPayload(String language, List<String> itemIds, List<String> displayNames)
        implements CustomPacketPayload {

    /** 单个 payload 最大项数。控制单包大小在 ~20-30 KB。 */
    public static final int MAX_BATCH = 256;
    private static final int MAX_ID_LEN = 256;
    private static final int MAX_NAME_LEN = 256;

    public static final Type<ClientNameUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "client_name_upload"));

    public static final StreamCodec<FriendlyByteBuf, ClientNameUploadPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientNameUploadPayload decode(FriendlyByteBuf buf) {
            String lang = buf.readUtf(32);
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_BATCH) n = Math.max(0, Math.min(n, MAX_BATCH));
            List<String> ids = new ArrayList<>(n);
            List<String> names = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ids.add(buf.readUtf(MAX_ID_LEN));
                names.add(buf.readUtf(MAX_NAME_LEN));
            }
            return new ClientNameUploadPayload(lang, ids, names);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ClientNameUploadPayload p) {
            buf.writeUtf(p.language() == null ? "" : p.language(), 32);
            int n = Math.min(p.itemIds().size(), p.displayNames().size());
            n = Math.min(n, MAX_BATCH);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeUtf(PayloadCodecUtils.clampUtf(p.itemIds().get(i), MAX_ID_LEN), MAX_ID_LEN);
                buf.writeUtf(PayloadCodecUtils.clampUtf(p.displayNames().get(i), MAX_NAME_LEN), MAX_NAME_LEN);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
