package com.yuyinrl.resourceobserver.web.handler;

import appeng.api.networking.IGrid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshotBuilder;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshotBuilder;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshotBuilder;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.auth.AuthContext;
import com.yuyinrl.resourceobserver.service.ObserverService;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.Ae2CellCapacityMetrics;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/observers} —— 列出所有已加载的 Observer。<br>
 * {@code GET /api/observers/{dim}/{x}/{y}/{z}} —— 单个 Observer 的详细快照。
 */
public final class ObserversHandler extends BaseApiHandler implements HttpHandler {

    public ObserversHandler(WebServerService server) {
        super(server);
    }

    /**
     * /api/observers 与 /api/observers/{id} —— 列表与详情统一入口，按路径段决定走哪条分支。
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/observers") || path.equals("/api/observers/")) {
            handleList(exchange);
        } else {
            handleDetail(exchange, path);
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        AuthContext ctx = authContext(exchange);
        boolean legacy = legacyAllowsAnyone();
        List<Map<String, Object>> result = runOnMain(mc -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ObserverBlockEntity be : ObserverService.listObservers()) {
                // 列表过滤：跳过用户无权查看的 Observer
                if (!be.canView(ctx == null ? null : ctx.viewer(), ctx != null && ctx.admin(), legacy)) {
                    continue;
                }
                Map<String, Object> row = summarizeObserver(be);
                if (row != null) list.add(row);
            }
            return list;
        });
        if (result == null) {
            sendError(exchange, 504, "server busy");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("observers", result);
        body.put("count", result.size());
        sendJson(exchange, 200, body);
    }

    private void handleDetail(HttpExchange exchange, String path) throws IOException {
        ObserverTarget target = parseObserverPath(path);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        AuthContext ctx = authContext(exchange);
        AccessResult<Map<String, Object>> res = runOnMainWithAccess(exchange, target,
                (mc, observer) -> buildDetailFromObserver(observer, ctx));
        if (res.isForbidden()) {
            sendError(exchange, 403, "forbidden");
            return;
        }
        if (res.isNotFound()) {
            sendError(exchange, 404, "observer not found");
            return;
        }
        sendJson(exchange, 200, res.body());
    }

    private @Nullable Map<String, Object> buildDetailFromObserver(
            ObserverBlockEntity observer,
            @Nullable AuthContext ctx
    ) {
        Map<String, Object> body = summarizeObserver(observer);
        if (body == null) return null;
        PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot prefs = overviewPrefs(observer, ctx);
        OverviewSnapshot overview = OverviewSnapshotBuilder.fromObserver(
                observer, ServerOverviewLocalizer.INSTANCE, prefs);
        PowerSnapshot power = PowerSnapshotBuilder.fromObserver(observer);
        body.put("overview", overviewMap(overview));
        body.put("power", powerMap(power));
        body.put("bindings", detailedBindings(observer));
        return body;
    }

    private PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot overviewPrefs(
            ObserverBlockEntity observer,
            @Nullable AuthContext ctx
    ) {
        if (ctx == null || ctx.viewer() == null || !(observer.getLevel() instanceof ServerLevel level)) {
            return PlayerUiPrefsSavedData.PlayerUiPrefsSnapshot.defaults();
        }
        return PlayerUiPrefsSavedData.get(level).getSnapshot(ctx.viewer());
    }

    /** 简要摘要（用于列表端点）。 */
    static @Nullable Map<String, Object> summarizeObserver(ObserverBlockEntity be) {
        if (be.getLevel() == null) return null;
        Map<String, Object> row = new LinkedHashMap<>();
        String dim = be.getLevel().dimension().location().toString();
        row.put("dimension", dim);
        BlockPos p = be.getBlockPos();
        row.put("x", p.getX());
        row.put("y", p.getY());
        row.put("z", p.getZ());
        row.put("bound", be.isBound());
        row.put("bindingCount", be.getBindingCount());
        // displayName: 简短易读，供前端 Selector 使用
        String shortDim = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        row.put("displayName", "Observer @ " + shortDim + " [" + p.getX() + "," + p.getY() + "," + p.getZ() + "]");
        // id: 唯一标识（同时是 REST URL 路径片段）
        row.put("id", dim + "/" + p.getX() + "/" + p.getY() + "/" + p.getZ());
        return row;
    }

