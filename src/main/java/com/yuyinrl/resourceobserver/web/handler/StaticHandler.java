package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.web.WebServerService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 静态资源处理器 —— 从 classpath 下的 {@code /assets/resourceobserver/web/} 读取文件。
 * <p>
 * 本轮实现仅提供占位首页；后续集成 React Dashboard 时再挂载其构建产物。
 * 严格限制读取路径避免路径穿越。
 */
public final class StaticHandler implements HttpHandler {
    private static final String PREFIX = "/assets/resourceobserver/web/";

    private final WebServerService server;

    public StaticHandler(WebServerService server) {
        this.server = server;
    }

    /** 静态资源回退入口 —— SPA 单页路由不存在的路径回落到 index.html。 */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            server.writeJson(exchange, 204, "");
            return;
        }
        // 把 127.0.0.1 / 数字 IP 的访问 302 到 localhost，统一 origin —— 否则
        // cookie 与 localStorage 会按 origin 隔离，玩家在 127.0.0.1 拿到登录态后
        // 切到 localhost（或反之）就会丢失，需要重新走 token 链接。
        String redirect = canonicalRedirect(exchange);
        if (redirect != null) {
            exchange.getResponseHeaders().set("Location", redirect);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.contains("..")) {
            sendNotFound(exchange);
            return;
        }
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        // 尝试读取精确资源
        String resource = PREFIX + path.substring(1);
        byte[] data = readResource(resource);

        // SPA 回退：路径不带扩展名且不命中资源时，返回 index.html，
        // 以便 React Router 的 /overview、/storage 等前端路由能正常刷新。
        if (data == null && !hasFileExtension(path)) {
            data = readResource(PREFIX + "index.html");
            if (data != null) resource = PREFIX + "index.html";
        }

        if (data == null) {
            sendNotFound(exchange);
            return;
        }

        String contentType = guessContentType(resource);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // 带 hash 的静态资源（Vite 默认命名：name-[hash].ext）可长期缓存；
        // index.html 不缓存以确保前端及时获取最新入口。
        if (path.equals("/index.html") || path.equals("/")) {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        } else if (resource.contains("/assets/")) {
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        }
        exchange.sendResponseHeaders(200, data.length);
        try (var os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static byte @org.jetbrains.annotations.Nullable [] readResource(String resource) throws IOException {
        try (InputStream in = StaticHandler.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }

    private static boolean hasFileExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash && dot < path.length() - 1;
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] body = "<!doctype html><title>404</title><h1>404 Not Found</h1>"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(404, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String guessContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css"))  return "text/css; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".map"))  return "application/json; charset=utf-8";
        if (lower.endsWith(".svg"))  return "image/svg+xml";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico"))  return "image/x-icon";
        if (lower.endsWith(".woff"))  return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf"))   return "font/ttf";
        if (lower.endsWith(".otf"))   return "font/otf";
        if (lower.endsWith(".txt"))   return "text/plain; charset=utf-8";
        if (lower.endsWith(".wasm"))  return "application/wasm";
        return "application/octet-stream";
    }

    /**
     * 若请求 Host 是 {@code 127.0.0.1} / {@code ::1} / 其它数字 IP 形式，则返回应当跳转到
     * 的 {@code http://localhost:<port><uri>}；否则返回 {@code null} 表示无需跳转。
     * <p>
     * 这一步是为了把 {@code 127.0.0.1} 与 {@code localhost} 这两条入口收敛到同一个 origin —
     * 浏览器把它们视为不同站点，cookie / localStorage 不互通，会造成"在 127 上登录后，
     * 切到 localhost 又要重新登录"的体验问题。
     */
    private static @org.jetbrains.annotations.Nullable String canonicalRedirect(HttpExchange ex) {
        String hostHeader = ex.getRequestHeaders().getFirst("Host");
        if (hostHeader == null || hostHeader.isBlank()) return null;
        // host:port 或 [v6]:port 拆分
        String hostOnly;
        String portPart;
        if (hostHeader.startsWith("[")) {
            int rb = hostHeader.indexOf(']');
            if (rb < 0) return null;
            hostOnly = hostHeader.substring(1, rb);
            portPart = (rb + 2 < hostHeader.length() && hostHeader.charAt(rb + 1) == ':')
                    ? hostHeader.substring(rb + 2) : "";
        } else {
            int colon = hostHeader.lastIndexOf(':');
            if (colon < 0) {
                hostOnly = hostHeader;
                portPart = "";
            } else {
                hostOnly = hostHeader.substring(0, colon);
                portPart = hostHeader.substring(colon + 1);
            }
        }
        if (!isLoopbackAlias(hostOnly)) return null;
        // 已经是 localhost 就不跳
        if ("localhost".equalsIgnoreCase(hostOnly)) return null;
        String uri = ex.getRequestURI().toString();
        String portSeg = portPart.isBlank() ? "" : (":" + portPart);
        return "http://localhost" + portSeg + uri;
    }

    /** 是否是回环地址的等价形式（127.x / ::1 等），需要规范化到 localhost。 */
    private static boolean isLoopbackAlias(String host) {
        if (host == null) return false;
        if ("127.0.0.1".equals(host)) return true;
        if (host.startsWith("127.")) return true;
        if ("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) return true;
        return false;
    }
}
