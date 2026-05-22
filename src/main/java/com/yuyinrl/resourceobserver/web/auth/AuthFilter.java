package com.yuyinrl.resourceobserver.web.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.JsonWriter;
import com.yuyinrl.resourceobserver.web.WebServerConfig;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.auth.PlayerWebTokenSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /api/* 路径鉴权过滤器 —— 包装一个下游 {@link HttpHandler}，
 * 在调用前根据 {@link WebServerConfig#AUTH_MODE} 解析并校验 Bearer Token，
 * 通过校验后注入 {@link AuthContext} 到 exchange 属性。
 * <p>
 * Token 解析顺序：
 * <ol>
 *   <li>HTTP 头 {@code Authorization: Bearer <token>}</li>
 *   <li>查询参数 {@code ?t=<token>} —— 仅用于浏览器首次跳转，前端拿到后应立即清掉 query。</li>
 * </ol>
 * <p>
 * 当 {@code auth.mode=NONE} 时跳过校验，下发 {@link AuthContext#anonymousAdmin()}。
 * <p>
 * 失败时直接返回 {@code 401} JSON 错误体并短路下游处理。
 */
public final class AuthFilter implements HttpHandler {

    private final WebServerService server;
    private final HttpHandler downstream;

    public AuthFilter(WebServerService server, HttpHandler downstream) {
        this.server = server;
        this.downstream = downstream;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 预检统一通过；具体 method 校验由下游 handler 负责
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            server.writeJson(exchange, 204, "");
            return;
        }

        WebServerConfig.AuthMode mode = WebServerConfig.AUTH_MODE.get();
        if (mode == WebServerConfig.AuthMode.NONE) {
            exchange.setAttribute(AuthContext.ATTRIBUTE_KEY, AuthContext.anonymousAdmin());
            downstream.handle(exchange);
            return;
        }

        String token = extractToken(exchange);
        if (token == null) {
            unauthorized(exchange, "missing_token", "请通过游戏内终端的「网页访问」按钮获取链接");
            return;
        }

        AuthContext ctx = resolveToken(token);
        if (ctx == null) {
            unauthorized(exchange, "invalid_token", "Token 无效或已被重新生成，请回游戏内重新获取");
            return;
        }

        exchange.setAttribute(AuthContext.ATTRIBUTE_KEY, ctx);
        downstream.handle(exchange);
    }

    /** 从请求中提取 session token —— 优先 Authorization 头，其次 Cookie。 */
    private static @Nullable String extractToken(HttpExchange ex) {
        List<String> auth = ex.getRequestHeaders().get("Authorization");
        if (auth != null) {
            for (String h : auth) {
                if (h == null) continue;
                String trim = h.trim();
                if (trim.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    String t = trim.substring(7).trim();
                    if (!t.isEmpty()) return t;
                }
            }
        }
        // Cookie: ro_session=...
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String header : cookies) {
                if (header == null) continue;
                for (String pair : header.split(";")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    String k = pair.substring(0, eq).trim();
                    if ("ro_session".equals(k)) {
                        String v = pair.substring(eq + 1).trim();
                        if (!v.isBlank()) return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 把 session token 翻译为 {@link AuthContext}。需要切回主线程访问 {@link MinecraftServer}
     * 与 SavedData，确保线程安全。
     */
    private @Nullable AuthContext resolveToken(String token) {
        MinecraftServer mc = server.minecraftServer();
        try {
            return mc.submit(() -> {
                ServerLevel overworld = mc.overworld();
                if (overworld == null) return null;
                Optional<UUID> uuidOpt = PlayerWebTokenSavedData.get(overworld).resolveSession(token);
                if (uuidOpt.isEmpty()) return null;
                UUID uuid = uuidOpt.get();
                String name = lookupPlayerName(mc, uuid);
                boolean admin = isOp(mc, uuid);
                return new AuthContext(uuid, name, admin);
            }).get();
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[Web][Auth] 解析 Token 失败：{}", e.toString());
            return null;
        }
    }

    /** 优先在线玩家；否则查 UserCache（GameProfileCache）；都查不到时回退 UUID 短串。 */
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
        // 离线玩家：通过 ops.json 查询 —— 需要先从 profile cache 拿到 name
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

    /** 工具方法 —— 用于 NONE 模式下日志/whoami 等场景的语言无关提示。 */
    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
