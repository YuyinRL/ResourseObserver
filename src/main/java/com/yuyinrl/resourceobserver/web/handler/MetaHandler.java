package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.web.WebServerService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /api/meta} —— Dashboard 元数据：Mod 版本、可用集成、API 版本等。
 * <p>
 * 由 Web Dashboard 前端启动时调用一次，用来决定显示哪些 Panel、哪些数据源可用。
 */
public final class MetaHandler extends BaseApiHandler implements HttpHandler {

    /** API schema 版本 —— 前端据此判断是否需要提示用户升级。 */
    private static final int API_VERSION = 1;

    public MetaHandler(WebServerService server) {
        super(server);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modid", ResourceObserverMod.MODID);
        body.put("apiVersion", API_VERSION);

        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("ae2", true);
        integrations.put("fluxNetworks", isFluxAvailable());
        integrations.put("mekanism", isMekanismAvailable());
        body.put("integrations", integrations);

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("health", "/api/health");
        endpoints.put("meta", "/api/meta");
        endpoints.put("observers", "/api/observers");
        endpoints.put("observerDetail", "/api/observers/{dim}/{x}/{y}/{z}");
        endpoints.put("observerCrafting", "/api/observers/{dim}/{x}/{y}/{z}/crafting");
        body.put("endpoints", endpoints);

        sendJson(exchange, 200, body);
    }

    private static boolean isFluxAvailable() {
        try {
            return FluxNetworksIntegration.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMekanismAvailable() {
        try {
            Class.forName("mekanism.api.Mekanism");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
