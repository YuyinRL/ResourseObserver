package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.JsonWriter;
import com.yuyinrl.resourceobserver.web.WebServerConfig;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.auth.AuthContext;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * HTTP 处理器基类 —— 提供主线程切换与错误捕获的工具方法。
 * <p>
 * HTTP 请求由独立工作线程处理；访问 Minecraft 世界/方块实体必须切回服务端主线程。
 * 本类封装 {@link MinecraftServer#submit(java.util.function.Supplier)} + 超时，
 * 避免主线程卡死时拖累 HTTP 线程池。
 */
public abstract class BaseApiHandler {
    /** 主线程任务超时 —— 3 秒内必须完成，否则放弃并返回 504。 */
    protected static final long MAIN_THREAD_TIMEOUT_MS = 3000L;

    protected final WebServerService server;

    protected BaseApiHandler(WebServerService server) {
        this.server = server;
    }

    /** 在服务端主线程同步执行 {@code fn} 并等待结果；超时或异常时返回 null。 */
    protected <T> @Nullable T runOnMain(Function<MinecraftServer, T> fn) {
        MinecraftServer mc = server.minecraftServer();
        CompletableFuture<T> fut = mc.submit(() -> fn.apply(mc));
        try {
            return fut.get(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fut.cancel(true);
            ResourceObserverMod.LOGGER.warn("[Web] 主线程任务超时");
            return null;
        } catch (InterruptedException | CancellationException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[Web] 主线程任务异常: {}", e.toString());
            return null;
        }
    }

    /** 按维度 id 解析 ServerLevel（仅限已加载维度）。 */
    protected @Nullable ServerLevel resolveLevel(MinecraftServer mc, String dimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(dimension)
            );
            return mc.getLevel(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析形如 {@code /api/observers/<dim>/<x>/<y>/<z>[/suffix]} 的路径。返回 null 表示无效。 */
    protected @Nullable ObserverTarget parseObserverPath(String path) {
        String stripped = path.startsWith("/api/observers/") ? path.substring("/api/observers/".length()) : null;
        if (stripped == null) return null;
        String[] parts = stripped.split("/");
        if (parts.length < 4) return null;
        try {
            String dim = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new ObserverTarget(dim, new BlockPos(x, y, z));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected void sendError(HttpExchange exchange, int status, String message) throws IOException {
        server.writeError(exchange, status, message);
    }

    protected void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        server.writeJson(exchange, status, JsonWriter.write(payload));
    }

    /**
     * 校验当前请求的 {@link AuthContext} 是否有权访问指定 Observer。
     * <p>未通过时返回 {@code false} 并已写入 403 响应；handler 应直接 return。
     * 调用前必须保证 observer 非空。本方法读取 {@link WebServerConfig#LEGACY_OBSERVER_POLICY}
     * 决定无主 Observer 的兜底策略。</p>
     */
    protected boolean checkObserverAccess(HttpExchange exchange, ObserverBlockEntity observer) throws IOException {
        AuthContext ctx = AuthContext.from(exchange);
        if (ctx == null) {
            sendError(exchange, 401, "unauthenticated");
            return false;
        }
        boolean legacyAllowsAnyone =
                WebServerConfig.LEGACY_OBSERVER_POLICY.get() == WebServerConfig.LegacyObserverPolicy.PUBLIC;
        if (!observer.canView(ctx.viewer(), ctx.admin(), legacyAllowsAnyone)) {
            sendError(exchange, 403, "forbidden");
            return false;
        }
        return true;
    }

    /** 当前请求的 AuthContext —— 通常用于列表过滤；未鉴权返回 null。 */
    protected @Nullable AuthContext authContext(HttpExchange exchange) {
        return AuthContext.from(exchange);
    }

    /** 是否允许任何已登录用户查看无主 Observer。 */
    protected static boolean legacyAllowsAnyone() {
        return WebServerConfig.LEGACY_OBSERVER_POLICY.get() == WebServerConfig.LegacyObserverPolicy.PUBLIC;
    }

    /** (维度, 坐标) 元组。 */
    public record ObserverTarget(String dimension, BlockPos pos) {
    }

    /**
     * 主线程组合操作 —— 解析 Observer + 鉴权 + 调用 builder，三种状态合一返回。
     * 本方法适合那些"只服务于单个 Observer"的 detail 类 handler（items/history/crafting 等）。
     * <p>
     * 调用方 typical:
     * <pre>{@code
     *   AccessResult<Map<String,Object>> res = runOnMainWithAccess(exchange, target, (mc, obs) -> buildXxx(mc, obs));
     *   if (res.isNotFound()) { sendError(exchange, 404, ...); return; }
     *   if (res.isForbidden()) { sendError(exchange, 403, ...); return; }
     *   sendJson(exchange, 200, res.body());
     * }</pre>
     */
    protected <T> AccessResult<T> runOnMainWithAccess(
            HttpExchange exchange,
            ObserverTarget target,
            java.util.function.BiFunction<MinecraftServer, ObserverBlockEntity, T> builder) {
        AuthContext ctx = AuthContext.from(exchange);
        AccessResult<T> result = runOnMain(mc -> {
            ServerLevel level = resolveLevel(mc, target.dimension());
            if (level == null) return AccessResult.<T>notFound();
            BlockPos pos = target.pos();
            if (!level.isLoaded(pos)) return AccessResult.<T>notFound();
            ObserverBlockEntity observer =
                    com.yuyinrl.resourceobserver.service.ObserverService.findByPos(level, pos).orElse(null);
            if (observer == null) return AccessResult.<T>notFound();
            if (!observer.canView(ctx == null ? null : ctx.viewer(),
                    ctx != null && ctx.admin(), legacyAllowsAnyone())) {
                return AccessResult.<T>forbidden();
            }
            T body = builder.apply(mc, observer);
            if (body == null) return AccessResult.<T>notFound();
            return AccessResult.ok(body);
        });
        // 主线程任务超时（runOnMain 返回 null）—— 视作 504 由调用方决定，但默认按 not_found 处理避免泄漏
        return result == null ? AccessResult.<T>notFound() : result;
    }

    /** Observer 详情访问结果三态：OK / NOT_FOUND / FORBIDDEN。 */
    public record AccessResult<T>(int status, @Nullable T body) {
        public static <T> AccessResult<T> ok(T body) { return new AccessResult<>(200, body); }
        public static <T> AccessResult<T> notFound() { return new AccessResult<>(404, null); }
        public static <T> AccessResult<T> forbidden() { return new AccessResult<>(403, null); }
        public boolean isOk() { return status == 200; }
        public boolean isNotFound() { return status == 404; }
        public boolean isForbidden() { return status == 403; }
    }
}
