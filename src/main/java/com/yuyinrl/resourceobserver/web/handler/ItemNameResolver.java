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
    private static final int CACHE_LIMIT = 4096;

    private ItemNameResolver() {}

    public record Resolved(@Nullable String translationKey, @Nullable String displayName) {
        static final Resolved EMPTY = new Resolved(null, null);
    }

    public static Resolved resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) return Resolved.EMPTY;
        Resolved cached = CACHE.get(itemId);
        if (cached != null) return cached;
        Resolved r = doResolve(itemId);
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.put(itemId, r);
        }
        return r;
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
                try {
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
            try {
                // Component.translatable(...).getString() 会通过当前 Language（dist 客户端 / 服务端均可）解析。
                displayName = Component.translatable(key).getString();
                if (displayName != null && displayName.equals(key)) {
                    // 翻译失败 —— 回退到 ID 末段的 Title Case，让前端有内容可展示
                    displayName = humanize(rl.getPath());
                }
            } catch (Throwable t) {
                ResourceObserverMod.LOGGER.debug("[Web] 翻译物品名失败 {}: {}", itemId, t.toString());
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
