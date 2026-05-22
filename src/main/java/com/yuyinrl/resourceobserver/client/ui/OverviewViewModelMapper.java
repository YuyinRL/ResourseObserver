package com.yuyinrl.resourceobserver.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;

import com.yuyinrl.resourceobserver.client.ui.snapshot.ClientOverviewLocalizer;
import com.yuyinrl.resourceobserver.client.util.PinyinMatcher;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.format.SnapshotFormatters;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshotBuilder;

/**
 * 总览视图模型映射器 —— Snapshot → 客户端 ViewModel 的薄壳适配器。
 *
 * <p>业务塑形由 {@link OverviewSnapshotBuilder} 完成；本类只负责客户端本地化、展示格式化与搜索过滤。</p>
 */
public final class OverviewViewModelMapper {
    private OverviewViewModelMapper() {
    }

    /**
     * 将 Payload 数据转换为 OverviewViewModel。
     * 公开签名保持兼容；内部统一先构建 Snapshot，再做客户端适配。
     *
     * @param searchQuery 搜索关键词（按 displayName 过滤），为空时不过滤
     */
    public static OverviewViewModel fromPayload(ObserverDataPayload payload, String searchQuery) {
        OverviewSnapshot snapshot = OverviewSnapshotBuilder.fromPayload(payload, ClientOverviewLocalizer.INSTANCE);
        return fromSnapshot(snapshot, searchQuery, new LinkedHashSet<>(payload.watchlistItemIds()));
    }

    /**
     * Snapshot → ViewModel 转换入口，供 V2 / 测试直接复用。
     */
    public static OverviewViewModel fromSnapshot(OverviewSnapshot snapshot, String searchQuery) {
        return fromSnapshot(snapshot, searchQuery, Set.of());
    }

    private static OverviewViewModel fromSnapshot(
            OverviewSnapshot snapshot,
            String searchQuery,
            Set<String> watchlistSet
    ) {
        OverviewSnapshot.HeaderInfo header = snapshot.header();
        OverviewSnapshot.UiStateSnapshot ui = snapshot.uiState();
        return new OverviewViewModel(
                text(header.titleKey()),
                text(header.subtitleKey()),
                text(header.linkStatusKey()),
                mapKpis(snapshot.kpis()),
                snapshot.chartWindow(),
                snapshot.chartScopeItemId(),
                mapFlowPoints(snapshot.chartSeries()),
                mapFlowPoints(snapshot.energyChartSeries()),
                mapUiState(ui),
                mapWatchlist(snapshot.watchlistItems(), watchlistSet),
                mapTableGroups(snapshot.tableGroups(), searchQuery, watchlistSet),
                mapKpiDetails(snapshot.kpiDetails()),
                mapStorageDetail(snapshot.storageDetail())
        );
    }

