package com.yuyinrl.resourceobserver.web.auth;

import com.yuyinrl.resourceobserver.web.WebServerConfig;

/**
 * 工具类：根据配置生成对外暴露的 Web Dashboard URL（含可选的 token 查询参数）。
 * <p>
 * 优先使用 {@link WebServerConfig#PUBLIC_URL_BASE}，未设置时退回到 {@code http://host:port}。
 * 当 host 为 {@code 0.0.0.0} / {@code ::} 这类通配地址时无法自动推断主机名，
 * 此时返回值仅适合本机访问；服主应配置 publicUrlBase。
 */
public final class WebUrlBuilder {

    private WebUrlBuilder() {
    }

    /** 生成基础 URL（不含 token，不带尾部 {@code /}）。 */
    public static String baseUrl() {
        String configured = WebServerConfig.PUBLIC_URL_BASE.get();
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured.trim());
        }
        String host = WebServerConfig.HOST.get();
        int port = WebServerConfig.PORT.get();
        // 通配地址在浏览器侧无法直接访问；回退到 localhost 让单机/本机可用，
        // 服主想跨网访问需自己配置 publicUrlBase。
        if (host == null || host.isBlank() || "0.0.0.0".equals(host) || "::".equals(host)) {
            host = "localhost";
        }
        return "http://" + host + ":" + port;
    }

    /** 生成完整登录 URL —— {@code <base>/?t=<token>}。 */
    public static String loginUrl(String token) {
        if (token == null) token = "";
        String base = baseUrl();
        return base + "/?t=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 当前是否能稳定推断出对外可用的 URL（用于在 GUI 中给玩家提示）。 */
    public static boolean hasReliablePublicUrl() {
        String configured = WebServerConfig.PUBLIC_URL_BASE.get();
        if (configured != null && !configured.isBlank()) return true;
        String host = WebServerConfig.HOST.get();
        return !(host == null || host.isBlank() || "0.0.0.0".equals(host) || "::".equals(host));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
