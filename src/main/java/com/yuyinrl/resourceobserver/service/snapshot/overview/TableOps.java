package com.yuyinrl.resourceobserver.service.snapshot.overview;

import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 表格过滤 / 排序 / 分组 / 关注列表的纯逻辑工具集。
 *
 * <p>F1.A.5 从 {@code OverviewViewModelMapper} 提取的 6 个表格相关方法
 * （matchesStatus / matchesGroup / comparatorFor / isCritical /
 * applyFiltersAndSort / groupRows / buildWatchlist），剥离 i18n 字符串拼装，
 * 改为操作 {@link OverviewSnapshot.TableRowSnapshot} 与
 * {@link OverviewSnapshot.GroupOptionSnapshot}，全部纯函数。
 *
 * <p>分组键约定（与 {@code PlayerUiPrefsSavedData} 镜像保持一致）：
 * <ul>
 *     <li>{@link #GROUP_FILTER_ALL} = "all" —— 不按分组筛选</li>
 *     <li>{@link #GROUP_UNGROUPED}  = "ungrouped" —— 默认未分组桶</li>
 * </ul>
 *
 * <p>临界判定阈值：{@link #CRITICAL_STOCK_THRESHOLD} = 64
 * （净消耗且库存 ≤ 64 视为严重亏损）。
 */
public final class TableOps {

    /** 分组筛选键："显示全部分组" */
    public static final String GROUP_FILTER_ALL = "all";

    /** 默认未分组桶 */
    public static final String GROUP_UNGROUPED = "ungrouped";

    /** 严重亏损阈值（净消耗且库存 ≤ 64） */
    public static final long CRITICAL_STOCK_THRESHOLD = 64L;

    private TableOps() {
    }

    // ===== 状态判定 =====

    /** 判断行是否处于严重亏损（净消耗且库存极低）。 */
    public static boolean isCritical(long net, long stock) {
        if (net >= 0L) {
            return false;
        }
        return stock <= CRITICAL_STOCK_THRESHOLD;
    }

    /** 行是否匹配状态筛选。 */
    public static boolean matchesStatus(OverviewSnapshot.TableRowSnapshot row, TableStatusFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case SURPLUS -> row.net() > 0L;
            case DEFICIT -> row.net() < 0L;
            case CRITICAL -> row.critical();
        };
    }

    /** 行是否匹配分组筛选；空 / null / ALL 均视为命中。 */
    public static boolean matchesGroup(OverviewSnapshot.TableRowSnapshot row, String groupFilterKey) {
        if (groupFilterKey == null || groupFilterKey.isBlank()
                || GROUP_FILTER_ALL.equals(groupFilterKey)) {
            return true;
        }
        return groupFilterKey.equals(row.groupKey());
    }

    /**
     * 标准化分组键：null / 空白 → fallback；其他原样返回。
     * 与 {@code FormatUtils.normalizeGroupKey} 同构，独立维护以解除模块依赖。
     */
    public static String normalizeGroupKey(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    // ===== 排序 =====

    /**
     * 按指定模式生成行比较器：主排序按 sortMode 字段，副排序按 displayName 字母序。
     * NET_ABS 兼容旧版本，行为同 NET。
     */
    public static Comparator<OverviewSnapshot.TableRowSnapshot> comparatorFor(
            TableSortMode sortMode, boolean sortDesc) {
        Comparator<OverviewSnapshot.TableRowSnapshot> comparator = switch (sortMode) {
            case NET_ABS, NET ->
                    Comparator.comparingLong(OverviewSnapshot.TableRowSnapshot::net);
            case PRODUCTION ->
                    Comparator.comparingLong(OverviewSnapshot.TableRowSnapshot::production);
            case CONSUMPTION ->
                    Comparator.comparingLong(OverviewSnapshot.TableRowSnapshot::consumption);
            case STOCK ->
                    Comparator.comparingLong(OverviewSnapshot.TableRowSnapshot::stock);
        };
        if (sortDesc) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(OverviewSnapshot.TableRowSnapshot::displayName);
    }

    // ===== 过滤 + 排序 + 分组 =====

    /**
     * 按状态 / 分组 / 自定义搜索过滤行，再按 sortMode 排序。
     *
     * @param searchPredicate 可选搜索过滤器；null 表示不启用。
     *                        典型实现使用拼音/子串匹配，由调用方注入以解除 MC 依赖。
     */
    public static List<OverviewSnapshot.TableRowSnapshot> applyFiltersAndSort(
            List<OverviewSnapshot.TableRowSnapshot> rows,
            TableStatusFilter statusFilter,
            String groupFilterKey,
            TableSortMode sortMode,
            boolean sortDesc,
            Predicate<OverviewSnapshot.TableRowSnapshot> searchPredicate
    ) {
        List<OverviewSnapshot.TableRowSnapshot> filtered = new ArrayList<>();
        for (OverviewSnapshot.TableRowSnapshot row : rows) {
            if (!matchesStatus(row, statusFilter)) {
                continue;
            }
            if (!matchesGroup(row, groupFilterKey)) {
                continue;
            }
            if (searchPredicate != null && !searchPredicate.test(row)) {
                continue;
            }
            filtered.add(row);
        }
        filtered.sort(comparatorFor(sortMode, sortDesc));
        return filtered;
    }

    /**
     * 把过滤排序后的行按分组归类。
     * <ul>
     *     <li>filter == ALL：遍历 groupOptions 顺序，仅返回非空分组；
     *         若全部为空则返回单一 ungrouped 占位（标题取自 options 中的 UNGROUPED 显示名，
     *         否则回落到 {@link #GROUP_UNGROUPED} 字面量）。</li>
     *     <li>filter ≠ ALL：返回单一分组（标题来自 options 中匹配项，否则用 key 本身）。</li>
     * </ul>
     */
    public static List<OverviewSnapshot.TableGroupSnapshot> groupRows(
            List<OverviewSnapshot.TableRowSnapshot> rows,
            String groupFilterKey,
            List<OverviewSnapshot.GroupOptionSnapshot> groupOptions
    ) {
        LinkedHashMap<String, List<OverviewSnapshot.TableRowSnapshot>> grouped = new LinkedHashMap<>();
        for (OverviewSnapshot.GroupOptionSnapshot option : groupOptions) {
            grouped.put(option.key(), new ArrayList<>());
        }
        for (OverviewSnapshot.TableRowSnapshot row : rows) {
            String key = normalizeGroupKey(row.groupKey(), GROUP_UNGROUPED);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        List<OverviewSnapshot.TableGroupSnapshot> result = new ArrayList<>();
        if (GROUP_FILTER_ALL.equals(groupFilterKey)) {
            for (OverviewSnapshot.GroupOptionSnapshot option : groupOptions) {
                List<OverviewSnapshot.TableRowSnapshot> bucket = grouped.getOrDefault(option.key(), List.of());
                if (bucket.isEmpty()) {
                    continue;
                }
                result.add(new OverviewSnapshot.TableGroupSnapshot(
                        option.key(), option.displayName(), bucket));
            }
            if (result.isEmpty()) {
                String ungroupedTitle = findGroupDisplayName(groupOptions, GROUP_UNGROUPED, GROUP_UNGROUPED);
                result.add(new OverviewSnapshot.TableGroupSnapshot(
                        GROUP_UNGROUPED, ungroupedTitle, List.of()));
            }
            return result;
        }

        String key = normalizeGroupKey(groupFilterKey, GROUP_FILTER_ALL);
        String title = findGroupDisplayName(groupOptions, key, key);
        result.add(new OverviewSnapshot.TableGroupSnapshot(
                key, title, grouped.getOrDefault(key, List.of())));
        return result;
    }

    private static String findGroupDisplayName(
            List<OverviewSnapshot.GroupOptionSnapshot> options, String key, String fallback) {
        for (OverviewSnapshot.GroupOptionSnapshot option : options) {
            if (option.key().equals(key)) {
                return option.displayName();
            }
        }
        return fallback;
    }

    // ===== 关注列表 =====

    /**
     * 按 watchlistItemIds 顺序从已展开的行中提取关注列表条目，
     * 仅保留行存在的物品（缺失项静默丢弃）。
     */
    public static List<OverviewSnapshot.WatchlistItemSnapshot> buildWatchlist(
            List<OverviewSnapshot.TableRowSnapshot> rows,
            List<String> watchlistItemIds
    ) {
        if (watchlistItemIds == null || watchlistItemIds.isEmpty() || rows.isEmpty()) {
            return List.of();
        }
        Map<String, OverviewSnapshot.TableRowSnapshot> rowById = new HashMap<>();
        for (OverviewSnapshot.TableRowSnapshot row : rows) {
            rowById.putIfAbsent(row.itemId(), row);
        }

        List<OverviewSnapshot.WatchlistItemSnapshot> watch = new ArrayList<>();
        for (String itemId : watchlistItemIds) {
            OverviewSnapshot.TableRowSnapshot row = rowById.get(itemId);
            if (row == null) {
                continue;
            }
            watch.add(new OverviewSnapshot.WatchlistItemSnapshot(
                    row.itemId(),
                    row.displayName(),
                    row.net(),
                    row.stock(),
                    row.iconSprite()
            ));
        }
        return watch;
    }
}
