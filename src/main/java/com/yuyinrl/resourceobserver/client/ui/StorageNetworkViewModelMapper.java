package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.client.ui.snapshot.ClientStorageLocalizer;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshotBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 存储网络视图模型映射器 —— Snapshot → 客户端 ViewModel 的薄壳适配器。
 *
 * <p>F2.B 起：所有数据塑形逻辑下沉至
 * {@link com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshotBuilder}
 * （service 层、三方共用）。本类仅负责：
 * <ol>
 *   <li>调用 {@code StorageSnapshotBuilder.fromPayload(...)} 拿到不可变 {@link StorageSnapshot}，
 *       注入 {@link ClientStorageLocalizer#INSTANCE} 与拼音搜索 matcher</li>
 *   <li>把翻译键解析为本地化字符串（节点状态 / 分段名）</li>
 *   <li>把 {@link StorageSnapshot.GroupColorSlot} 映射为 {@link UiThemeTokens} ARGB</li>
 *   <li>枚举 1:1 映射：{@link OverviewSnapshot.KpiStatus} → {@link OverviewViewModel.Status}、
 *       {@link StorageSnapshot.AlertLevel} → {@link StorageNetworkViewModel.AlertLevel}</li>
 * </ol>
 *
 * <p>历史 helper（{@code formatBufferSeconds} / {@code bufferRatioFromSeconds} /
 * {@code computeCountdownSeconds}）已迁出至 {@code SnapshotFormatters} 与
 * {@code StorageBufferSmoother}，PageBuilder 直接使用新工具类。
 */
public final class StorageNetworkViewModelMapper {

    private StorageNetworkViewModelMapper() {
    }

    /**
     * 主入口 —— 把 {@link ObserverDataPayload} 折叠为 {@link StorageNetworkViewModel}。
     * 公开签名保持兼容；内部走 Snapshot。
     *
     * @param payload           服务端数据载荷
     * @param selectedNodeId    当前选中的节点 ID（null/空 = 全部节点）
     * @param alertFilterActive 是否仅显示异常物品
     * @param bufferEma         倒计时锚点状态（跨 tick 持久化，由本方法读写）
     * @param searchQuery       搜索关键词（按 displayName 过滤；客户端使用拼音首字母匹配）
     */
    public static StorageNetworkViewModel fromPayload(
            ObserverDataPayload payload,
            String selectedNodeId,
            boolean alertFilterActive,
            Map<String, double[]> bufferEma,
            String searchQuery
    ) {
        StorageSnapshot snapshot = StorageSnapshotBuilder.fromPayload(
                payload,
                selectedNodeId,
                alertFilterActive,
                bufferEma,
                searchQuery,
                ClientStorageLocalizer.INSTANCE,
                ClientStorageLocalizer.PINYIN_MATCHER
        );
        return fromSnapshot(snapshot);
    }

    /**
     * Snapshot → ViewModel 转换 —— 仅做枚举映射、翻译键解析与颜色槽映射。
     * 暴露为 public 便于将来 V2 / 测试场景直接构造 ViewModel。
     */
    public static StorageNetworkViewModel fromSnapshot(StorageSnapshot snapshot) {
        List<StorageNetworkViewModel.NodeEntry> nodes = new ArrayList<>(snapshot.nodes().size());
        for (StorageSnapshot.NodeSnapshot n : snapshot.nodes()) {
            nodes.add(new StorageNetworkViewModel.NodeEntry(
                    n.nodeId(),
                    n.displayName(),
                    n.networkType(),
                    n.iconSprite(),
                    n.itemCount(),
                    n.capacityRatio(),
                    n.selected(),
                    n.usedFormatted(),
                    n.totalFormatted(),
                    Component.translatable(n.statusKey()).getString(),
                    n.statusAlert(),
                    n.coordinatesText()
            ));
        }

        List<StorageNetworkViewModel.StorageKpi> kpis = new ArrayList<>(snapshot.kpis().size());
        for (StorageSnapshot.KpiSnapshot k : snapshot.kpis()) {
            kpis.add(new StorageNetworkViewModel.StorageKpi(
                    k.labelKey(),
                    k.value(),
                    mapStatus(k.status())
            ));
        }

        List<StorageNetworkViewModel.ItemRow> items = new ArrayList<>(snapshot.items().size());
        for (StorageSnapshot.ItemRowSnapshot row : snapshot.items()) {
            items.add(new StorageNetworkViewModel.ItemRow(
                    row.itemId(),
                    row.displayName(),
                    row.iconSprite(),
                    row.localAmount(),
                    row.globalAmount(),
                    row.delta(),
                    mapAlert(row.alertLevel()),
                    row.groupKey(),
                    row.burnRatePerMin(),
                    row.estimatedBufferText(),
                    row.bufferRatio()
            ));
        }

        List<StorageNetworkViewModel.UsageSegment> segments = new ArrayList<>(snapshot.usageSegments().size());
        for (StorageSnapshot.UsageSegmentSnapshot seg : snapshot.usageSegments()) {
            segments.add(new StorageNetworkViewModel.UsageSegment(
                    seg.groupKey(),
                    Component.translatable(seg.displayNameKey()).getString(),
                    seg.percentage(),
                    mapColorSlot(seg.colorSlot())
            ));
        }

        return new StorageNetworkViewModel(
                nodes,
                snapshot.selectedNodeId(),
                snapshot.alertFilterActive(),
                kpis,
                items,
                segments,
                snapshot.totalItemCount(),
                snapshot.criticalItemCount(),
                snapshot.totalUsedRatio()
        );
    }

    // ===== Snapshot 枚举 → ViewModel 枚举 =====

    private static OverviewViewModel.Status mapStatus(OverviewSnapshot.KpiStatus s) {
        if (s == null) return OverviewViewModel.Status.NEUTRAL;
        return switch (s) {
            case POSITIVE -> OverviewViewModel.Status.POSITIVE;
            case WARNING  -> OverviewViewModel.Status.WARNING;
            case NEGATIVE -> OverviewViewModel.Status.NEGATIVE;
            case NEUTRAL  -> OverviewViewModel.Status.NEUTRAL;
        };
    }

    private static StorageNetworkViewModel.AlertLevel mapAlert(StorageSnapshot.AlertLevel a) {
        if (a == null) return StorageNetworkViewModel.AlertLevel.GREEN;
        return switch (a) {
            case GREEN  -> StorageNetworkViewModel.AlertLevel.GREEN;
            case YELLOW -> StorageNetworkViewModel.AlertLevel.YELLOW;
            case RED    -> StorageNetworkViewModel.AlertLevel.RED;
        };
    }

    private static int mapColorSlot(StorageSnapshot.GroupColorSlot slot) {
        if (slot == null) return UiThemeTokens.BLUE;
        return switch (slot) {
            case RAW          -> UiThemeTokens.CYAN;
            case INTERMEDIATE -> UiThemeTokens.AMBER;
            case FINISHED     -> UiThemeTokens.EMERALD;
            case OTHER        -> UiThemeTokens.BLUE;
        };
    }
}
