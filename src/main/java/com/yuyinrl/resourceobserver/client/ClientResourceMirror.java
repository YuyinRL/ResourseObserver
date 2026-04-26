package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.web.IconRenderer;
import com.yuyinrl.resourceobserver.network.ClientIconUploadPayload;
import com.yuyinrl.resourceobserver.network.ClientNameUploadPayload;
import com.yuyinrl.resourceobserver.network.IconRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端资源镜像 —— 登录服务器后，把本机解析到的物品本地化名称（以及按需的图标 PNG）
 * 上传给服务端，让专用服务端的 Web UI 拿到中文名称和模组图标。
 * <p>
 * 仅 {@code Dist.CLIENT} 加载，不能被服务端 ClassLoader 触达。
 */
public final class ClientResourceMirror {

    private static final AtomicBoolean MIRRORED = new AtomicBoolean(false);

    private ClientResourceMirror() {}

    public static void register() {
        // ClientPlayerNetworkEvent 在 NeoForge 游戏总线（运行时）上触发
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientResourceMirror::onLoggingIn);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientResourceMirror::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (MIRRORED.getAndSet(true)) return;
        // 异步线程扫描 + 分批发送，避免阻塞客户端主线程；
        // 延迟 5 秒等待资源管理器/语言文件完全加载，避免拿到未翻译的 raw key
        Thread t = new Thread(() -> {
            try { Thread.sleep(5000L); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            doMirrorNames();
        }, "RO-NameMirror");
        t.setDaemon(true);
        t.start();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MIRRORED.set(false);
    }

    private static void doMirrorNames() {
        try {
            String lang = currentLanguage();
            List<String> ids = new ArrayList<>(ClientNameUploadPayload.MAX_BATCH);
            List<String> names = new ArrayList<>(ClientNameUploadPayload.MAX_BATCH);
            int total = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                if (key == null) continue;
                String id = key.toString();
                String descId;
                String name;
                try {
                    descId = item.getDescriptionId();
                    name = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
                } catch (Throwable ignored) {
                    continue;
                }
                if (name == null || name.isBlank()) continue;
                // 跳过未翻译的原始 key —— 让服务端用注册表 path humanize 兜底（"diamond_axe" → "Diamond Axe"）
                // 比 "item.minecraft.diamond_axe" 这种原始键好看
                if (name.equals(descId)) continue;
                ids.add(id);
                names.add(name);
                if (ids.size() >= ClientNameUploadPayload.MAX_BATCH) {
                    sendBatch(lang, ids, names);
                    total += ids.size();
                    ids = new ArrayList<>(ClientNameUploadPayload.MAX_BATCH);
                    names = new ArrayList<>(ClientNameUploadPayload.MAX_BATCH);
                    // 适度让步，避免占满网络
                    try { Thread.sleep(40L); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (!ids.isEmpty()) {
                sendBatch(lang, ids, names);
                total += ids.size();
            }
            ResourceObserverMod.LOGGER.info("[NameMirror] 已上传 {} 条本地化名称（{}）", total, lang);
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[NameMirror] 上传过程异常: {}", t.toString());
        }
    }

    private static void sendBatch(String lang, List<String> ids, List<String> names) {
        try {
            PacketDistributor.sendToServer(new ClientNameUploadPayload(lang, ids, names));
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[NameMirror] 发送批次失败: {}", t.toString());
        }
    }

    private static String currentLanguage() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                return mc.options.languageCode;
            }
        } catch (Throwable ignored) {}
        return "zh_cn";
    }

    /** 处理服务端发来的图标请求：在工作线程渲染（避免在 render 线程死锁）。 */
    public static void handleIconRequest(IconRequestPayload payload) {
        if (payload == null || payload.itemIds() == null || payload.itemIds().isEmpty()) return;
        // 必须切到非 render 线程；IconRenderer.renderToCache 内部可能 mc.execute 等待结果，
        // 在 render 线程上调用会直接死锁导致 2-5s 超时
        final java.util.List<String> ids = new java.util.ArrayList<>(payload.itemIds());
        Thread t = new Thread(() -> processIconRequests(ids), "RO-IconMirror");
        t.setDaemon(true);
        t.start();
    }

    private static void processIconRequests(java.util.List<String> itemIds) {
        for (String itemId : itemIds) {
            if (itemId == null || itemId.isBlank()) continue;
            try {
                String cacheKey = cacheKeyFor(itemId);
                if (cacheKey == null) continue;
                Path file = IconRenderer.renderToCache(itemId, cacheKey);
                if (file == null || !Files.exists(file)) continue;
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length == 0 || bytes.length > ClientIconUploadPayload.MAX_BYTES) continue;
                PacketDistributor.sendToServer(new ClientIconUploadPayload(itemId, bytes));
            } catch (Throwable t) {
                ResourceObserverMod.LOGGER.debug("[IconMirror] 上传 {} 失败: {}", itemId, t.toString());
            }
        }
    }

    private static String cacheKeyFor(String itemId) {
        int idx = itemId.indexOf(':');
        if (idx <= 0 || idx == itemId.length() - 1) return null;
        return sanitize(itemId.substring(0, idx)) + "__" + sanitize(itemId.substring(idx + 1));
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
