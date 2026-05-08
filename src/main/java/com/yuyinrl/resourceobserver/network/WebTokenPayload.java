package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：返回 Web Dashboard 登录链接与 Token。
 *
 * @param url         可直接打开的完整登录链接（含 ?t=token），或仅含 baseUrl 在无法推断公网地址时
 * @param baseUrl     Dashboard 根地址（可能与 url 相同或仅含 host:port）
 * @param token       原始 Token 字符串（前端按 Bearer 注入或粘贴使用）
 * @param regenerated true 表示这是一次作废旧令牌后的重新签发
 * @param reliable    true 表示服务端推断出可靠的访问地址；false 时玩家需手动配置 publicUrlBase 或在内网用
 */
public record WebTokenPayload(
        String url,
        String baseUrl,
        String token,
        boolean regenerated,
        boolean reliable
) implements CustomPacketPayload {
    public static final Type<WebTokenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "web_token"));

    public static final StreamCodec<FriendlyByteBuf, WebTokenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WebTokenPayload decode(FriendlyByteBuf buf) {
            String url = buf.readUtf(512);
            String baseUrl = buf.readUtf(512);
            String token = buf.readUtf(128);
            boolean regen = buf.readBoolean();
            boolean reliable = buf.readBoolean();
            return new WebTokenPayload(url, baseUrl, token, regen, reliable);
        }

        @Override
        public void encode(FriendlyByteBuf buf, WebTokenPayload payload) {
            buf.writeUtf(payload.url() == null ? "" : payload.url(), 512);
            buf.writeUtf(payload.baseUrl() == null ? "" : payload.baseUrl(), 512);
            buf.writeUtf(payload.token() == null ? "" : payload.token(), 128);
            buf.writeBoolean(payload.regenerated());
            buf.writeBoolean(payload.reliable());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
