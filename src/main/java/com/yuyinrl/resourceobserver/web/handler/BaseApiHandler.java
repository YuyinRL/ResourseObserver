package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.JsonWriter;
import com.yuyinrl.resourceobserver.web.WebServerService;
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

    /** (维度, 坐标) 元组。 */
    public record ObserverTarget(String dimension, BlockPos pos) {
    }
}
