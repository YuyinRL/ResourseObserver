package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.JsonWriter;
import com.yuyinrl.resourceobserver.web.WebServerService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /api/health} —— 健康检查端点，返回服务存活状态与模组版本。
 */
public final class HealthHandler implements HttpHandler {
    private final WebServerService server;

    public HealthHandler(WebServerService server) {
        this.server = server;
    }

    /** /api/health —— 健康检查；恒返回 {@code {"status":"ok"}}。 */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            server.writeJson(exchange, 204, "");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("modid", ResourceObserverMod.MODID);
        payload.put("serverName", server.minecraftServer().getMotd());
        payload.put("tickCount", server.minecraftServer().getTickCount());
        payload.put("singleplayer", server.minecraftServer().isSingleplayer());
        server.writeJson(exchange, 200, JsonWriter.write(payload));
    }
}