    private List<Map<String, Object>> detailedBindings(ObserverBlockEntity observer) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BoundEntry binding : observer.getBindings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("networkType", binding.networkType());
            entry.put("networkId", binding.networkId());
            entry.put("targetBlockId", binding.targetBlockId());

            BindingStats stats = observer.getStatsFor(binding.networkId());
            entry.put("currentValue", stats.currentValue());
            entry.put("capacity", stats.capacity());
            entry.put("totalProduced", stats.totalProduced());
            entry.put("totalConsumed", stats.totalConsumed());

            if ("AE2_ITEMS".equals(binding.networkType())) {
                StorageSnapshot snapshot = StorageSnapshotBuilder.fromObserver(
                        observer,
                        binding.networkId(),
                        false,
                        new HashMap<>(),
                        "",
                        ServerStorageLocalizer.INSTANCE,
                        StorageSnapshotBuilder.SearchMatcher.DEFAULT,
                        null
                );
                entry.put("items", ItemsHandler.itemRows(snapshot, binding.networkId()));
                entry.put("kpis", ItemsHandler.kpis(snapshot));
                entry.put("usageSegments", ItemsHandler.usageSegments(snapshot));
                entry.put("nodeSummary", ItemsHandler.nodeSummary(snapshot, binding.networkId()));
                Ae2CellCapacityMetrics cell = observer.getAe2CellCapacityMetricsFor(binding.networkId());
                entry.put("cellCapacity", cellMetricsMap(cell));
            } else if ("FLUX_ENERGY".equals(binding.networkType())) {
                FluxNetworksIntegration.FluxSampleResult fr = observer.getFluxSampleResultFor(binding.networkId());
                if (fr != null) {
                    Map<String, Object> flux = new LinkedHashMap<>();
                    flux.put("totalEnergy", fr.totalEnergy());
                    flux.put("totalMaxEnergyStorage", fr.totalMaxEnergyStorage());
                    flux.put("totalBuffer", fr.totalBuffer());
                    flux.put("energyInput", fr.energyInput());
                    flux.put("energyOutput", fr.energyOutput());
                    flux.put("networkName", fr.networkName());
                    flux.put("networkId", fr.networkId());
                    flux.put("networkColor", fr.networkColor());
                    // 设备分类计数 —— 对齐游戏内 Power Network 页的「接口/储能块」统计
                    flux.put("plugCount", fr.plugCount());
                    flux.put("pointCount", fr.pointCount());
                    flux.put("storageCount", fr.storageCount());
                    flux.put("controllerCount", fr.controllerCount());
                    flux.put("deviceCount",
                            fr.plugCount() + fr.pointCount() + fr.storageCount() + fr.controllerCount());
                    // 设备列表 —— 供 Web 侧「Device List / External Control」使用
                    List<Map<String, Object>> devices = new ArrayList<>();
                    if (fr.devices() != null) {
                        for (FluxNetworksIntegration.FluxDeviceSnapshot d : fr.devices()) {
                            Map<String, Object> dev = new LinkedHashMap<>();
                            dev.put("deviceType", d.deviceType());
                            dev.put("customName", d.customName());
                            dev.put("transferChange", d.transferChange());
                            dev.put("transferBuffer", d.transferBuffer());
                            dev.put("rawLimit", d.rawLimit());
                            dev.put("maxTransferLimit", d.maxTransferLimit());
                            dev.put("maxEnergyStorage", d.maxEnergyStorage());
                            dev.put("posKey", d.posKey());
                            dev.put("externalEnergyStored", d.externalEnergyStored());
                            dev.put("externalEnergyCapacity", d.externalEnergyCapacity());
                            // 外部相邻方块引用 —— 用于「Consumer 用电器」与 External Group 列表
                            List<Map<String, Object>> refs = new ArrayList<>();
                            if (d.externalRefs() != null) {
                                for (FluxNetworksIntegration.ExternalEnergyRef r : d.externalRefs()) {
                                    Map<String, Object> ref = new LinkedHashMap<>();
                                    ref.put("extId", r.extId());
                                    ref.put("displayName", r.displayName());
                                    ref.put("stored", r.stored());
                                    ref.put("capacity", r.capacity());
                                    ref.put("maxAcceptPerTick", r.maxAcceptPerTick());
                                    refs.add(ref);
                                }
                            }
                            dev.put("externalRefs", refs);
                            devices.add(dev);
                        }
                    }
                    flux.put("devices", devices);
                    entry.put("flux", flux);
                }
            }
            out.add(entry);
        }
        return out;
    }

    private Map<String, Object> powerMap(PowerSnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", snapshot.displayName());
        body.put("totalInputPerTick", snapshot.totalInputPerTick());
        body.put("totalOutputPerTick", snapshot.totalOutputPerTick());
        body.put("totalStored", snapshot.totalStored());
        body.put("totalCapacity", snapshot.totalCapacity());
        body.put("timestampMs", snapshot.timestampMs());
        body.put("devices", powerDeviceMaps(snapshot.devices()));
        body.put("kpiCards", powerKpiMaps(snapshot.kpiCards()));
        body.put("loadSegments", powerLoadSegmentMaps(snapshot.loadSegments()));
        body.put("overloadAlerts", powerAlertMaps(snapshot.overloadAlerts()));
        body.put("overloadInfo", powerOverloadInfoMap(snapshot.overloadInfo()));
        body.put("consumers", powerConsumerMaps(snapshot.consumers()));
        return body;
    }

    private List<Map<String, Object>> powerDeviceMaps(List<PowerSnapshot.DeviceSnapshot> devices) {
        List<Map<String, Object>> out = new ArrayList<>(devices.size());
        for (PowerSnapshot.DeviceSnapshot device : devices) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nodeId", device.nodeId());
            row.put("displayName", device.displayName());
            row.put("category", device.category().name());
            row.put("modName", device.modName());
            row.put("energyPerTick", device.energyPerTick());
            row.put("storedEnergy", device.storedEnergy());
            row.put("maxCapacity", device.maxCapacity());
            row.put("usageRatio", device.usageRatio());
            row.put("alertLevel", device.alertLevel().name());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> powerKpiMaps(List<PowerSnapshot.KpiSnapshot> kpis) {
        List<Map<String, Object>> out = new ArrayList<>(kpis.size());
        for (PowerSnapshot.KpiSnapshot kpi : kpis) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("labelKey", ServerPowerLocalizer.INSTANCE.localizeKey(kpi.labelKey()));
            row.put("value", kpi.value());
            row.put("status", kpi.status().name());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> powerLoadSegmentMaps(List<PowerSnapshot.LoadSegmentSnapshot> segments) {
        List<Map<String, Object>> out = new ArrayList<>(segments.size());
        for (PowerSnapshot.LoadSegmentSnapshot segment : segments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", segment.category().name());
            row.put("displayNameKey", ServerPowerLocalizer.INSTANCE.localizeKey(segment.displayNameKey()));
            row.put("percentage", segment.percentage());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> powerAlertMaps(List<PowerSnapshot.OverloadAlertSnapshot> alerts) {
        List<Map<String, Object>> out = new ArrayList<>(alerts.size());
        for (PowerSnapshot.OverloadAlertSnapshot alert : alerts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nodeId", alert.nodeId());
            row.put("displayName", alert.displayName());
            row.put("alertLevel", alert.alertLevel().name());
            row.put("throughputLoss", alert.throughputLoss());
            row.put("descriptionKey", ServerPowerLocalizer.INSTANCE.localizeKey(alert.descriptionKey()));
            row.put("descriptionArgs", alert.descriptionArgs());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> powerOverloadInfoMap(PowerSnapshot.OverloadInfoSnapshot info) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headroomPercent", info.headroomPercent());
        body.put("reservePerTick", info.reservePerTick());
        return body;
    }

    private List<Map<String, Object>> powerConsumerMaps(List<PowerSnapshot.ConsumerSnapshot> consumers) {
        List<Map<String, Object>> out = new ArrayList<>(consumers.size());
        for (PowerSnapshot.ConsumerSnapshot consumer : consumers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceName", consumer.deviceName());
            row.put("modName", consumer.modName());
            row.put("consumptionPerTick", consumer.consumptionPerTick());
            row.put("percentage", consumer.percentage());
            row.put("supplyRatio", consumer.supplyRatio());
            row.put("paletteIndex", consumer.paletteIndex());
            row.put("count", consumer.count());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> overviewMap(OverviewSnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("header", headerMap(snapshot.header()));
        body.put("kpis", kpiMaps(snapshot.kpis()));
        body.put("kpiDetails", kpiDetailMaps(snapshot.kpiDetails()));
        body.put("storageDetail", storageDetailMap(snapshot.storageDetail()));
        body.put("chartWindow", snapshot.chartWindow().name());
        body.put("chartScope", snapshot.chartScope().name());
        body.put("chartScopeItemId", blankToNull(snapshot.chartScopeItemId()));
        body.put("chartSeries", chartPointMaps(snapshot.chartSeries()));
        body.put("energyChartSeries", chartPointMaps(snapshot.energyChartSeries()));
        body.put("watchlistItems", watchlistMaps(snapshot.watchlistItems()));
        body.put("tableGroups", tableGroupMaps(snapshot.tableGroups()));
        body.put("uiState", uiStateMap(snapshot.uiState()));
        return body;
    }

    private Map<String, Object> headerMap(OverviewSnapshot.HeaderInfo header) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titleKey", header.titleKey());
        body.put("subtitleKey", header.subtitleKey());
        body.put("linkStatusKey", header.linkStatusKey());
        body.put("bindingCount", header.bindingCount());
        body.put("hasAe2Binding", header.hasAe2Binding());
        body.put("hasFluxBinding", header.hasFluxBinding());
        return body;
    }

    private List<Map<String, Object>> kpiMaps(List<OverviewSnapshot.KpiSnapshot> kpis) {
        List<Map<String, Object>> out = new ArrayList<>(kpis.size());
        for (OverviewSnapshot.KpiSnapshot kpi : kpis) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", kpi.type().name());
            row.put("labelKey", kpi.labelKey());
            row.put("valueRaw", kpi.valueRaw());
            row.put("valueKind", kpi.valueKind().name());
            row.put("trendKey", kpi.trendKey());
            row.put("trendArg", kpi.trendArg());
            row.put("status", kpi.status().name());
            row.put("iconSprite", kpi.iconSprite());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> kpiDetailMaps(List<OverviewSnapshot.KpiDetailSnapshot> details) {
        List<Map<String, Object>> out = new ArrayList<>(details.size());
        for (OverviewSnapshot.KpiDetailSnapshot detail : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", detail.type().name());
            row.put("titleKey", detail.titleKey());
            row.put("hintKey", detail.hintKey());
            row.put("status", detail.status().name());
            row.put("itemChannel", channelMap(detail.itemChannel()));
            row.put("fluidChannel", channelMap(detail.fluidChannel()));
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> channelMap(OverviewSnapshot.ChannelDetail channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("labelKey", channel.labelKey());
        body.put("recentRate", channel.recentRate());
        body.put("previousRate", channel.previousRate());
        body.put("trendPercent", channel.trendPercent());
        body.put("trendAvailable", channel.trendAvailable());
        body.put("available", channel.available());
        return body;
    }

    private Map<String, Object> storageDetailMap(OverviewSnapshot.StorageDetailSnapshot detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hasAe2Binding", detail.hasAe2Binding());
        body.put("diskReliable", detail.diskReliable());
        body.put("externalReliable", detail.externalReliable());
        body.put("hintKey", detail.hintKey());
        body.put("diskItem", storageChannelMap(detail.diskItem()));
        body.put("diskFluid", storageChannelMap(detail.diskFluid()));
        body.put("externalItem", storageChannelMap(detail.externalItem()));
        body.put("externalFluid", storageChannelMap(detail.externalFluid()));
        return body;
    }

    private Map<String, Object> storageChannelMap(OverviewSnapshot.StorageChannelSnapshot channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("labelKey", channel.labelKey());
        body.put("usedBytes", channel.usedBytes());
        body.put("totalBytes", channel.totalBytes());
        body.put("usedTypes", channel.usedTypes());
        body.put("totalTypes", channel.totalTypes());
        body.put("available", channel.available());
        return body;
    }

    private List<Map<String, Object>> chartPointMaps(List<OverviewSnapshot.ChartPointSnapshot> points) {
        List<Map<String, Object>> out = new ArrayList<>(points.size());
        for (OverviewSnapshot.ChartPointSnapshot point : points) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slotIndex", point.slotIndex());
            row.put("bucket", point.bucket());
            row.put("production", point.production());
            row.put("consumption", point.consumption());
            row.put("net", point.net());
            row.put("stock", point.stock());
            row.put("hasFlow", point.hasFlow());
            row.put("hasStock", point.hasStock());
            row.put("sampleCount", point.sampleCount());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> watchlistMaps(List<OverviewSnapshot.WatchlistItemSnapshot> items) {
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (OverviewSnapshot.WatchlistItemSnapshot item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.itemId());
            row.put("displayName", item.displayName());
            row.put("netPerMinute", item.netPerMinute());
            row.put("stock", item.stock());
            row.put("iconSprite", item.iconSprite());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> tableGroupMaps(List<OverviewSnapshot.TableGroupSnapshot> groups) {
        List<Map<String, Object>> out = new ArrayList<>(groups.size());
        for (OverviewSnapshot.TableGroupSnapshot group : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", group.key());
            row.put("displayName", group.displayName());
            row.put("rows", tableRowMaps(group.rows()));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> tableRowMaps(List<OverviewSnapshot.TableRowSnapshot> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (OverviewSnapshot.TableRowSnapshot item : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.itemId());
            row.put("displayName", item.displayName());
            row.put("groupKey", item.groupKey());
            row.put("production", item.production());
            row.put("consumption", item.consumption());
            row.put("net", item.net());
            row.put("stock", item.stock());
            row.put("critical", item.critical());
            row.put("starred", item.starred());
            row.put("iconSprite", item.iconSprite());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> uiStateMap(OverviewSnapshot.UiStateSnapshot state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupFilterKey", state.groupFilterKey());
        body.put("groups", groupOptionMaps(state.groups()));
        body.put("sortMode", state.sortMode().name());
        body.put("sortDesc", state.sortDesc());
        body.put("statusFilter", state.statusFilter().name());
        body.put("watchlistLimit", state.watchlistLimit());
        return body;
    }

    private List<Map<String, Object>> groupOptionMaps(List<OverviewSnapshot.GroupOptionSnapshot> groups) {
        List<Map<String, Object>> out = new ArrayList<>(groups.size());
        for (OverviewSnapshot.GroupOptionSnapshot group : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", group.key());
            row.put("displayName", group.displayName());
            row.put("systemGroup", group.systemGroup());
            out.add(row);
        }
        return out;
    }

    private @Nullable String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Object> cellMetricsMap(Ae2CellCapacityMetrics m) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemUsedBytes", m.itemUsedBytes());
        body.put("itemTotalBytes", m.itemTotalBytes());
        body.put("itemUsedTypes", m.itemUsedTypes());
        body.put("itemTotalTypes", m.itemTotalTypes());
        body.put("itemUsedUnits", m.itemUsedUnits());
        body.put("itemMaxUnits", m.itemMaxUnits());
        body.put("fluidUsedBytes", m.fluidUsedBytes());
        body.put("fluidTotalBytes", m.fluidTotalBytes());
        body.put("fluidUsedUnits", m.fluidUsedUnits());
        body.put("fluidMaxUnits", m.fluidMaxUnits());
        body.put("reliable", m.reliable());
        body.put("available", m.available());
        return body;
    }
}
