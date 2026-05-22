package com.yuyinrl.resourceobserver.web.handler;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品 ID → (translationKey, displayName) 解析器。
 * <p>
 * 解析顺序：
 * 1. 注册表查找：根据 itemId 从 BuiltInRegistries.ITEM 取出 Item，得到 descriptionId（即翻译键）；
 * 2. 显示名优先取客户端语言（集成端单机），其次取服务端 LanguageManager（一般为 en_us）；
 * 3. 缓存命中后直接返回，避免每次 HTTP 请求都跑一遍。
 * <p>
 * Web 侧会按 displayName ▶ translationKey 末段 ▶ 原始 ID 的优先级展示。
 */
public final class ItemNameResolver {
    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();
    /** 客户端镜像上来的本地化名称，优先级最高。 */
    private static final Map<String, String> OVERRIDE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 4096;
    private static final int OVERRIDE_LIMIT = 16384;

    private ItemNameResolver() {}

    public record Resolved(@Nullable String translationKey, @Nullable String displayName) {
        static final Resolved EMPTY = new Resolved(null, null);
    }

    public static Resolved resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) return Resolved.EMPTY;
        String override = OVERRIDE.get(itemId);
        if (override != null && !override.isEmpty()) {
            Resolved cached = CACHE.get(itemId);
            String key = cached == null ? null : cached.translationKey();
            return new Resolved(key, override);
        }
        Resolved cached = CACHE.get(itemId);
        if (cached != null) return cached;
        Resolved r = doResolve(itemId);
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.put(itemId, r);
        }
        return r;
    }

    /**
     * 来自客户端镜像的本地化名称表 → 写入 OVERRIDE 层，下次 {@link #resolve} 优先返回。
     *
     * @param language 客户端语言（仅日志用，便于排查多语言冲突）
     * @param names    itemId → displayName
     */
    public static void applyOverrides(String language, Map<String, String> names) {
        if (names == null || names.isEmpty()) return;
        int added = 0;
        for (Map.Entry<String, String> e : names.entrySet()) {
            String id = e.getKey();
            String name = e.getValue();
            if (id == null || id.isBlank() || name == null || name.isBlank()) continue;
            if (OVERRIDE.size() >= OVERRIDE_LIMIT && !OVERRIDE.containsKey(id)) continue;
            OVERRIDE.put(id, name);
            added++;
        }
        if (added > 0) {
            com.yuyinrl.resourceobserver.ResourceObserverMod.LOGGER.debug(
                    "[Web] 客户端镜像名称 {} 条，语言 {}，OVERRIDE 累计 {}", added, language, OVERRIDE.size());
        }
    }

    /** 当服务端语言切换或 mod 重载时手动清空缓存（暂未挂事件，预留接口）。 */
    public static void invalidate() {
        CACHE.clear();
    }

    private static Resolved doResolve(String itemId) {
        try {
            // 流体显式前缀：fluid:<namespace>:<path>
            if (itemId.startsWith("fluid:")) {
                String fluidIdRaw = itemId.substring("fluid:".length());
                ResourceLocation fluidRl = ResourceLocation.tryParse(fluidIdRaw);
                if (fluidRl == null) return Resolved.EMPTY;
                Fluid fluid = BuiltInRegistries.FLUID.get(fluidRl);
                if (fluid == null) return Resolved.EMPTY;
                String key = fluid.getFluidType().getDescriptionId();
                String displayName = null;
                String fromAssets = ServerAssetIndex.translate(key);
                if (fromAssets != null && !fromAssets.isEmpty()) {
                    displayName = fromAssets;
                } else try {
                    displayName = Component.translatable(key).getString();
                    if (displayName != null && displayName.equals(key)) {
                        displayName = humanize(fluidRl.getPath());
                    }
                } catch (Throwable t) {
                    ResourceObserverMod.LOGGER.debug("[Web] 翻译流体名失败 {}: {}", itemId, t.toString());
                }
                return new Resolved(key, displayName);
            }
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return Resolved.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) return Resolved.EMPTY;
            String key = item.getDescriptionId();
            String displayName = null;
            // 1) 先查服务端从 mod jar 加载的 zh_cn / en_us 翻译表（dedicated server 唯一可靠来源）
            String fromAssets = ServerAssetIndex.translate(key);
            if (fromAssets != null && !fromAssets.isEmpty()) {
                displayName = fromAssets;
            } else {
                try {
                    // 2) 兜底：Component.translatable —— 集成端可用，专用服务端通常仅 en_us
                    displayName = Component.translatable(key).getString();
                    if (displayName != null && displayName.equals(key)) {
                        displayName = humanize(rl.getPath());
                    }
                } catch (Throwable t) {
                    ResourceObserverMod.LOGGER.debug("[Web] 翻译物品名失败 {}: {}", itemId, t.toString());
                }
            }
            return new Resolved(key, displayName);
        } catch (Throwable t) {
            return Resolved.EMPTY;
        }
    }

    private static String humanize(String path) {
        if (path == null || path.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '-' || c == '/') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
