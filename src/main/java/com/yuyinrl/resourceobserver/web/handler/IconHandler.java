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
 * 仅在集成端（客户端单机）有效；专用服务端返回 503。<br>
 * 实现：把请求转发到 {@link com.yuyinrl.resourceobserver.client.web.IconRenderer}
 * 在客户端线程渲染物品贴图为 PNG，并落到 {@code <gameDir>/cache/resourceobserver-icons-v2/} 文件缓存。
 */
public final class IconHandler extends BaseApiHandler implements HttpHandler {

    private static final ConcurrentMap<String, Path> CACHE = new ConcurrentHashMap<>();

    public IconHandler(WebServerService server) {
        super(server);
    }

    /** 暴露给 {@link MetaHandler} —— 前端据此决定是否使用图标。 */
    public static boolean isClientSideAvailable() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        if (!isClientSideAvailable()) {
            sendError(exchange, 503, "icon api requires client-side runtime");
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
        Path file;
        if (cached != null && Files.exists(cached)) {
            file = cached;
        } else {
            file = renderToFile(itemId, cacheKey);
            if (file == null || !Files.exists(file)) {
                sendError(exchange, 404, "icon not available");
                return;
            }
            CACHE.put(cacheKey, file);
        }

        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "image/png");
        headers.set("Cache-Control", "public, max-age=86400");
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

    static Path cacheRoot() {
        return Paths.get("cache", "resourceobserver-icons-v2");
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
