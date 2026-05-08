package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.JsonWriter;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.auth.PlayerWebTokenSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code POST /api/auth/exchange} —— 用一次性 link token 换取长期 session token。
 * <p>
 * 请求体可为 JSON {@code {"token":"rl_..."}}、表单 {@code token=rl_...} 或查询参数 {@code ?t=rl_...}。
 * 成功时：
 * <ul>
 *   <li>返回 JSON {@code {session_token, uuid, name, admin}}；</li>
 *   <li>同时设置 cookie {@code Set-Cookie: ro_session=<token>; Max-Age=...; Path=/; SameSite=Lax}。</li>
 * </ul>
 * 失败（link token 不存在 / 过期 / 已被消费）→ 401。
 * <p>
 * 本端点不经过 {@link com.yuyinrl.resourceobserver.web.auth.AuthFilter}，
 * 因为它本身就是"获取鉴权凭证"的入口。
 */
public final class AuthExchangeHandler implements HttpHandler {

    /** Session cookie Max-Age（秒）。30 天。 */
    private static final long SESSION_COOKIE_MAX_AGE_SEC = 30L * 24 * 3600;

    private final WebServerService server;

    public AuthExchangeHandler(WebServerService server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            server.writeJson(exchange, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            server.writeError(exchange, 405, "method not allowed");
            return;
        }

        String linkToken = extractLinkToken(exchange);
        if (linkToken == null || linkToken.isBlank()) {
            unauthorized(exchange, "missing_token", "请求缺少 link token");
            return;
        }

        MinecraftServer mc = server.minecraftServer();
        ExchangeResult result;
        try {
            result = mc.submit(() -> {
                ServerLevel overworld = mc.overworld();
                if (overworld == null) return null;
                PlayerWebTokenSavedData store = PlayerWebTokenSavedData.get(overworld);
                Optional<UUID> uuidOpt = store.consumeLinkToken(linkToken);
                if (uuidOpt.isEmpty()) return null;
                UUID uuid = uuidOpt.get();
                String session = store.issueSessionToken(uuid);
                String name = lookupPlayerName(mc, uuid);
                boolean admin = isOp(mc, uuid);
                return new ExchangeResult(session, uuid, name, admin);
            }).get();
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[Web][Auth] exchange 处理失败：{}", e.toString());
            unauthorized(exchange, "internal", "服务端处理失败");
            return;
        }

        if (result == null) {
            unauthorized(exchange, "invalid_link_token",
                    "Link token 无效、已过期或已被使用，请回游戏内重新获取链接");
            return;
        }

        // 写 cookie（持久化登录态，关闭浏览器重开仍有效）
        String cookie = "ro_session=" + result.sessionToken
                + "; Max-Age=" + SESSION_COOKIE_MAX_AGE_SEC
                + "; Path=/"
                + "; SameSite=Lax";
        exchange.getResponseHeaders().add("Set-Cookie", cookie);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_token", result.sessionToken);
        body.put("uuid", result.uuid.toString());
        body.put("name", result.name);
        body.put("admin", result.admin);
        server.writeJson(exchange, 200, JsonWriter.write(body));
    }

    /** 从 body / query / form 中提取 link token。 */
    private static String extractLinkToken(HttpExchange ex) throws IOException {
        // 1) Query string
        URI uri = ex.getRequestURI();
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String k = pair.substring(0, eq);
                if ("t".equals(k) || "token".equals(k)) {
                    String raw = pair.substring(eq + 1);
                    try {
                        String decoded = java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
                        if (!decoded.isBlank()) return decoded;
                    } catch (IllegalArgumentException ignored) {
                        if (!raw.isBlank()) return raw;
                    }
                }
            }
        }
        // 2) Body
        try (InputStream is = ex.getRequestBody()) {
            byte[] data = is.readAllBytes();
            if (data.length == 0) return null;
            String body = new String(data, StandardCharsets.UTF_8).trim();
            if (body.isEmpty()) return null;
            // JSON
            if (body.startsWith("{")) {
                int idx = body.indexOf("\"token\"");
                if (idx < 0) idx = body.indexOf("\"t\"");
                if (idx < 0) return null;
                int colon = body.indexOf(':', idx);
                if (colon < 0) return null;
                int q1 = body.indexOf('"', colon + 1);
                if (q1 < 0) return null;
                int q2 = body.indexOf('"', q1 + 1);
                if (q2 < 0) return null;
                return body.substring(q1 + 1, q2);
            }
            // form url-encoded
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String k = pair.substring(0, eq);
                if ("t".equals(k) || "token".equals(k)) {
                    String raw = pair.substring(eq + 1);
                    try {
                        String decoded = java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
                        if (!decoded.isBlank()) return decoded;
                    } catch (IllegalArgumentException ignored) {
                        if (!raw.isBlank()) return raw;
                    }
                }
            }
        }
        return null;
    }

    private static String lookupPlayerName(MinecraftServer mc, UUID uuid) {
        ServerPlayer online = mc.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = mc.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) {
                String n = profile.get().getName();
                if (n != null && !n.isBlank()) return n;
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private static boolean isOp(MinecraftServer mc, UUID uuid) {
        ServerPlayer online = mc.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return mc.getPlayerList().isOp(online.getGameProfile());
        }
        GameProfileCache cache = mc.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) {
                return mc.getPlayerList().isOp(profile.get());
            }
        }
        return false;
    }

    private void unauthorized(HttpExchange ex, String code, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("status", 401);
        server.writeJson(ex, 401, JsonWriter.write(body));
    }

    private record ExchangeResult(String sessionToken, UUID uuid, String name, boolean admin) {
    }
}
