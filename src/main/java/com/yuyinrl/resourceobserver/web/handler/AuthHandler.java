package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.auth.AuthContext;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /api/auth/whoami} —— 返回当前已登录会话的玩家信息。
 * <p>
 * 由 {@link com.yuyinrl.resourceobserver.web.auth.AuthFilter} 在过滤通过后注入
 * {@link AuthContext}；本端点仅做格式化输出，未通过鉴权时早在过滤器层就被 401 拒绝，
 * 不会进入这里。
 */
public final class AuthHandler extends BaseApiHandler implements HttpHandler {

    public AuthHandler(WebServerService server) {
        super(server);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        AuthContext ctx = AuthContext.from(exchange);
        if (ctx == null) {
            sendError(exchange, 401, "no auth context");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", ctx.viewer() == null ? null : ctx.viewer().toString());
        body.put("name", ctx.viewerName());
        body.put("admin", ctx.admin());
        sendJson(exchange, 200, body);
    }
}
