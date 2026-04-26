package com.yuyinrl.resourceobserver.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.web.handler.CraftingHandler;
import com.yuyinrl.resourceobserver.web.handler.CraftingOrderHandler;
import com.yuyinrl.resourceobserver.web.handler.HealthHandler;
import com.yuyinrl.resourceobserver.web.handler.HistoryHandler;
import com.yuyinrl.resourceobserver.web.handler.IconHandler;
import com.yuyinrl.resourceobserver.web.handler.ItemsHandler;
import com.yuyinrl.resourceobserver.web.handler.MetaHandler;
import com.yuyinrl.resourceobserver.web.handler.ObserversHandler;
import com.yuyinrl.resourceobserver.web.handler.SamplerDebugHandler;
import com.yuyinrl.resourceobserver.web.handler.StaticHandler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 内置 Web Dashboard HTTP 服务 —— 随 Minecraft 服务端一同启动。
 * <p>
 * 基于 JDK 内置 {@link HttpServer}，无外部依赖。服务生命周期绑定到
 * {@link ServerStartedEvent} / {@link ServerStoppingEvent}，保证
 * 单人游戏存档切换以及专用服务端停机都能正确释放端口。
 * <p>
 * 所有需要访问世界/方块实体的处理器必须调用 {@link MinecraftServer#execute(Runnable)}
 * 或通过 {@link net.minecraft.server.level.ServerLevel} 的线程安全 API，
 * 避免在 HTTP 工作线程上直接触碰游戏对象。
 */
public final class WebServerService {
    private static @Nullable WebServerService INSTANCE;

    private final HttpServer server;
    private final MinecraftServer mcServer;
    private final boolean corsAllowAll;

    private WebServerService(HttpServer server, MinecraftServer mcServer, boolean corsAllowAll) {
        this.server = server;
        this.mcServer = mcServer;
        this.corsAllowAll = corsAllowAll;
    }

    public MinecraftServer minecraftServer() {
        return mcServer;
    }

    public boolean corsAllowAll() {
        return corsAllowAll;
    }

    public static @Nullable WebServerService instance() {
        return INSTANCE;
    }

    /** 注册事件监听器到 NeoForge 事件总线。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(Lifecycle.class);
    }

    /**
     * 写入 JSON 响应。会一并设置 CORS、Content-Type 等响应头。
     */
    public void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (corsAllowAll) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 事件订阅容器。 */
    @EventBusSubscriber(modid = ResourceObserverMod.MODID)
    public static final class Lifecycle {
        private Lifecycle() {
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            if (INSTANCE != null) {
                return;
            }
            if (!WebServerConfig.ENABLED.get()) {
                ResourceObserverMod.LOGGER.info("[Web] 服务已通过配置禁用，跳过启动");
                return;
            }
            String host = WebServerConfig.HOST.get();
            int port = WebServerConfig.PORT.get();
            boolean cors = WebServerConfig.CORS_ALLOW_ALL.get();
            MinecraftServer mc = event.getServer();
            try {
                // 提前在后台线程扫描 mod jar 资源（lang/textures），不阻塞服务端启动
                Thread initThread = new Thread(() -> {
                    try {
                        com.yuyinrl.resourceobserver.web.handler.ServerAssetIndex.ensureLoaded();
                    } catch (Throwable t) {
                        ResourceObserverMod.LOGGER.warn("[Web] ServerAssetIndex 初始化失败：{}", t.toString());
                    }
                }, "ResourceObserver-AssetIndex");
                initThread.setDaemon(true);
                initThread.start();

                HttpServer httpServer = HttpServer.create(new InetSocketAddress(host, port), 16);
                WebServerService svc = new WebServerService(httpServer, mc, cors);
                svc.installRoutes();
                httpServer.setExecutor(Executors.newFixedThreadPool(4, r -> {
                    Thread t = new Thread(r, "ResourceObserver-Web");
                    t.setDaemon(true);
                    return t;
                }));
                httpServer.start();
                INSTANCE = svc;
                ResourceObserverMod.LOGGER.info("[Web] Dashboard 已启动 http://{}:{}/", host, port);
            } catch (IOException e) {
                ResourceObserverMod.LOGGER.error("[Web] 启动失败（{}:{}）：{}", host, port, e.toString());
            }
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            WebServerService svc = INSTANCE;
            if (svc == null) return;
            try {
                svc.server.stop(0);
                ResourceObserverMod.LOGGER.info("[Web] Dashboard 已停止");
            } catch (Throwable t) {
                ResourceObserverMod.LOGGER.warn("[Web] 停止时出错: {}", t.toString());
            }
            INSTANCE = null;
        }
    }

    private void installRoutes() {
        HealthHandler health = new HealthHandler(this);
        MetaHandler meta = new MetaHandler(this);
        ObserversHandler observers = new ObserversHandler(this);
        CraftingHandler crafting = new CraftingHandler(this);
        CraftingOrderHandler craftingOrder = new CraftingOrderHandler(this);
        HistoryHandler history = new HistoryHandler(this);
        ItemsHandler items = new ItemsHandler(this);
        IconHandler icon = new IconHandler(this);
        SamplerDebugHandler samplerDebug = new SamplerDebugHandler(this);
        StaticHandler staticHandler = new StaticHandler(this);

        server.createContext("/api/health", health);
        server.createContext("/api/meta", meta);
        server.createContext("/api/observers", ex -> dispatchObservers(ex, observers, crafting, craftingOrder, history, items, samplerDebug));
        server.createContext("/api/icon/", icon);
        server.createContext("/", staticHandler);
    }

    private void dispatchObservers(HttpExchange ex, ObserversHandler observers, CraftingHandler crafting,
                                   CraftingOrderHandler craftingOrder,
                                   HistoryHandler history, ItemsHandler items, SamplerDebugHandler samplerDebug)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 204, "");
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/crafting/plan") || path.endsWith("/crafting/confirm") || path.endsWith("/crafting/cancel")) {
            craftingOrder.handle(ex);
        } else if (path.endsWith("/crafting")) {
            crafting.handle(ex);
        } else if (path.endsWith("/history")) {
            history.handle(ex);
        } else if (path.endsWith("/items")) {
            items.handle(ex);
        } else if (path.endsWith("/debug/sampler")) {
            samplerDebug.handle(ex);
        } else {
            observers.handle(ex);
        }
    }

    /** 通用错误响应。 */
    public void writeError(HttpExchange ex, int status, String message) throws IOException {
        String body = JsonWriter.write(Map.of("error", message, "status", status));
        writeJson(ex, status, body);
    }
}
