package com.yuyinrl.resourceobserver.web.auth;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.network.WebTokenPayload;
import com.yuyinrl.resourceobserver.world.auth.PlayerWebTokenSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Web Token 服务端工具：签发一次性 link token + 推送 GUI Payload / 首次聊天链接。
 * <p>
 * 所有方法应在主线程调用（与 {@link PlayerWebTokenSavedData} 同步）。
 * <p>
 * 注意：从 v0.2 起 URL 中携带的 token 是<b>一次性 link token</b>，
 * 浏览器需调用 {@code POST /api/auth/exchange} 用其换取长期 session token；
 * 因此每次 {@link #issueAndSend(ServerPlayer, boolean)} 都会签发一个新 link token，
 * "regenerate" 选项同时会把该玩家所有 session token 一并吊销（其它已登录浏览器立即下线）。
 */
public final class WebTokenService {

    /** 用于跟踪"该玩家是否已经在本进程内推送过首次聊天链接"。 */
    private static final java.util.Set<java.util.UUID> firstUsePushed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private WebTokenService() {
    }

    /**
     * 处理客户端的 Token 请求：
     * <ul>
     *   <li>regenerate=false：仅签发新 link token（不影响已有 session）。</li>
     *   <li>regenerate=true：先吊销玩家所有 session 再签发新 link token。</li>
     * </ul>
     */
    public static void issueAndSend(ServerPlayer player, boolean regenerate) {
        PlayerWebTokenSavedData store = PlayerWebTokenSavedData.get(player.serverLevel());
        if (regenerate) {
            store.revokeAllSessions(player.getUUID());
        }
        String linkToken = store.issueLinkToken(player.getUUID());
        sendTokenPayload(player, linkToken, regenerate);
    }

    /**
     * 玩家首次右键终端时调用：若玩家从未在本进程推送过首次聊天链接，则推送一条带
     * ClickEvent.OPEN_URL 的聊天消息。后续右键不再重复（每个进程一次）。
     */
    public static void pushFirstUseChatIfNeeded(ServerPlayer player) {
        if (!firstUsePushed.add(player.getUUID())) return;
        PlayerWebTokenSavedData store = PlayerWebTokenSavedData.get(player.serverLevel());
        String linkToken = store.issueLinkToken(player.getUUID());
        try {
            String url = WebUrlBuilder.loginUrl(linkToken);
            MutableComponent linkText = Component.translatable("message.resourceobserver.web_token.first_use_link")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("message.resourceobserver.web_token.first_use_hover"))));
            MutableComponent msg = Component.translatable("message.resourceobserver.web_token.first_use_prefix")
                    .append(Component.literal(" "))
                    .append(linkText);
            player.sendSystemMessage(msg);
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Web] 首次推送聊天链接失败: {}", t.toString());
        }
    }

    private static void sendTokenPayload(ServerPlayer player, String linkToken, boolean regenerated) {
        String base = WebUrlBuilder.baseUrl();
        String url = WebUrlBuilder.loginUrl(linkToken);
        boolean reliable = WebUrlBuilder.hasReliablePublicUrl();
        PacketDistributor.sendToPlayer(player, new WebTokenPayload(url, base, linkToken, regenerated, reliable));
    }
}
