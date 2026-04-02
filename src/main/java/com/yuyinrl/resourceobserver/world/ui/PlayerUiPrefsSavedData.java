package com.yuyinrl.resourceobserver.world.ui;

import com.yuyinrl.resourceobserver.network.UiActionType;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家 UI 偏好持久化存储（SavedData）—— 保存每个玩家的终端界面设置。
 * <p>
 * 每个玩家独立存储以下偏好数据：
 * - 关注列表（watchlist）：最多 WATCHLIST_LIMIT(12) 个物品，使用 LinkedHashSet 保持插入顺序
 * - 自定义分组（groups）：玩家创建的物品分类，含一个系统"未分组"分组
 * - 物品-分组映射（itemGroupMap）：记录每个物品所属的分组
 * - 表格筛选/排序设置：分组筛选键、排序模式、排序方向、状态筛选
 * <p>
 * 数据存储于主世界的 DataStorage，跨维度共享。
 * 所有操作通过 applyAction() 统一入口处理。
 */
public class PlayerUiPrefsSavedData extends SavedData {
    /** 关注列表最大容量 */
    public static final int WATCHLIST_LIMIT = 12;
    /** 分组筛选键 "all" 表示不筛选 */
    public static final String GROUP_FILTER_ALL = "all";
    /** 系统默认"未分组"分组的键名 */
    public static final String GROUP_UNGROUPED = "ungrouped";

    // ========== NBT 标签常量 ==========
    private static final String DATA_NAME = "resourceobserver_ui_prefs";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER_ID = "player_id";
    private static final String TAG_WATCHLIST = "watchlist";
    private static final String TAG_ITEM_ID = "item_id";
    private static final String TAG_GROUP_FILTER_KEY = "group_filter_key";
    private static final String TAG_SORT_MODE = "sort_mode";
    private static final String TAG_SORT_DESC = "sort_desc";
    private static final String TAG_STATUS_FILTER = "status_filter";
    private static final String TAG_GROUPS = "groups";
    private static final String TAG_GROUP_KEY = "group_key";
    private static final String TAG_GROUP_NAME = "group_name";
    private static final String TAG_GROUP_SYSTEM = "group_system";
    private static final String TAG_ITEM_GROUPS = "item_groups";
    /** 分组名称最大长度 */
    private static final int GROUP_NAME_MAX_LEN = 24;

    private static final Factory<PlayerUiPrefsSavedData> FACTORY =
            new Factory<>(PlayerUiPrefsSavedData::new, PlayerUiPrefsSavedData::load);

    /** 所有玩家的偏好数据映射（玩家 UUID → PlayerPrefs） */
    private final Map<UUID, PlayerPrefs> players = new HashMap<>();

    /** 获取当前世界的玩家偏好实例（始终存储在主世界） */
    public static PlayerUiPrefsSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** 获取指定玩家的偏好快照（不可变副本），玩家无数据时返回默认值 */
    public PlayerUiPrefsSnapshot getSnapshot(UUID playerId) {
        PlayerPrefs prefs = players.get(playerId);
        if (prefs == null) {
            return PlayerUiPrefsSnapshot.defaults();
        }
        return prefs.snapshot();
    }

