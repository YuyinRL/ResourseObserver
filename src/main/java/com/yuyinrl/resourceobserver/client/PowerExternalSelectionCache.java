package com.yuyinrl.resourceobserver.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-side persistence for selected external storage groups on power page.
 */
public final class PowerExternalSelectionCache {
    private PowerExternalSelectionCache() {
    }

    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("resourceobserver_external_selection_cache.json");
    private static final int CACHE_VERSION = 1;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_GROUP_IDS_PER_ENTRY = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, CacheEntry> ENTRIES = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static synchronized LinkedHashSet<String> loadSelection(@Nullable String observerKey) {
        ensureLoaded();
        if (observerKey == null || observerKey.isBlank()) {
            return new LinkedHashSet<>();
        }
        CacheEntry entry = ENTRIES.get(observerKey);
        if (entry == null) {
            return new LinkedHashSet<>();
        }
        entry.updatedAt = System.currentTimeMillis();
        LinkedHashSet<String> sanitized = sanitizeIds(entry.selectedExternalGroupIds);
        entry.selectedExternalGroupIds = new ArrayList<>(sanitized);
        return sanitized;
    }

    public static synchronized void saveSelection(@Nullable String observerKey, Set<String> selectedExternalGroupIds) {
        ensureLoaded();
        if (observerKey == null || observerKey.isBlank()) {
            return;
        }

        LinkedHashSet<String> sanitized = sanitizeIds(selectedExternalGroupIds);
        if (sanitized.isEmpty()) {
            if (ENTRIES.remove(observerKey) != null) {
                flushQuietly();
            }
            return;
        }

        CacheEntry entry = new CacheEntry();
        entry.selectedExternalGroupIds = new ArrayList<>(sanitized);
        entry.updatedAt = System.currentTimeMillis();
        ENTRIES.put(observerKey, entry);
        trimOldestEntries();
        flushQuietly();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        ENTRIES.clear();
        if (!Files.exists(CACHE_PATH)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(CACHE_PATH, StandardCharsets.UTF_8)) {
            CacheFile file = GSON.fromJson(reader, CacheFile.class);
            if (file == null || file.entries == null || file.entries.isEmpty()) {
                return;
            }
            for (Map.Entry<String, CacheEntry> e : file.entries.entrySet()) {
                String key = e.getKey();
                CacheEntry value = e.getValue();
                if (key == null || key.isBlank() || value == null) {
                    continue;
                }
                LinkedHashSet<String> sanitized = sanitizeIds(value.selectedExternalGroupIds);
                if (sanitized.isEmpty()) {
                    continue;
                }
                CacheEntry entry = new CacheEntry();
                entry.selectedExternalGroupIds = new ArrayList<>(sanitized);
                entry.updatedAt = value.updatedAt;
                ENTRIES.put(key, entry);
            }
            trimOldestEntries();
        } catch (JsonParseException e) {
            ResourceObserverMod.LOGGER.warn("[ResourceObserver] Failed to parse external selection cache: {}", e.getMessage());
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[ResourceObserver] Failed to read external selection cache", e);
        }
    }

    private static void flushQuietly() {
        try {
            Path parent = CACHE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            CacheFile file = new CacheFile();
            file.version = CACHE_VERSION;
            file.entries = new LinkedHashMap<>(ENTRIES);

            try (Writer writer = Files.newBufferedWriter(
                    CACHE_PATH,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                GSON.toJson(file, writer);
            }
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[ResourceObserver] Failed to write external selection cache", e);
        }
    }

    private static void trimOldestEntries() {
        while (ENTRIES.size() > MAX_ENTRIES) {
            String oldestKey = null;
            long oldestTs = Long.MAX_VALUE;
            for (Map.Entry<String, CacheEntry> e : ENTRIES.entrySet()) {
                long ts = e.getValue() == null ? 0L : e.getValue().updatedAt;
                if (ts < oldestTs) {
                    oldestTs = ts;
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            ENTRIES.remove(oldestKey);
        }
    }

    private static LinkedHashSet<String> sanitizeIds(@Nullable Iterable<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source == null) {
            return result;
        }
        for (String id : source) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalized = id.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
            if (result.size() >= MAX_GROUP_IDS_PER_ENTRY) {
                break;
            }
        }
        return result;
    }

    /**
     * 构造按观察者位置 + 维度 + 服务器/世界标识的缓存键，与 ModernUI 实现保持一致。
     */
    public static String buildKey(@Nullable BlockPos observerPos) {
        if (observerPos == null) return "";
        Minecraft mc = Minecraft.getInstance();
        String sessionId = "unknown";
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            sessionId = "server:" + serverData.ip.trim().toLowerCase(Locale.ROOT);
        } else if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            String levelName = mc.getSingleplayerServer().getWorldData().getLevelName();
            sessionId = (levelName != null && !levelName.isBlank())
                    ? "singleplayer:" + levelName.trim().toLowerCase(Locale.ROOT)
                    : "singleplayer";
        }
        String dimension = mc.level != null
                ? mc.level.dimension().location().toString()
                : "unknown";
        return sessionId + "|" + dimension + "|"
                + observerPos.getX() + "," + observerPos.getY() + "," + observerPos.getZ();
    }

    private static final class CacheFile {
        private int version = CACHE_VERSION;
        private Map<String, CacheEntry> entries = new LinkedHashMap<>();
    }

    private static final class CacheEntry {
        private List<String> selectedExternalGroupIds = new ArrayList<>();
        private long updatedAt = System.currentTimeMillis();
    }
}

