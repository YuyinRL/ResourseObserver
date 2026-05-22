package com.yuyinrl.resourceobserver.world.auth;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 每玩家 Web 访问 Token 持久化存储（SavedData）—— 双 Token 模型。
 * <p>
 * <b>Link Token（一次性）</b>：用于游戏内向玩家分发的登录 URL（{@code ?t=...}）。
 * 5 分钟 TTL，被 {@link #consumeLinkToken(String)} 消费一次后立即作废；
 * <b>不持久化</b>，进程重启即清空（强制重新签发）。
 * <p>
 * <b>Session Token（长期）</b>：浏览器调用 {@code POST /api/auth/exchange} 用 link token
 * 换取得到，写入 cookie/localStorage，可重启浏览器继续使用；一个玩家可有多个 session
 * token（多设备）。<b>持久化</b>到 SavedData。
 * <p>
 * 调用方需保证主线程访问。
 */
public final class PlayerWebTokenSavedData extends SavedData {

    private static final String DATA_NAME = "resourceobserver_web_tokens";

    private static final String TAG_SESSIONS = "sessions";
    private static final String TAG_PLAYER_ID = "player_id";
    private static final String TAG_TOKEN = "token";
    private static final String TAG_CREATED_AT = "created_at";

    private static final String LINK_PREFIX = "rl_";
    private static final String SESSION_PREFIX = "rs_";
    private static final int TOKEN_BYTES = 16;

    /** Link token 默认 TTL：5 分钟。 */
    public static final long LINK_TTL_MS = 5 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Factory<PlayerWebTokenSavedData> FACTORY =
            new Factory<>(PlayerWebTokenSavedData::new, PlayerWebTokenSavedData::load);

    public static PlayerWebTokenSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Link token → 条目（含玩家、过期时间）；进程内存，不持久化。 */
    private final Map<String, LinkEntry> linkTokens = new HashMap<>();
    /** Session token → 玩家 UUID；持久化。 */
    private final Map<String, UUID> sessionTokens = new HashMap<>();
    /** 玩家 → 其所有 session token；从 sessionTokens 派生，仅供 revokeAll 使用。 */
    private final Map<UUID, Set<String>> playerSessions = new HashMap<>();

    /**
     * 签发一个全新的一次性 link token；调用前应清理过期项。
     */
    public String issueLinkToken(UUID playerId) {
        purgeExpiredLinks();
        String token = generateToken(LINK_PREFIX);
        long now = System.currentTimeMillis();
        linkTokens.put(token, new LinkEntry(playerId, now + LINK_TTL_MS));
        return token;
    }

    /**
     * 消费一个 link token —— 一次性：成功返回玩家 UUID，并立即从表中删除。
     * <p>过期 / 不存在 / 已被消费 → 返回空。</p>
     */
    public Optional<UUID> consumeLinkToken(@Nullable String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        purgeExpiredLinks();
        LinkEntry entry = linkTokens.remove(token);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(entry.playerId);
    }

    /** 签发新的长期 session token（持久化）。 */
    public String issueSessionToken(UUID playerId) {
        String token = generateToken(SESSION_PREFIX);
        sessionTokens.put(token, playerId);
        playerSessions.computeIfAbsent(playerId, k -> new HashSet<>()).add(token);
        setDirty();
        return token;
    }

    /** 由 session token 反查玩家 UUID（HTTP 鉴权主路径）。 */
    public Optional<UUID> resolveSession(@Nullable String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        UUID id = sessionTokens.get(token);
        return Optional.ofNullable(id);
    }

    /** 吊销玩家所有 session token —— 强制其它设备下线（regenerate 时调用）。 */
    public void revokeAllSessions(UUID playerId) {
        Set<String> set = playerSessions.remove(playerId);
        if (set == null || set.isEmpty()) return;
        for (String t : set) sessionTokens.remove(t);
        setDirty();
    }

    /** 清空已过期的 link tokens（不影响 session）。 */
    private void purgeExpiredLinks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, LinkEntry>> it = linkTokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt < now) it.remove();
        }
    }

    public int sessionCount() {
        return sessionTokens.size();
    }

    public int linkTokenCount() {
        return linkTokens.size();
    }

    private static String generateToken(String prefix) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, UUID> e : sessionTokens.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID(TAG_PLAYER_ID, e.getValue());
            t.putString(TAG_TOKEN, e.getKey());
            t.putLong(TAG_CREATED_AT, now);
            list.add(t);
        }
        tag.put(TAG_SESSIONS, list);
        return tag;
    }

    private static PlayerWebTokenSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerWebTokenSavedData data = new PlayerWebTokenSavedData();
        if (tag.contains(TAG_SESSIONS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_SESSIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                UUID id = t.getUUID(TAG_PLAYER_ID);
                String token = t.getString(TAG_TOKEN);
                if (token == null || token.isBlank()) continue;
                data.sessionTokens.put(token, id);
                data.playerSessions.computeIfAbsent(id, k -> new HashSet<>()).add(token);
            }
        }
        // 兼容旧格式：v0.1 时存在 entries 列表，每玩家一个长期 token —— 当作 session token 迁入。
        if (tag.contains("entries", Tag.TAG_LIST)) {
            ListTag legacy = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                CompoundTag t = legacy.getCompound(i);
                UUID id = t.getUUID(TAG_PLAYER_ID);
                String token = t.getString(TAG_TOKEN);
                if (token == null || token.isBlank()) continue;
                data.sessionTokens.putIfAbsent(token, id);
                data.playerSessions.computeIfAbsent(id, k -> new HashSet<>()).add(token);
            }
        }
        return data;
    }

    private static final class LinkEntry {
        final UUID playerId;
        final long expiresAt;

        LinkEntry(UUID playerId, long expiresAt) {
            this.playerId = playerId;
            this.expiresAt = expiresAt;
        }
    }
}