    /**
     * 应用 UI 操作到指定玩家的偏好数据。
     * 根据 actionType 分发到对应的处理方法，操作成功后标记数据已修改。
     * @return 操作结果（是否修改、是否成功、失败消息键）
     */
    public ActionResult applyAction(UUID playerId, UiActionType actionType, String itemId, List<String> itemIds, String actionValue) {
        PlayerPrefs prefs = players.computeIfAbsent(playerId, ignored -> PlayerPrefs.defaults());
        List<String> targets = PlayerPrefs.mergeTargetItems(itemId, itemIds);
        // 根据操作类型分发处理
        ActionResult result = switch (actionType) {
            case TOGGLE_WATCH -> prefs.toggleWatch(itemId);          // 切换关注
            case SET_GROUP_FILTER_KEY -> prefs.setGroupFilterKey(actionValue); // 设置分组筛选
            case SET_SORT_MODE -> prefs.setSortMode(TableSortMode.fromKey(actionValue)); // 设置排序模式
            case SET_STATUS_FILTER -> prefs.setStatusFilter(TableStatusFilter.fromKey(actionValue)); // 设置状态筛选
            case RESET_FILTERS -> prefs.resetFilters();              // 重置所有筛选
            case ASSIGN_ITEM_GROUP -> prefs.assignItemGroup(targets, actionValue); // 分配分组
            case CREATE_GROUP -> prefs.createGroup(actionValue, targets);     // 创建分组
            case RENAME_GROUP -> prefs.renameGroup(itemId, actionValue);      // 重命名分组
            case DELETE_GROUP -> prefs.deleteGroup(itemId);          // 删除分组
            case CLEAR_ITEM_GROUP -> prefs.clearItemGroup(targets);  // 清除分组
        };
        if (result.changed()) {
            setDirty(); // 标记数据已修改，触发自动保存
        }
        return result;
    }

