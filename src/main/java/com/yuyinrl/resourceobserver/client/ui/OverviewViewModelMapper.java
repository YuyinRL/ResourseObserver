package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OverviewViewModelMapper {
    private OverviewViewModelMapper() {
    }

    public static OverviewViewModel fromPayload(ObserverDataPayload payload) {
        LinkedHashSet<String> watchlistSet = new LinkedHashSet<>(payload.watchlistItemIds());
        long totalProduced = 0L;
        long totalConsumed = 0L;
        long totalCurrent = 0L;
        long totalCapacity = 0L;

        List<OverviewViewModel.TableRow> allRows = new ArrayList<>();
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            totalProduced += binding.totalProduced();
            totalConsumed += binding.totalConsumed();
            totalCurrent += binding.currentValue();
            totalCapacity += Math.max(0L, binding.capacity());

            for (ObserverDataPayload.ItemDeltaEntry item : binding.itemDeltas()) {
                long production = Math.max(0L, item.delta());
                long consumption = Math.max(0L, -item.delta());
                long net = item.delta();
                long capacity = estimateCapacity(item.amount());
                String localizedName = localizeItemName(item.itemId(), item.displayName());
                allRows.add(new OverviewViewModel.TableRow(
                        item.itemId(),
                        localizedName,
                        normalizeGroupKey(item.groupKey()),
                        production,
                        consumption,
                        net,
                        item.amount(),
                        capacity,
                        isCritical(net, item.amount(), capacity),
                        watchlistSet.contains(item.itemId()),
                        item.iconSprite()
                ));
            }
        }

        if (allRows.isEmpty()) {
            allRows = buildFallbackRows(payload, watchlistSet);
        }

        List<OverviewViewModel.KpiMetric> kpis = buildKpis(totalProduced, totalConsumed, totalCurrent, totalCapacity);
        List<OverviewViewModel.FlowPoint> chartSeries = new ArrayList<>(payload.chartSeries().size());
        for (ObserverDataPayload.ChartPoint point : payload.chartSeries()) {
            chartSeries.add(new OverviewViewModel.FlowPoint(
                    point.slotIndex(),
                    point.production(),
                    point.consumption(),
                    point.net(),
                    point.stock(),
                    point.hasFlow(),
                    point.hasStock()
            ));
        }

        List<OverviewViewModel.GroupOption> groups = buildGroups(payload.groups());
        OverviewViewModel.UiState uiState = new OverviewViewModel.UiState(
                normalizeGroupFilterKey(payload.tableGroupFilterKey()),
                groups,
                payload.tableSortMode(),
                payload.tableSortDesc(),
                payload.tableStatusFilter(),
                payload.watchlistLimit()
        );

        List<OverviewViewModel.WatchlistItem> watchlist = buildWatchlist(allRows, payload.watchlistItemIds());
        List<OverviewViewModel.TableRow> filteredRows = applyFiltersAndSort(
                allRows,
                uiState.statusFilter(),
                uiState.groupFilterKey(),
                uiState.sortMode(),
                uiState.sortDesc()
        );
        List<OverviewViewModel.TableGroup> groupedRows = groupRows(filteredRows, uiState.groupFilterKey(), uiState.groups());

        return new OverviewViewModel(
                "OPERATIONS DASHBOARD",
                "Global monitoring of linked storage networks and resource flow.",
                "LINK ESTABLISHED",
                kpis,
                payload.chartWindow(),
                payload.chartScopeItemId(),
                chartSeries,
                uiState,
                watchlist,
                groupedRows
        );
    }

    private static List<OverviewViewModel.KpiMetric> buildKpis(
            long totalProduced,
            long totalConsumed,
            long totalCurrent,
            long totalCapacity
    ) {
        long net = totalProduced - totalConsumed;
        double fillRatio = totalCapacity > 0 ? (double) totalCurrent / (double) totalCapacity : 0.0;
        double efficiency = totalProduced > 0 ? (double) totalConsumed / (double) totalProduced : 0.0;

        List<OverviewViewModel.KpiMetric> result = new ArrayList<>(4);
        result.add(new OverviewViewModel.KpiMetric(
                "screen.resourceobserver.overview.section.kpi_production",
                formatCompact(totalProduced) + " /min",
                net >= 0 ? "+5.2% vs cycle" : "-2.4% vs cycle",
                net >= 0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.WARNING,
                "resourceobserver:terminal/kpi_production"
        ));
        result.add(new OverviewViewModel.KpiMetric(
                "screen.resourceobserver.overview.section.kpi_consumption",
                formatCompact(totalConsumed) + " /min",
                totalConsumed > totalProduced ? "+2.1% vs cycle" : "-1.3% vs cycle",
                totalConsumed > totalProduced ? OverviewViewModel.Status.WARNING : OverviewViewModel.Status.NEUTRAL,
                "resourceobserver:terminal/kpi_consumption"
        ));
        result.add(new OverviewViewModel.KpiMetric(
                "screen.resourceobserver.overview.section.kpi_storage",
                String.format(Locale.ROOT, "%.1f%%", fillRatio * 100.0),
                formatCompact(totalCurrent) + " / " + formatCompact(totalCapacity),
                fillRatio > 0.9 ? OverviewViewModel.Status.WARNING : OverviewViewModel.Status.POSITIVE,
                "resourceobserver:terminal/kpi_storage"
        ));
        result.add(new OverviewViewModel.KpiMetric(
                "screen.resourceobserver.overview.section.kpi_efficiency",
                String.format(Locale.ROOT, "%.1f%%", Math.max(0.0, (1.0 - Math.max(0.0, efficiency - 1.0)) * 100.0)),
                "Nominal",
                OverviewViewModel.Status.POSITIVE,
                "resourceobserver:terminal/kpi_efficiency"
        ));
        return result;
    }

    private static List<OverviewViewModel.GroupOption> buildGroups(List<ObserverDataPayload.GroupEntry> source) {
        String ungroupedLabel = Component.translatable("screen.resourceobserver.overview.group.ungrouped").getString();
        LinkedHashMap<String, OverviewViewModel.GroupOption> groups = new LinkedHashMap<>();
        groups.put(
                PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                new OverviewViewModel.GroupOption(
                        PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                        ungroupedLabel,
                        true
                )
        );
        for (ObserverDataPayload.GroupEntry group : source) {
            String key = normalizeGroupKey(group.key());
            if (key.isBlank() || PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(key)) {
                continue;
            }
            if (PlayerUiPrefsSavedData.GROUP_UNGROUPED.equals(key)) {
                groups.put(key, new OverviewViewModel.GroupOption(key, ungroupedLabel, true));
            } else {
                String displayName = group.displayName() == null || group.displayName().isBlank()
                        ? key
                        : group.displayName();
                groups.put(key, new OverviewViewModel.GroupOption(key, displayName, group.systemGroup()));
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static List<OverviewViewModel.TableRow> applyFiltersAndSort(
            List<OverviewViewModel.TableRow> rows,
            TableStatusFilter statusFilter,
            String groupFilterKey,
            TableSortMode sortMode,
            boolean sortDesc
    ) {
        List<OverviewViewModel.TableRow> filtered = new ArrayList<>();
        for (OverviewViewModel.TableRow row : rows) {
            if (!matchesStatus(row, statusFilter)) {
                continue;
            }
            if (!matchesGroup(row, groupFilterKey)) {
                continue;
            }
            filtered.add(row);
        }
        filtered.sort(comparatorFor(sortMode, sortDesc));
        return filtered;
    }

    private static List<OverviewViewModel.WatchlistItem> buildWatchlist(
            List<OverviewViewModel.TableRow> rows,
            List<String> watchlistItemIds
    ) {
        if (watchlistItemIds.isEmpty() || rows.isEmpty()) {
            return List.of();
        }
        Map<String, OverviewViewModel.TableRow> rowById = new HashMap<>();
        for (OverviewViewModel.TableRow row : rows) {
            rowById.putIfAbsent(row.itemId(), row);
        }

        List<OverviewViewModel.WatchlistItem> watch = new ArrayList<>();
        for (String itemId : watchlistItemIds) {
            OverviewViewModel.TableRow row = rowById.get(itemId);
            if (row == null) {
                continue;
            }
            watch.add(new OverviewViewModel.WatchlistItem(
                    row.itemId(),
                    row.displayName(),
                    row.net(),
                    row.stock(),
                    row.capacity(),
                    true,
                    row.iconSprite()
            ));
        }
        return watch;
    }

    private static List<OverviewViewModel.TableGroup> groupRows(
            List<OverviewViewModel.TableRow> rows,
            String groupFilterKey,
            List<OverviewViewModel.GroupOption> groupOptions
    ) {
        LinkedHashMap<String, List<OverviewViewModel.TableRow>> grouped = new LinkedHashMap<>();
        for (OverviewViewModel.GroupOption group : groupOptions) {
            grouped.put(group.key(), new ArrayList<>());
        }
        for (OverviewViewModel.TableRow row : rows) {
            grouped.computeIfAbsent(normalizeGroupKey(row.groupKey()), ignored -> new ArrayList<>()).add(row);
        }

        List<OverviewViewModel.TableGroup> result = new ArrayList<>();
        if (PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            for (OverviewViewModel.GroupOption group : groupOptions) {
                List<OverviewViewModel.TableRow> groupRows = grouped.getOrDefault(group.key(), List.of());
                if (groupRows.isEmpty()) {
                    continue;
                }
                result.add(new OverviewViewModel.TableGroup(group.key(), group.displayName(), groupRows));
            }
            if (result.isEmpty()) {
                result.add(new OverviewViewModel.TableGroup(
                        PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                        Component.translatable("screen.resourceobserver.overview.group.ungrouped").getString(),
                        List.of()
                ));
            }
            return result;
        }

        String key = normalizeGroupKey(groupFilterKey);
        String title = key;
        for (OverviewViewModel.GroupOption group : groupOptions) {
            if (group.key().equals(key)) {
                title = group.displayName();
                break;
            }
        }
        result.add(new OverviewViewModel.TableGroup(key, title, grouped.getOrDefault(key, List.of())));
        return result;
    }

    private static List<OverviewViewModel.TableRow> buildFallbackRows(
            ObserverDataPayload payload,
            Collection<String> starredItems
    ) {
        List<OverviewViewModel.TableRow> rows = new ArrayList<>();
        int idx = 0;
        for (ObserverDataPayload.BindingEntry binding : payload.bindings()) {
            String fallbackId = binding.networkType().toLowerCase(Locale.ROOT) + "_" + idx++;
            long stock = binding.currentValue();
            long capacity = Math.max(binding.capacity(), stock + 1);
            long net = binding.totalProduced() - binding.totalConsumed();
            rows.add(new OverviewViewModel.TableRow(
                    fallbackId,
                    binding.displayName(),
                    PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                    binding.totalProduced(),
                    binding.totalConsumed(),
                    net,
                    stock,
                    capacity,
                    isCritical(net, stock, capacity),
                    starredItems.contains(fallbackId),
                    binding.iconSprite()
            ));
        }
        return rows;
    }

    private static String localizeItemName(String itemId, String fallbackDisplayName) {
        if (itemId == null || itemId.isBlank()) {
            return fallbackDisplayName;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item != Items.AIR) {
                String translated = new ItemStack(item).getHoverName().getString();
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
            }
        } catch (Exception ignored) {
            // Keep fallback name when id is invalid or not resolvable on client.
        }
        return fallbackDisplayName == null || fallbackDisplayName.isBlank() ? itemId : fallbackDisplayName;
    }

    private static long estimateCapacity(long amount) {
        long cap = Math.max(1L, amount);
        long rounded = ((cap + 999L) / 1000L) * 1000L;
        return Math.max(rounded, cap + 250L);
    }

    private static String normalizeGroupKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlayerUiPrefsSavedData.GROUP_UNGROUPED;
        }
        return raw;
    }

    private static String normalizeGroupFilterKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlayerUiPrefsSavedData.GROUP_FILTER_ALL;
        }
        return raw;
    }

    private static boolean matchesStatus(OverviewViewModel.TableRow row, TableStatusFilter statusFilter) {
        return switch (statusFilter) {
            case ALL -> true;
            case SURPLUS -> row.net() > 0;
            case DEFICIT -> row.net() < 0;
            case CRITICAL -> row.critical();
        };
    }

    private static boolean matchesGroup(OverviewViewModel.TableRow row, String groupFilterKey) {
        if (groupFilterKey == null || groupFilterKey.isBlank() || PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            return true;
        }
        return groupFilterKey.equals(row.groupKey());
    }

    private static Comparator<OverviewViewModel.TableRow> comparatorFor(TableSortMode sortMode, boolean sortDesc) {
        Comparator<OverviewViewModel.TableRow> comparator = switch (sortMode) {
            case NET_ABS -> Comparator.comparingLong((OverviewViewModel.TableRow row) -> Math.abs(row.net()));
            case NET -> Comparator.comparingLong(OverviewViewModel.TableRow::net);
            case PRODUCTION -> Comparator.comparingLong(OverviewViewModel.TableRow::production);
            case CONSUMPTION -> Comparator.comparingLong(OverviewViewModel.TableRow::consumption);
            case STOCK_RATIO -> Comparator.comparingDouble(OverviewViewModelMapper::stockRatio);
        };
        if (sortDesc) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(OverviewViewModel.TableRow::displayName);
    }

    private static double stockRatio(OverviewViewModel.TableRow row) {
        if (row.capacity() <= 0) {
            return 0.0;
        }
        return (double) row.stock() / (double) row.capacity();
    }

    private static boolean isCritical(long net, long stock, long capacity) {
        if (net >= 0 || capacity <= 0) {
            return false;
        }
        return (double) stock / (double) capacity < 0.15d;
    }

    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}