    private static List<OverviewViewModel.KpiMetric> mapKpis(List<OverviewSnapshot.KpiSnapshot> source) {
        List<OverviewViewModel.KpiMetric> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.KpiSnapshot kpi : source) {
            result.add(new OverviewViewModel.KpiMetric(
                    mapType(kpi.type()),
                    text(kpi.labelKey()),
                    formatKpiValue(kpi.valueRaw(), kpi.valueKind()),
                    trendText(kpi.trendKey(), kpi.trendArg()),
                    mapStatus(kpi.status()),
                    kpi.iconSprite()
            ));
        }
        return result;
    }

    private static List<OverviewViewModel.KpiDetail> mapKpiDetails(
            List<OverviewSnapshot.KpiDetailSnapshot> source
    ) {
        List<OverviewViewModel.KpiDetail> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.KpiDetailSnapshot detail : source) {
            OverviewViewModel.KpiType type = mapType(detail.type());
            result.add(new OverviewViewModel.KpiDetail(
                    type,
                    text(detail.titleKey()),
                    formatDetailHint(detail, type),
                    mapStatus(detail.status()),
                    mapChannel(type, detail.itemChannel(), false),
                    mapChannel(type, detail.fluidChannel(), true)
            ));
        }
        return result;
    }

    private static String formatDetailHint(
            OverviewSnapshot.KpiDetailSnapshot detail,
            OverviewViewModel.KpiType type
    ) {
        OverviewSnapshot.ChannelDetail item = detail.itemChannel();
        String key = detail.hintKey();
        if (item != null && item.trendAvailable()
                && ("screen.resourceobserver.overview.kpi.trend.vs_previous".equals(key)
                || "screen.resourceobserver.overview.kpi.trend.delta_points".equals(key))) {
            return text(key, formatChannelTrend(type, item.trendPercent(), true));
        }
        return text(key);
    }

    private static OverviewViewModel.KpiDetailChannel mapChannel(
            OverviewViewModel.KpiType type,
            OverviewSnapshot.ChannelDetail channel,
            boolean fluid
    ) {
        if (channel == null) {
            return new OverviewViewModel.KpiDetailChannel("N/A", "N/A", "N/A", "N/A", false);
        }
        return new OverviewViewModel.KpiDetailChannel(
                text(channel.labelKey()),
                detailLine("screen.resourceobserver.overview.kpi.detail.recent",
                        formatChannelValue(type, channel.recentRate(), channel.available(), fluid)),
                detailLine("screen.resourceobserver.overview.kpi.detail.previous",
                        formatChannelValue(type, channel.previousRate(), channel.available(), fluid)),
                detailLine("screen.resourceobserver.overview.kpi.detail.change",
                        formatChannelTrend(type, channel.trendPercent(), channel.trendAvailable())),
                channel.available()
        );
    }

    /**
     * 公开的存储详情映射入口 —— V2 StorageDetailDialog 直接使用快照转 ViewModel.StorageDetail。
     */
    public static OverviewViewModel.StorageDetail toStorageDetail(
            OverviewSnapshot.StorageDetailSnapshot snapshot
    ) {
        return mapStorageDetail(snapshot);
    }

    private static OverviewViewModel.StorageDetail mapStorageDetail(
            OverviewSnapshot.StorageDetailSnapshot detail
    ) {
        if (detail == null) {
            return OverviewViewModel.StorageDetail.unavailable("N/A");
        }
        String hint = text(detail.hintKey());
        if (!detail.hasAe2Binding()) {
            return OverviewViewModel.StorageDetail.unavailable(hint);
        }
        return new OverviewViewModel.StorageDetail(
                true,
                detail.diskReliable(),
                detail.externalReliable(),
                hint,
                mapStorageChannel(detail.diskItem(), false),
                mapStorageChannel(detail.diskFluid(), false),
                mapStorageChannel(detail.externalItem(), true),
                mapStorageChannel(detail.externalFluid(), true)
        );
    }

    private static OverviewViewModel.StorageChannel mapStorageChannel(
            OverviewSnapshot.StorageChannelSnapshot channel,
            boolean external
    ) {
        if (channel == null || !channel.available()) {
            return OverviewViewModel.StorageChannel.na();
        }
        String usageKey = external ? externalUsageKey(channel.labelKey())
                : "screen.resourceobserver.overview.storage.detail.bytes_usage";
        String types = external ? text("screen.resourceobserver.overview.storage.detail.types_na")
                : text("screen.resourceobserver.overview.storage.detail.types_usage",
                        SnapshotFormatters.formatCompact(channel.usedTypes()),
                        SnapshotFormatters.formatCompact(channel.totalTypes()));
        String typesExact = external ? text("screen.resourceobserver.overview.storage.detail.types_na")
                : text("screen.resourceobserver.overview.storage.detail.types_usage",
                        SnapshotFormatters.formatExactCount(channel.usedTypes()),
                        SnapshotFormatters.formatExactCount(channel.totalTypes()));
        return new OverviewViewModel.StorageChannel(
                text(channel.labelKey()),
                text(usageKey, formatCompactStorage(channel.usedBytes(), external),
                        formatCompactStorage(channel.totalBytes(), external)),
                types,
                text(usageKey, formatExactStorage(channel.usedBytes(), external),
                        formatExactStorage(channel.totalBytes(), external)),
                typesExact,
                true
        );
    }

    private static List<OverviewViewModel.FlowPoint> mapFlowPoints(
            List<OverviewSnapshot.ChartPointSnapshot> source
    ) {
        List<OverviewViewModel.FlowPoint> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.ChartPointSnapshot point : source) {
            result.add(new OverviewViewModel.FlowPoint(
                    point.slotIndex(),
                    point.production(),
                    point.consumption(),
                    point.net(),
                    point.stock(),
                    point.hasFlow(),
                    point.hasStock()
            ));
        }
        return result;
    }

    private static OverviewViewModel.UiState mapUiState(OverviewSnapshot.UiStateSnapshot ui) {
        List<OverviewViewModel.GroupOption> groups = new ArrayList<>(ui.groups().size());
        for (OverviewSnapshot.GroupOptionSnapshot group : ui.groups()) {
            groups.add(new OverviewViewModel.GroupOption(group.key(), group.displayName(), group.systemGroup()));
        }
        return new OverviewViewModel.UiState(
                ui.groupFilterKey(),
                groups,
                ui.sortMode(),
                ui.sortDesc(),
                ui.statusFilter(),
                ui.watchlistLimit()
        );
    }

    private static List<OverviewViewModel.WatchlistItem> mapWatchlist(
            List<OverviewSnapshot.WatchlistItemSnapshot> source,
            Set<String> watchlistSet
    ) {
        List<OverviewViewModel.WatchlistItem> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.WatchlistItemSnapshot item : source) {
            boolean starred = watchlistSet.isEmpty() || watchlistSet.contains(item.itemId());
            result.add(new OverviewViewModel.WatchlistItem(
                    item.itemId(), item.displayName(), item.netPerMinute(), item.stock(), starred, item.iconSprite()
            ));
        }
        return result;
    }

    private static List<OverviewViewModel.TableGroup> mapTableGroups(
            List<OverviewSnapshot.TableGroupSnapshot> source,
            String searchQuery,
            Set<String> watchlistSet
    ) {
        String query = normalizeQuery(searchQuery);
        List<OverviewViewModel.TableGroup> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.TableGroupSnapshot group : source) {
            List<OverviewViewModel.TableRow> rows = mapRows(group.rows(), query, watchlistSet);
            if (!rows.isEmpty()) {
                result.add(new OverviewViewModel.TableGroup(group.key(), group.displayName(), rows));
            }
        }
        return result;
    }

    private static List<OverviewViewModel.TableRow> mapRows(
            List<OverviewSnapshot.TableRowSnapshot> source,
            String query,
            Set<String> watchlistSet
    ) {
        List<OverviewViewModel.TableRow> result = new ArrayList<>(source.size());
        for (OverviewSnapshot.TableRowSnapshot row : source) {
            if (!query.isEmpty() && !PinyinMatcher.matches(row.displayName(), query)) {
                continue;
            }
            boolean starred = watchlistSet.isEmpty() ? row.starred() : watchlistSet.contains(row.itemId());
            result.add(new OverviewViewModel.TableRow(
                    row.itemId(),
                    row.displayName(),
                    row.groupKey(),
                    row.production(),
                    row.consumption(),
                    row.net(),
                    row.stock(),
                    row.critical(),
                    starred,
                    row.iconSprite()
            ));
        }
        return result;
    }

    private static String formatKpiValue(double value, OverviewSnapshot.ValueKind kind) {
        if (!Double.isFinite(value)) {
            return "N/A";
        }
        return switch (kind) {
            case FLOW_PER_MINUTE -> SnapshotFormatters.formatCompactDouble(value) + " /min";
            case PERCENT -> SnapshotFormatters.trimTrailingZeros(String.format(java.util.Locale.ROOT, "%.1f%%", value));
            case BALANCE_SCORE -> SnapshotFormatters.formatSignedPoints(value);
            case COUNT -> SnapshotFormatters.formatCompact(Math.round(value));
        };
    }

    private static String formatChannelValue(
            OverviewViewModel.KpiType type,
            double value,
            boolean available,
            boolean fluid
    ) {
        if (!available) {
            return text("screen.resourceobserver.overview.kpi.loading");
        }
        if (type == OverviewViewModel.KpiType.BALANCE) {
            return SnapshotFormatters.formatSignedPoints(value);
        }
        String suffix = fluid ? "B/min" : "/min";
        return SnapshotFormatters.formatExactDouble(value) + " " + suffix;
    }

    private static String formatChannelTrend(OverviewViewModel.KpiType type, double value, boolean available) {
        if (!available) {
            return trendUnavailableText();
        }
        if (type == OverviewViewModel.KpiType.BALANCE) {
            return SnapshotFormatters.formatSignedPoints(value);
        }
        return SnapshotFormatters.formatSignedPercent(value);
    }

    private static String formatCompactStorage(long value, boolean external) {
        return external ? SnapshotFormatters.formatCompact(value) : SnapshotFormatters.formatByteLike(value);
    }

    private static String formatExactStorage(long value, boolean external) {
        return external ? SnapshotFormatters.formatExactCount(value) : SnapshotFormatters.formatExactByteLike(value);
    }

    private static String externalUsageKey(String labelKey) {
        if ("screen.resourceobserver.overview.storage.detail.external_fluid".equals(labelKey)) {
            return "screen.resourceobserver.overview.storage.detail.external_fluid_usage";
        }
        return "screen.resourceobserver.overview.storage.detail.external_item_usage";
    }

    private static String detailLine(String key, String value) {
        return text(key, value);
    }

    private static String trendText(String key, String arg) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return arg == null ? text(key) : text(key, arg);
    }

    private static String trendUnavailableText() {
        return text("screen.resourceobserver.overview.kpi.trend.unavailable");
    }

    private static String normalizeQuery(String searchQuery) {
        return searchQuery == null || searchQuery.isBlank() ? "" : searchQuery.trim();
    }

    private static String text(String key, Object... args) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return Component.translatable(key, args).getString();
    }

    private static OverviewViewModel.KpiType mapType(OverviewSnapshot.KpiType type) {
        if (type == null) {
            return OverviewViewModel.KpiType.BALANCE;
        }
        return switch (type) {
            case PRODUCTION -> OverviewViewModel.KpiType.PRODUCTION;
            case CONSUMPTION -> OverviewViewModel.KpiType.CONSUMPTION;
            case STORAGE -> OverviewViewModel.KpiType.STORAGE;
            case BALANCE -> OverviewViewModel.KpiType.BALANCE;
        };
    }

    private static OverviewViewModel.Status mapStatus(OverviewSnapshot.KpiStatus status) {
        if (status == null) {
            return OverviewViewModel.Status.NEUTRAL;
        }
        return switch (status) {
            case POSITIVE -> OverviewViewModel.Status.POSITIVE;
            case WARNING -> OverviewViewModel.Status.WARNING;
            case NEGATIVE -> OverviewViewModel.Status.NEGATIVE;
            case NEUTRAL -> OverviewViewModel.Status.NEUTRAL;
        };
    }
}
