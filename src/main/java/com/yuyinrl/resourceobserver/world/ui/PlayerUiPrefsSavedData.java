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

public class PlayerUiPrefsSavedData extends SavedData {
    public static final int WATCHLIST_LIMIT = 12;
    public static final String GROUP_FILTER_ALL = "all";
    public static final String GROUP_UNGROUPED = "ungrouped";

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
    private static final int GROUP_NAME_MAX_LEN = 24;

    private static final Factory<PlayerUiPrefsSavedData> FACTORY =
            new Factory<>(PlayerUiPrefsSavedData::new, PlayerUiPrefsSavedData::load);

    private final Map<UUID, PlayerPrefs> players = new HashMap<>();

    public static PlayerUiPrefsSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public PlayerUiPrefsSnapshot getSnapshot(UUID playerId) {
        PlayerPrefs prefs = players.get(playerId);
        if (prefs == null) {
            return PlayerUiPrefsSnapshot.defaults();
        }
        return prefs.snapshot();
    }

    public ActionResult applyAction(UUID playerId, UiActionType actionType, String itemId, List<String> itemIds, String actionValue) {
        PlayerPrefs prefs = players.computeIfAbsent(playerId, ignored -> PlayerPrefs.defaults());
        List<String> targets = PlayerPrefs.mergeTargetItems(itemId, itemIds);
        ActionResult result = switch (actionType) {
            case TOGGLE_WATCH -> prefs.toggleWatch(itemId);
            case SET_GROUP_FILTER_KEY -> prefs.setGroupFilterKey(actionValue);
            case SET_SORT_MODE -> prefs.setSortMode(TableSortMode.fromKey(actionValue));
            case SET_STATUS_FILTER -> prefs.setStatusFilter(TableStatusFilter.fromKey(actionValue));
            case RESET_FILTERS -> prefs.resetFilters();
            case ASSIGN_ITEM_GROUP -> prefs.assignItemGroup(targets, actionValue);
            case CREATE_GROUP -> prefs.createGroup(actionValue, targets);
            case RENAME_GROUP -> prefs.renameGroup(itemId, actionValue);
            case DELETE_GROUP -> prefs.deleteGroup(itemId);
            case CLEAR_ITEM_GROUP -> prefs.clearItemGroup(targets);
        };
        if (result.changed()) {
            setDirty();
        }
        return result;
    }

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

    private static String defaultUngroupedName() {
        return "Ungrouped";
    }

    private static String sanitizeGroupFilterKey(String key, Map<String, GroupDefinition> groups) {
        String normalized = normalizeGroupKey(key);
        if (normalized.isBlank() || GROUP_FILTER_ALL.equals(normalized)) {
            return GROUP_FILTER_ALL;
        }
        return groups.containsKey(normalized) ? normalized : GROUP_FILTER_ALL;
    }

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

    private static final class PlayerPrefs {
        private final LinkedHashSet<String> watchlistItemIds = new LinkedHashSet<>();
        private final LinkedHashMap<String, GroupDefinition> groups = new LinkedHashMap<>();
        private final Map<String, String> itemGroupMap = new HashMap<>();
        private String groupFilterKey = GROUP_FILTER_ALL;
        private TableSortMode sortMode = TableSortMode.NET_ABS;
        private boolean sortDesc = true;
        private TableStatusFilter statusFilter = TableStatusFilter.ALL;

        private static PlayerPrefs defaults() {
            PlayerPrefs prefs = new PlayerPrefs();
            prefs.ensureSystemGroups();
            return prefs;
        }

        private void ensureSystemGroups() {
            if (!groups.containsKey(GROUP_UNGROUPED)) {
                groups.put(GROUP_UNGROUPED, new GroupDefinition(GROUP_UNGROUPED, defaultUngroupedName(), true));
            }
        }

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

        private ActionResult setGroupFilterKey(String key) {
            String normalized = sanitizeGroupFilterKey(key, groups);
            if (normalized.equals(groupFilterKey)) {
                return ActionResult.NO_CHANGE;
            }
            groupFilterKey = normalized;
            return ActionResult.changedSuccess();
        }

        private ActionResult setSortMode(TableSortMode mode) {
            if (sortMode == mode) {
                sortDesc = !sortDesc;
                return ActionResult.changedSuccess();
            }
            sortMode = mode;
            sortDesc = true;
            return ActionResult.changedSuccess();
        }

        private ActionResult setStatusFilter(TableStatusFilter filter) {
            if (statusFilter == filter) {
                return ActionResult.NO_CHANGE;
            }
            statusFilter = filter;
            return ActionResult.changedSuccess();
        }

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

        private String uniqueGroupKey(String baseKey) {
            String key = baseKey;
            int n = 2;
            while (groups.containsKey(key) || GROUP_FILTER_ALL.equals(key)) {
                key = baseKey + "_" + n++;
            }
            return key;
        }
    }

    public record GroupDefinition(String key, String displayName, boolean systemGroup) {
    }

    public record PlayerUiPrefsSnapshot(
            List<String> watchlistItemIds,
            String groupFilterKey,
            List<GroupDefinition> groups,
            Map<String, String> itemGroupMap,
            TableSortMode sortMode,
            boolean sortDesc,
            TableStatusFilter statusFilter
    ) {
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

        public String groupKeyForItem(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                return GROUP_UNGROUPED;
            }
            String key = itemGroupMap.get(itemId);
            return key == null || key.isBlank() ? GROUP_UNGROUPED : key;
        }
    }

    public record ActionResult(boolean changed, boolean success, String failMessageKey) {
        private static final ActionResult NO_CHANGE = new ActionResult(false, true, "");

        public static ActionResult changedSuccess() {
            return new ActionResult(true, true, "");
        }

        public static ActionResult failed(String messageKey) {
            return new ActionResult(false, false, messageKey == null ? "" : messageKey);
        }
    }
}
