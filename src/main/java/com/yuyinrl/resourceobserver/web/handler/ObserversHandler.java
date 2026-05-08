package com.yuyinrl.resourceobserver.web.handler;

import appeng.api.networking.IGrid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.auth.AuthContext;
import com.yuyinrl.resourceobserver.service.ObserverService;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.Ae2CellCapacityMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
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
        AccessResult<Map<String, Object>> res = runOnMainWithAccess(exchange, target,
                (mc, observer) -> buildDetailFromObserver(observer));
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

    private @Nullable Map<String, Object> buildDetailFromObserver(ObserverBlockEntity observer) {
        Map<String, Object> body = summarizeObserver(observer);
        if (body == null) return null;
        body.put("bindings", detailedBindings(observer));
        return body;
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
                entry.put("items", ae2Items(observer, binding.networkId()));
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

    private List<Map<String, Object>> ae2Items(ObserverBlockEntity observer, String networkId) {
        Map<String, Long> amounts = observer.getAe2ItemAmountsFor(networkId);
        Map<String, Double> prodRates = observer.getAe2ItemProdRatesPerMinFor(networkId);
        Map<String, Double> consRates = observer.getAe2ItemConsRatesPerMinFor(networkId);
        Map<String, Double> netRates = observer.getAe2ItemRatesPerMinFor(networkId);
        List<Map<String, Object>> out = new ArrayList<>(amounts.size());
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            String id = e.getKey();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("amount", e.getValue());
            row.put("production", prodRates.getOrDefault(id, 0.0d));
            row.put("consumption", consRates.getOrDefault(id, 0.0d));
            row.put("net", netRates.getOrDefault(id, 0.0d));
            // 注入翻译键 + 当前服务端语言下的显示名，供 Web 端按语言切换
            ItemNameResolver.Resolved r = ItemNameResolver.resolve(id);
            if (r.translationKey() != null) row.put("translationKey", r.translationKey());
            if (r.displayName() != null) row.put("displayName", r.displayName());
            out.add(row);
        }
        return out;
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
