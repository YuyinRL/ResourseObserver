package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.WebServerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@code GET /api/icon/{namespace}/{path}} —— 物品图标 PNG 接口。
 * <p>
 * 集成端直接调用客户端渲染器；专用服务端通过在线客户端镜像上传真实渲染 PNG。
 * 缓存位于 {@code <gameDir>/cache/resourceobserver-icons-v4/}。
 */
public final class IconHandler extends BaseApiHandler implements HttpHandler {

    private static final ConcurrentMap<String, Path> CACHE = new ConcurrentHashMap<>();

    public IconHandler(WebServerService server) {
        super(server);
    }

    /** 暴露给 {@link MetaHandler} —— 前端据此决定是否使用图标。专用服务端也启用，靠客户端镜像补图。 */
    public static boolean isClientSideAvailable() {
        return true;
    }

    /** 真正的"客户端可本地渲染"——仅集成端可用。 */
    private static boolean canRenderLocally() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        // /api/icon/{ns}/{path}  —— path 可能含斜杠（如 ae2/printed_silicon），保留剩余段
        String prefix = "/api/icon/";
        if (!path.startsWith(prefix)) {
            sendError(exchange, 404, "not found");
            return;
        }
        String rest = path.substring(prefix.length());
        int firstSlash = rest.indexOf('/');
        if (firstSlash <= 0 || firstSlash == rest.length() - 1) {
            sendError(exchange, 400, "bad icon path");
            return;
        }
        String ns = urlDecode(rest.substring(0, firstSlash));
        String itemPath = urlDecode(rest.substring(firstSlash + 1));
        String itemId = ns + ":" + itemPath;
        String cacheKey = sanitize(ns) + "__" + sanitize(itemPath);

        Path cached = CACHE.get(cacheKey);
        Path file = null;
        if (cached != null && Files.exists(cached)) {
            file = cached;
        } else {
            if (cached != null) {
                ResourceObserverMod.LOGGER.debug("[Web] CACHE 命中但文件不存在: {} -> {}", cacheKey, cached);
            }
            // 优先查磁盘缓存（专用服务端 + 客户端镜像 都共享此目录）
            Path onDisk = cacheRoot().resolve(cacheKey + ".png");
            if (Files.exists(onDisk)) {
                file = onDisk;
                CACHE.put(cacheKey, onDisk.toAbsolutePath());
            } else if (canRenderLocally()) {
                file = renderToFile(itemId, cacheKey);
                if (file != null && Files.exists(file)) {
                    CACHE.put(cacheKey, file.toAbsolutePath());
                }
            } else {
                // 专用服务端无法执行 Minecraft 物品渲染；请求在线客户端代渲染，保持与 ModernUI 一致
                requestClientMirror(itemId);
            }
        }

        if (file == null || !Files.exists(file)) {
            sendError(exchange, 404, "icon not available");
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "image/png");
        // 较短的浏览器缓存：避免 PNG 被替换后浏览器长时间使用旧版本（页签内仍有内存缓存）
        headers.set("Cache-Control", "public, max-age=300, must-revalidate");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 调用客户端渲染器；失败时返回 null。 */
    private static Path renderToFile(String itemId, String cacheKey) {
        try {
            // 使用反射延迟加载客户端类，避免在专用服务端 ClassLoader 触发 client-only 引用
            Class<?> renderer = Class.forName(
                    "com.yuyinrl.resourceobserver.client.web.IconRenderer");
            Object result = renderer
                    .getMethod("renderToCache", String.class, String.class)
                    .invoke(null, itemId, cacheKey);
            return (Path) result;
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.debug("[Web] 渲染物品图标失败 {}: {}", itemId, t.toString());
            return null;
        }
    }

    /** 清空内存缓存（暂未挂事件，预留接口）。文件保留以便后续命中。 */
    public static void invalidate() {
        CACHE.clear();
    }

    /** 把客户端上传的 PNG 字节写入磁盘缓存，下一次 web 请求即可命中。 */
    public static void acceptClientUpload(String itemId, byte[] pngBytes) {
        if (itemId == null || itemId.isBlank() || pngBytes == null || pngBytes.length == 0) return;
        // 简易 PNG 头校验：89 50 4E 47
        if (pngBytes.length < 8
                || (pngBytes[0] & 0xff) != 0x89 || pngBytes[1] != 0x50
                || pngBytes[2] != 0x4E || pngBytes[3] != 0x47) {
            return;
        }
        String cacheKey = cacheKeyFor(itemId);
        if (cacheKey == null) return;
        try {
            Path dir = cacheRoot();
            Files.createDirectories(dir);
            Path file = dir.resolve(cacheKey + ".png");
            Files.write(file, pngBytes);
            CACHE.put(cacheKey, file.toAbsolutePath());
            PENDING.remove(cacheKey);
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[Web] 写入客户端镜像图标失败 {}: {}", itemId, e.toString());
        }
    }

    /** 解析 itemId 为缓存 key（与 handle() 内逻辑保持一致）。 */
    private static @org.jetbrains.annotations.Nullable String cacheKeyFor(String itemId) {
        int idx = itemId.indexOf(':');
        if (idx <= 0 || idx == itemId.length() - 1) return null;
        String ns = itemId.substring(0, idx);
        String pathPart = itemId.substring(idx + 1);
        return sanitize(ns) + "__" + sanitize(pathPart);
    }

    /** 节流：同一 itemId 在 PENDING_TTL 内只触发一次客户端请求。 */
    private static final ConcurrentMap<String, Long> PENDING = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_NANOS = 10_000_000_000L; // 10s

    /** 在专用服务端通过广播让在线客户端代为渲染并上传图标。 */
    private static void requestClientMirror(String itemId) {
        long now = System.nanoTime();
        Long last = PENDING.get(itemId);
        if (last != null && now - last < PENDING_TTL_NANOS) return;
        PENDING.put(itemId, now);
        try {
            net.minecraft.server.MinecraftServer mc =
                    net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (mc == null) return;
            var packet = new com.yuyinrl.resourceobserver.network.IconRequestPayload(java.util.List.of(itemId));
            for (net.minecraft.server.level.ServerPlayer p : mc.getPlayerList().getPlayers()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, packet);
                break; // 第一名玩家足以；多发只会浪费带宽
            }
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.debug("[Web] 触发客户端图标镜像失败 {}: {}", itemId, t.toString());
        }
    }

    static Path cacheRoot() {
        return Paths.get("cache", "resourceobserver-icons-v4");
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
