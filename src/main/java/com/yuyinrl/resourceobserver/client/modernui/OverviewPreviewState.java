package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;

import java.util.List;

/**
 * ModernUI 总览预览状态（样例数据）。
 */
public record OverviewPreviewState(
        String headerTitle,
        String headerSubtitle,
        boolean linked,
        int observerX,
        int observerY,
        int observerZ,
        List<KpiCard> kpis,
        List<GroupOption> groups,
        List<WatchEntry> watchlist,
        List<TableEntry> table
) {
    public record KpiCard(
            OverviewViewModel.KpiType type,
            String labelKey,
            String value,
            String trend,
            OverviewViewModel.Status status
    ) {
    }

    public record GroupOption(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    public record WatchEntry(
            String itemId,
            String displayName,
            long netPerMinute,
            long stock
    ) {
    }

    public record TableEntry(
            String itemId,
            String displayName,
            String groupKey,
            long production,
            long consumption,
            long stock,
            boolean critical,
            boolean starred
    ) {
        public long net() {
            return production - consumption;
        }
    }
}
