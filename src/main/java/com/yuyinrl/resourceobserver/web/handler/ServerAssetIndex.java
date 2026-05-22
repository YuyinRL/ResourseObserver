package com.yuyinrl.resourceobserver.web.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端资源索引：直接从已加载的 Mod jar 内读取 {@code assets/<ns>/lang/*.json}
 * 与 {@code assets/<ns>/textures/{item,block}/<path>.png}，无需客户端连接。
 * <p>
 * 由于 Mod 的 jar 文件同时存在于客户端与服务端，且其内置资源（lang、texture）
 * 不会被服务端运行时主动加载，但文件本身可通过 {@link IModFile#findResource(String...)}
 * 直接定位。本类把这些"沉默资源"激活成 Web Dashboard 可用的中文译名与图标字节。
 */
public final class ServerAssetIndex {

    /** 翻译表 translationKey -> displayName，按优先语言（zh_cn）填充，其次 en_us 填空缺。 */
    private static final Map<String, String> TRANSLATIONS = new ConcurrentHashMap<>();
    /** 图标字节缓存 itemId -> PNG bytes。 */
    private static final Map<String, byte[]> ICON_CACHE = new ConcurrentHashMap<>();
    private static final int ICON_CACHE_LIMIT = 4096;
    /** 优先语言；为空时表示尚未初始化。 */
    private static volatile String preferredLang = "zh_cn";
    private static volatile boolean langsLoaded = false;

    private ServerAssetIndex() {}

    /** 设置优先语言（如 "zh_cn"），并强制下一次调用重新加载。 */
    public static synchronized void setPreferredLanguage(String lang) {
        String norm = (lang == null || lang.isBlank()) ? "zh_cn" : lang.toLowerCase(Locale.ROOT).trim();
        if (!norm.equals(preferredLang)) {
            preferredLang = norm;
            TRANSLATIONS.clear();
            langsLoaded = false;
        }
    }

    /** 立即扫描所有 mod 的 lang 文件；幂等。 */
    public static synchronized void ensureLoaded() {
        if (langsLoaded) return;
        loadAllLanguageFiles(preferredLang);
        if (!"en_us".equals(preferredLang)) {
            // 用 en_us 兜底缺失项，保证未汉化的模组也至少有英文名
            loadAllLanguageFiles("en_us");
        }
        langsLoaded = true;
        ResourceObserverMod.LOGGER.info(
                "[ServerAssetIndex] 已加载翻译条目 {} 条（首选语言 {}）", TRANSLATIONS.size(), preferredLang);
    }

    /** 翻译单个 key；未命中返回 null。 */
    public static @Nullable String translate(String key) {
        if (key == null || key.isEmpty()) return null;
        ensureLoaded();
        return TRANSLATIONS.get(key);
    }

    /**
     * 从 mod jar 内直接读取物品/方块贴图的 PNG 字节。
     * 顺序：textures/item/&lt;path&gt;.png → textures/block/&lt;path&gt;.png。
     * 若是动画条带（高 &gt; 宽且高为宽整数倍），自动裁出首帧。
     */
    public static @Nullable byte[] findItemIcon(String ns, String path) {
        if (ns == null || ns.isEmpty() || path == null || path.isEmpty()) return null;
        String cacheKey = ns + ":" + path;
        byte[] cached = ICON_CACHE.get(cacheKey);
        if (cached != null) return cached;

        IModFile modFile = findModFile(ns);
        if (modFile == null) return null;

        for (String dir : new String[]{"item", "block"}) {
            byte[] bytes = readFromModFile(modFile, "assets", ns, "textures", dir, path + ".png");
            if (bytes != null) {
                byte[] cropped = cropFirstFrameIfAnimated(bytes);
                byte[] out = cropped != null ? cropped : bytes;
                if (ICON_CACHE.size() < ICON_CACHE_LIMIT) {
                    ICON_CACHE.put(cacheKey, out);
                }
                return out;
            }
        }
        return null;
    }

    // ---------------- 内部实现 ----------------

    private static void loadAllLanguageFiles(String lang) {
        ModList list = ModList.get();
        if (list == null) return;
        int loadedFiles = 0;
        for (IModInfo info : list.getMods()) {
            String modid = info.getModId();
            try {
                IModFile modFile = info.getOwningFile().getFile();
                byte[] bytes = readFromModFile(modFile, "assets", modid, "lang", lang + ".json");
                if (bytes == null) continue;
                String json = new String(bytes, StandardCharsets.UTF_8);
                int added = parseAndMerge(json);
                if (added > 0) loadedFiles++;
            } catch (Throwable t) {
                ResourceObserverMod.LOGGER.debug(
                        "[ServerAssetIndex] 读取 {} 的 {} 语言文件失败：{}", modid, lang, t.toString());
            }
        }
        ResourceObserverMod.LOGGER.debug(
                "[ServerAssetIndex] 语言 {} 命中 {} 个 mod 的 lang 文件", lang, loadedFiles);
    }

    private static int parseAndMerge(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return 0;
            JsonObject obj = el.getAsJsonObject();
            int added = 0;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                JsonElement v = e.getValue();
                if (v == null || !v.isJsonPrimitive()) continue;
                String key = e.getKey();
                String val = v.getAsString();
                if (key == null || key.isEmpty() || val == null || val.isEmpty()) continue;
                // putIfAbsent：首选语言先填，再用 en_us 兜底；不会覆盖已有 zh_cn
                if (TRANSLATIONS.putIfAbsent(key, val) == null) added++;
            }
            return added;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static @Nullable IModFile findModFile(String modid) {
        ModList list = ModList.get();
        if (list == null) return null;
        Optional<? extends ModContainer> opt = list.getModContainerById(modid);
        if (opt.isEmpty()) return null;
        try {
            return opt.get().getModInfo().getOwningFile().getFile();
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte @Nullable [] readFromModFile(IModFile modFile, String... pathParts) {
        try {
            Path p = modFile.findResource(pathParts);
            if (p == null || !Files.exists(p)) return null;
            return Files.readAllBytes(p);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 若是纵向 N 帧动画条带（高 &gt; 宽且高为宽整数倍），裁出首帧返回新 PNG 字节；
     * 否则返回 null（调用方使用原始字节）。
     */
    static byte @Nullable [] cropFirstFrameIfAnimated(byte[] pngBytes) {
        try {
            java.awt.image.BufferedImage src =
                    javax.imageio.ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (src == null) return null;
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= 0 || h <= 0 || h <= w) return null;
            if (h % w != 0) return null;
            java.awt.image.BufferedImage copy =
                    new java.awt.image.BufferedImage(w, w, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = copy.createGraphics();
            try {
                g.drawImage(src.getSubimage(0, 0, w, w), 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            javax.imageio.ImageIO.write(copy, "PNG", out);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
