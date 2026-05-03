package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.service.ObserverService;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.history.WebHighPrecisionSampler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/observers/{dim}/{x}/{y}/{z}/debug/sampler}
 * <p>
 * 暴露当前 Observer 的 Web 高精度采样层运行时状态，用于前端调试面板。
 * 仅返回内存内 ring buffer 的统计信息，不写入存档。
 */
public final class SamplerDebugHandler extends BaseApiHandler implements HttpHandler {

    public SamplerDebugHandler(WebServerService server) {
        super(server);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String suffix = "/debug/sampler";
        String trimmed = path.endsWith(suffix)
                ? path.substring(0, path.length() - suffix.length())
                : path;
        ObserverTarget target = parseObserverPath(trimmed);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        Map<String, Object> body = runOnMain(mc -> buildBody(mc, target));
        if (body == null) {
            sendError(exchange, 404, "observer not found");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private @Nullable Map<String, Object> buildBody(MinecraftServer mc, ObserverTarget target) {
        ServerLevel level = resolveLevel(mc, target.dimension());
        if (level == null) return null;
        if (ObserverService.findByPos(level, target.pos()).isEmpty()) return null;

        WebHighPrecisionSampler.Diagnostics diag =
                WebHighPrecisionSampler.diagnostics(level, target.pos());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestedIntervalTicks", diag.requestedIntervalTicks());
        body.put("active", diag.requestedIntervalTicks() > 0);
        body.put("globalDemandActive", diag.globalDemandActive());
        body.put("itemDemandActive", diag.itemDemandActive());
        body.put("demandRemainingMs", diag.demandRemainingMs());
        body.put("latestBucket", diag.latestBucket());
        body.put("bufferBuckets", diag.bufferBuckets());
        body.put("bucketTicks", diag.bucketTicks());
        body.put("minValidBuckets", diag.minValidBuckets());
        body.put("gameTime", level.getGameTime());
        body.put("serverTimeMs", System.currentTimeMillis());

        List<Map<String, Object>> networks = new ArrayList<>();
        for (WebHighPrecisionSampler.NetworkDiagnostics net : diag.networks()) {
            Map<String, Object> netBody = new LinkedHashMap<>();
            netBody.put("networkId", net.networkId());
            netBody.put("validBuckets", net.validBuckets());
            netBody.put("totalSamples", net.totalSamples());
            netBody.put("lastSampleGameTime", net.lastSampleGameTime());
            netBody.put("previousSnapshotSize", net.previousSnapshotSize());
            List<Map<String, Object>> recent = new ArrayList<>();
            for (WebHighPrecisionSampler.RecentBucket rb : net.recentBuckets()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucket", rb.bucket());
                row.put("sampleCount", rb.sampleCount());
                row.put("observedTicks", rb.observedTicks());
                row.put("producedPerMinute", rb.producedPerMinute());
                row.put("consumedPerMinute", rb.consumedPerMinute());
                row.put("stock", rb.stock());
                recent.add(row);
            }
            netBody.put("recentBuckets", recent);
            networks.add(netBody);
        }
        body.put("networks", networks);
        return body;
    }
}