    /** NBT 保存 —— 序列化所有玩家的偏好数据 */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, PlayerPrefs> entry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER_ID, entry.getKey());

            ListTag watchlist = new ListTag();
            for (String itemId : entry.getValue().watchlistItemIds) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString(TAG_ITEM_ID, itemId);
                watchlist.add(itemTag);
            }
            playerTag.put(TAG_WATCHLIST, watchlist);

            ListTag groups = new ListTag();
            for (GroupDefinition group : entry.getValue().groups.values()) {
                CompoundTag groupTag = new CompoundTag();
                groupTag.putString(TAG_GROUP_KEY, group.key());
                groupTag.putString(TAG_GROUP_NAME, group.displayName());
                groupTag.putBoolean(TAG_GROUP_SYSTEM, group.systemGroup());
                groups.add(groupTag);
            }
            playerTag.put(TAG_GROUPS, groups);

            ListTag itemGroups = new ListTag();
            for (Map.Entry<String, String> itemGroupEntry : entry.getValue().itemGroupMap.entrySet()) {
                CompoundTag mapTag = new CompoundTag();
                mapTag.putString(TAG_ITEM_ID, itemGroupEntry.getKey());
                mapTag.putString(TAG_GROUP_KEY, itemGroupEntry.getValue());
                itemGroups.add(mapTag);
            }
            playerTag.put(TAG_ITEM_GROUPS, itemGroups);

            playerTag.putString(TAG_GROUP_FILTER_KEY, entry.getValue().groupFilterKey);
            playerTag.putString(TAG_SORT_MODE, entry.getValue().sortMode.key());
            playerTag.putBoolean(TAG_SORT_DESC, entry.getValue().sortDesc);
            playerTag.putString(TAG_STATUS_FILTER, entry.getValue().statusFilter.key());
            playersTag.add(playerTag);
        }
        tag.put(TAG_PLAYERS, playersTag);
        return tag;
    }

    /** NBT 加载 —— 反序列化恢复所有玩家的偏好数据 */
    private static PlayerUiPrefsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerUiPrefsSavedData data = new PlayerUiPrefsSavedData();
        if (!tag.contains(TAG_PLAYERS, Tag.TAG_LIST)) {
            return data;
        }

        ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            if (!playerTag.hasUUID(TAG_PLAYER_ID)) {
                continue;
            }
            UUID playerId = playerTag.getUUID(TAG_PLAYER_ID);
            PlayerPrefs prefs = PlayerPrefs.defaults();

            if (playerTag.contains(TAG_WATCHLIST, Tag.TAG_LIST)) {
                ListTag watchlist = playerTag.getList(TAG_WATCHLIST, Tag.TAG_COMPOUND);
                for (int j = 0; j < watchlist.size(); j++) {
                    String itemId = watchlist.getCompound(j).getString(TAG_ITEM_ID);
                    if (!itemId.isBlank()) {
                        prefs.watchlistItemIds.add(itemId);
                    }
                }
                trimWatchlist(prefs.watchlistItemIds);
            }

            if (playerTag.contains(TAG_GROUPS, Tag.TAG_LIST)) {
                prefs.groups.clear();
                ListTag groups = playerTag.getList(TAG_GROUPS, Tag.TAG_COMPOUND);
                for (int j = 0; j < groups.size(); j++) {
                    CompoundTag groupTag = groups.getCompound(j);
                    String key = normalizeGroupKey(groupTag.getString(TAG_GROUP_KEY));
                    String name = normalizeGroupName(groupTag.getString(TAG_GROUP_NAME));
                    boolean system = groupTag.getBoolean(TAG_GROUP_SYSTEM);
                    if (key.isBlank() || name.isBlank()) {
                        continue;
                    }
                    if (GROUP_FILTER_ALL.equals(key)) {
                        continue;
                    }
                    if (GROUP_UNGROUPED.equals(key)) {
                        system = true;
                        name = defaultUngroupedName();
                    }
                    prefs.groups.put(key, new GroupDefinition(key, name, system));
                }
            }
            prefs.ensureSystemGroups();

            if (playerTag.contains(TAG_ITEM_GROUPS, Tag.TAG_LIST)) {
                ListTag itemGroups = playerTag.getList(TAG_ITEM_GROUPS, Tag.TAG_COMPOUND);
                for (int j = 0; j < itemGroups.size(); j++) {
                    CompoundTag mapTag = itemGroups.getCompound(j);
                    String itemId = mapTag.getString(TAG_ITEM_ID);
                    String groupKey = normalizeGroupKey(mapTag.getString(TAG_GROUP_KEY));
                    if (itemId.isBlank() || groupKey.isBlank()) {
                        continue;
                    }
                    if (GROUP_UNGROUPED.equals(groupKey)) {
                        continue;
                    }
                    if (prefs.groups.containsKey(groupKey)) {
                        prefs.itemGroupMap.put(itemId, groupKey);
                    }
                }
            }

            prefs.groupFilterKey = sanitizeGroupFilterKey(playerTag.getString(TAG_GROUP_FILTER_KEY), prefs.groups);
            prefs.sortMode = TableSortMode.fromKey(playerTag.getString(TAG_SORT_MODE));
            prefs.sortDesc = !playerTag.contains(TAG_SORT_DESC, Tag.TAG_BYTE) || playerTag.getBoolean(TAG_SORT_DESC);
            prefs.statusFilter = TableStatusFilter.fromKey(playerTag.getString(TAG_STATUS_FILTER));
            data.players.put(playerId, prefs);
        }
        return data;
    }

    /** 裁剪关注列表至最大容量 */
    private static void trimWatchlist(LinkedHashSet<String> watchlist) {
        if (watchlist.size() <= WATCHLIST_LIMIT) {
            return;
        }
        List<String> ids = new ArrayList<>(watchlist);
        watchlist.clear();
        for (int i = 0; i < WATCHLIST_LIMIT && i < ids.size(); i++) {
            watchlist.add(ids.get(i));
        }
    }

    /** 返回"未分组"分组的默认显示名称 */
    private static String defaultUngroupedName() {
        return "Ungrouped";
    }

    /** 验证分组筛选键有效性，无效键回退到 ALL */
    private static String sanitizeGroupFilterKey(String key, Map<String, GroupDefinition> groups) {
        String normalized = normalizeGroupKey(key);
        if (normalized.isBlank() || GROUP_FILTER_ALL.equals(normalized)) {
            return GROUP_FILTER_ALL;
        }
        return groups.containsKey(normalized) ? normalized : GROUP_FILTER_ALL;
    }

    /** 规范化分组显示名称（trim + 长度截断） */
    private static String normalizeGroupName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > GROUP_NAME_MAX_LEN) {
            return trimmed.substring(0, GROUP_NAME_MAX_LEN);
        }
        return trimmed;
    }

    /**
     * 规范化分组键名 —— 将任意字符串转为小写字母+数字+下划线格式。
     * 移除特殊字符，合并连续下划线，去除首尾下划线。
     */
    private static String normalizeGroupKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        boolean lastUnderscore = false;
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                sb.append(ch);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        String key = sb.toString();
        while (key.startsWith("_")) {
            key = key.substring(1);
        }
        while (key.endsWith("_")) {
            key = key.substring(0, key.length() - 1);
        }
        return key;
    }

    /**
     * 单个玩家的偏好数据（可变内部状态）。
     * 所有修改操作返回 ActionResult 以支持统一的成功/失败处理。
     */
    private static final class PlayerPrefs {
        /** 关注列表物品 ID（有序去重集合） */
        private final LinkedHashSet<String> watchlistItemIds = new LinkedHashSet<>();
        /** 自定义分组定义（保持插入顺序） */
        private final LinkedHashMap<String, GroupDefinition> groups = new LinkedHashMap<>();
        /** 物品到分组的映射（itemId → groupKey） */
        private final Map<String, String> itemGroupMap = new HashMap<>();
        /** 当前分组筛选键 */
        private String groupFilterKey = GROUP_FILTER_ALL;
        /** 当前排序模式 */
        private TableSortMode sortMode = TableSortMode.NET_ABS;
        /** 是否降序排列 */
        private boolean sortDesc = true;
        /** 当前状态筛选 */
        private TableStatusFilter statusFilter = TableStatusFilter.ALL;

        /** 创建默认偏好（包含系统分组） */
        private static PlayerPrefs defaults() {
            PlayerPrefs prefs = new PlayerPrefs();
            prefs.ensureSystemGroups();
            return prefs;
        }

        /** 确保系统分组（"未分组"）始终存在 */
        private void ensureSystemGroups() {
            if (!groups.containsKey(GROUP_UNGROUPED)) {
                groups.put(GROUP_UNGROUPED, new GroupDefinition(GROUP_UNGROUPED, defaultUngroupedName(), true));
            }
        }

        /** 创建不可变的偏好快照 */
        private PlayerUiPrefsSnapshot snapshot() {
            List<GroupDefinition> orderedGroups = new ArrayList<>(groups.values());
            return new PlayerUiPrefsSnapshot(
                    List.copyOf(watchlistItemIds),
                    groupFilterKey,
                    List.copyOf(orderedGroups),
                    Map.copyOf(itemGroupMap),
                    sortMode,
                    sortDesc,
                    statusFilter
            );
        }

        /**
         * 切换物品关注状态。
         * 已关注则移除，未关注则添加（超出限制时返回失败）。
         */
        private ActionResult toggleWatch(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                return ActionResult.NO_CHANGE;
            }
            if (watchlistItemIds.contains(itemId)) {
                watchlistItemIds.remove(itemId);
                return ActionResult.changedSuccess();
            }
            if (watchlistItemIds.size() >= WATCHLIST_LIMIT) {
                return ActionResult.failed("message.resourceobserver.watchlist_limit_reached");
            }
            watchlistItemIds.add(itemId);
            return ActionResult.changedSuccess();
        }

        /** 设置分组筛选键 */
        private ActionResult setGroupFilterKey(String key) {
            String normalized = sanitizeGroupFilterKey(key, groups);
            if (normalized.equals(groupFilterKey)) {
                return ActionResult.NO_CHANGE;
            }
            groupFilterKey = normalized;
            return ActionResult.changedSuccess();
        }

        /**
         * 设置排序模式。
         * 如果模式相同，切换排序方向（升序/降序）；
         * 如果模式不同，设为新模式并默认降序。
         */
        private ActionResult setSortMode(TableSortMode mode) {
            if (sortMode == mode) {
                sortDesc = !sortDesc;
                return ActionResult.changedSuccess();
            }
            sortMode = mode;
            sortDesc = true;
            return ActionResult.changedSuccess();
        }

        /** 设置状态筛选 */
        private ActionResult setStatusFilter(TableStatusFilter filter) {
            if (statusFilter == filter) {
                return ActionResult.NO_CHANGE;
            }
            statusFilter = filter;
            return ActionResult.changedSuccess();
        }

        /** 重置所有筛选条件为默认值 */
        private ActionResult resetFilters() {
            boolean changed = !GROUP_FILTER_ALL.equals(groupFilterKey)
                    || sortMode != TableSortMode.NET_ABS
                    || !sortDesc
                    || statusFilter != TableStatusFilter.ALL;
            groupFilterKey = GROUP_FILTER_ALL;
            sortMode = TableSortMode.NET_ABS;
            sortDesc = true;
            statusFilter = TableStatusFilter.ALL;
            return changed ? ActionResult.changedSuccess() : ActionResult.NO_CHANGE;
        }

        /** 合并单个 itemId 和 itemIds 列表为去重列表 */
        private static List<String> mergeTargetItems(String itemId, List<String> itemIds) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            if (itemId != null && !itemId.isBlank()) {
                result.add(itemId);
            }
            if (itemIds != null) {
                for (String id : itemIds) {
                    if (id != null && !id.isBlank()) {
                        result.add(id);
                    }
                }
            }
            return List.copyOf(result);
        }

        /** 将物品分配到指定分组（分配到"未分组"等同于清除分组） */
        private ActionResult assignItemGroup(List<String> itemIds, String groupKey) {
            if (itemIds == null || itemIds.isEmpty()) {
                return ActionResult.NO_CHANGE;
            }
            String normalizedKey = normalizeGroupKey(groupKey);
            if (normalizedKey.isBlank() || GROUP_UNGROUPED.equals(normalizedKey)) {
                return clearItemGroup(itemIds);
            }
            if (!groups.containsKey(normalizedKey)) {
                return ActionResult.failed("message.resourceobserver.group_not_found");
            }
            boolean changed = false;
            for (String itemId : itemIds) {
                String old = itemGroupMap.put(itemId, normalizedKey);
                if (!normalizedKey.equals(old)) {
                    changed = true;
                }
            }
            return changed ? ActionResult.changedSuccess() : ActionResult.NO_CHANGE;
        }

        /** 清除物品的分组分配（物品回到"未分组"状态） */
        private ActionResult clearItemGroup(List<String> itemIds) {
            if (itemIds == null || itemIds.isEmpty()) {
                return ActionResult.NO_CHANGE;
            }
            boolean changed = false;
            for (String itemId : itemIds) {
                if (itemGroupMap.remove(itemId) != null) {
                    changed = true;
                }
            }
            return changed ? ActionResult.changedSuccess() : ActionResult.NO_CHANGE;
        }

        /**
         * 创建新的自定义分组。
         * 验证名称非空和唯一性，自动生成键名，可选地将物品分配到新分组。
         */
        private ActionResult createGroup(String rawName, List<String> itemIdsToAssign) {
            String name = normalizeGroupName(rawName);
            if (name.isBlank()) {
                return ActionResult.failed("message.resourceobserver.group_name_required");
            }
            if (groupNameExists(name, "")) {
                return ActionResult.failed("message.resourceobserver.group_name_exists");
            }

            String baseKey = normalizeGroupKey(name);
            if (baseKey.isBlank()) {
                baseKey = "group";
            }
            String key = uniqueGroupKey(baseKey);
            groups.put(key, new GroupDefinition(key, name, false));
            if (itemIdsToAssign != null) {
                for (String itemId : itemIdsToAssign) {
                    if (itemId == null || itemId.isBlank()) {
                        continue;
                    }
                    itemGroupMap.put(itemId, key);
                }
            }
            return ActionResult.changedSuccess();
        }

        /** 重命名已有分组（系统分组不可重命名） */
        private ActionResult renameGroup(String groupKey, String newNameRaw) {
            String key = normalizeGroupKey(groupKey);
            GroupDefinition existing = groups.get(key);
            if (existing == null) {
                return ActionResult.failed("message.resourceobserver.group_not_found");
            }
            if (existing.systemGroup()) {
                return ActionResult.failed("message.resourceobserver.group_system_locked");
            }

            String newName = normalizeGroupName(newNameRaw);
            if (newName.isBlank()) {
                return ActionResult.failed("message.resourceobserver.group_name_required");
            }
            if (existing.displayName().equals(newName)) {
                return ActionResult.NO_CHANGE;
            }
            if (groupNameExists(newName, key)) {
                return ActionResult.failed("message.resourceobserver.group_name_exists");
            }

            groups.put(key, new GroupDefinition(key, newName, false));
            return ActionResult.changedSuccess();
        }

        /**
         * 删除自定义分组。
         * 系统分组不可删除。删除后清除该分组的所有物品映射。
         * 如果当前筛选键为该分组，重置为 ALL。
         */
        private ActionResult deleteGroup(String groupKey) {
            String key = normalizeGroupKey(groupKey);
            GroupDefinition existing = groups.get(key);
            if (existing == null) {
                return ActionResult.failed("message.resourceobserver.group_not_found");
            }
            if (existing.systemGroup()) {
                return ActionResult.failed("message.resourceobserver.group_system_locked");
            }

            groups.remove(key);
            itemGroupMap.entrySet().removeIf(entry -> key.equals(entry.getValue()));
            if (key.equals(groupFilterKey)) {
                groupFilterKey = GROUP_FILTER_ALL;
            }
            return ActionResult.changedSuccess();
        }

        /** 检查分组名称是否已存在（忽略大小写），可排除指定键名 */
        private boolean groupNameExists(String name, String exceptKey) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (GroupDefinition group : groups.values()) {
                if (!exceptKey.isBlank() && group.key().equals(exceptKey)) {
                    continue;
                }
                if (group.displayName().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return true;
                }
            }
            return false;
        }

        /** 生成唯一的分组键名（冲突时追加递增后缀） */
        private String uniqueGroupKey(String baseKey) {
            String key = baseKey;
            int n = 2;
            while (groups.containsKey(key) || GROUP_FILTER_ALL.equals(key)) {
                key = baseKey + "_" + n++;
            }
            return key;
        }
    }

    /**
     * 分组定义记录 —— 描述一个物品分组。
     * @param key         分组唯一键（小写字母+数字+下划线）
     * @param displayName 分组显示名称
     * @param systemGroup 是否为系统内置分组（不可删除/重命名）
     */
    public record GroupDefinition(String key, String displayName, boolean systemGroup) {
    }

    /**
     * 玩家 UI 偏好快照（不可变）—— 用于跨线程安全传递数据。
     */
    public record PlayerUiPrefsSnapshot(
            List<String> watchlistItemIds,
            String groupFilterKey,
            List<GroupDefinition> groups,
            Map<String, String> itemGroupMap,
            TableSortMode sortMode,
            boolean sortDesc,
            TableStatusFilter statusFilter
    ) {
        /** 返回默认偏好快照 */
        public static PlayerUiPrefsSnapshot defaults() {
            return new PlayerUiPrefsSnapshot(
                    List.of(),
                    GROUP_FILTER_ALL,
                    List.of(new GroupDefinition(GROUP_UNGROUPED, defaultUngroupedName(), true)),
                    Map.of(),
                    TableSortMode.NET_ABS,
                    true,
                    TableStatusFilter.ALL
            );
        }

        /** 查询物品所属的分组键，未分配则返回 "ungrouped" */
        public String groupKeyForItem(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                return GROUP_UNGROUPED;
            }
            String key = itemGroupMap.get(itemId);
            return key == null || key.isBlank() ? GROUP_UNGROUPED : key;
        }
    }

    /**
     * UI 操作结果记录。
     * @param changed        数据是否被修改
     * @param success        操作是否成功
     * @param failMessageKey 失败时的本地化消息键（空字符串表示无消息）
     */
    public record ActionResult(boolean changed, boolean success, String failMessageKey) {
        /** 未修改的成功结果 */
        private static final ActionResult NO_CHANGE = new ActionResult(false, true, "");

        /** 已修改的成功结果 */
        public static ActionResult changedSuccess() {
            return new ActionResult(true, true, "");
        }

        /** 失败结果，附带错误消息键 */
        public static ActionResult failed(String messageKey) {
            return new ActionResult(false, false, messageKey == null ? "" : messageKey);
        }
    }
}
