package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.network.chat.Component;

import java.util.List;

public record OverviewViewModel(
        String headerTitle,
        String headerSubtitle,
        String linkStatus,
        List<KpiMetric> kpis,
        ChartWindow chartWindow,
        String chartScopeItemId,
        List<FlowPoint> chartSeries,
        UiState uiState,
        List<WatchlistItem> watchlistItems,
        List<TableGroup> tableGroups
) {
    public record KpiMetric(
            String label,
            String value,
            String trend,
            Status status,
            String iconSprite
    ) {
    }

    public record FlowPoint(
            int slotIndex,
            double production,
            double consumption,
            double net,
            double stock,
            boolean hasFlow,
            boolean hasStock
    ) {
    }

    public record WatchlistItem(
            String itemId,
            String displayName,
            long netPerMinute,
            long stock,
            long capacity,
            boolean starred,
            String iconSprite
    ) {
    }

    public record GroupOption(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    public record TableGroup(
            String key,
            String title,
            List<TableRow> rows
    ) {
    }

    public record TableRow(
            String itemId,
            String displayName,
            String groupKey,
            long production,
            long consumption,
            long net,
            long stock,
            long capacity,
            boolean critical,
            boolean starred,
            String iconSprite
    ) {
    }

    public record UiState(
            String groupFilterKey,
            List<GroupOption> groups,
            TableSortMode sortMode,
            boolean sortDesc,
            TableStatusFilter statusFilter,
            int watchlistLimit
    ) {
        public String groupNameByKey(String key) {
            if (key == null) {
                return "";
            }
            if (PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(key)) {
                return Component.translatable("screen.resourceobserver.overview.filter.group.all").getString();
            }
            for (GroupOption group : groups) {
                if (key.equals(group.key())) {
                    return group.displayName();
                }
            }
            return key;
        }
    }

    public enum Status {
        POSITIVE,
        WARNING,
        NEGATIVE,
        NEUTRAL
    }
}
