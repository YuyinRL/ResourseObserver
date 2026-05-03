package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.ObserverService;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.util.QueryUtil;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.history.HistoryRecorder;
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
 * {@code GET /api/observers/{dim}/{x}/{y}/{z}/history?range=detail|short|medium|long[&item=<id>]}
 * <p>
 * 返回时间序列数据点，用于 Web 仪表盘的资源流量趋势图。range 对应三档时间颗粒度：
 * <ul>
 *   <li>detail → {@link ChartWindow#WEB_DETAIL_1M_1S}（1 分钟，每 1 秒一点）</li>
 *   <li>short  → {@link ChartWindow#WEB_SHORT_1M_10S}（1 分钟，每 10 秒一点）</li>
 *   <li>medium → {@link ChartWindow#WEB_MEDIUM_15M_1M}（15 分钟，每 1 分钟一点）</li>
 *   <li>long   → {@link ChartWindow#WEB_LONG_1H_5M}（1 小时，每 5 分钟一点）</li>
 * </ul>
 * 若提供 {@code item}，返回该物品的子序列（ChartScope.ITEM）；否则返回全网络合计。
 */
public final class HistoryHandler extends BaseApiHandler implements HttpHandler {
    /** Observer 采样间隔，与 ObserverBlockEntity 的服务端采样节奏保持一致。 */
    private static final long SAMPLE_INTERVAL_TICKS = 10L;

    public HistoryHandler(WebServerService server) {
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
        String trimmed = path.endsWith("/history")
                ? path.substring(0, path.length() - "/history".length())
                : path;
        ObserverTarget target = parseObserverPath(trimmed);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        Map<String, String> query = QueryUtil.parseQuery(uri.getRawQuery());
        ChartWindow visibleWindow = mapRange(query.getOrDefault("range", "short"));
        ChartWindow window = bufferWindow(visibleWindow);
        String itemId = query.get("item");
        ChartScope scope = (itemId != null && !itemId.isBlank()) ? ChartScope.ITEM : ChartScope.GLOBAL;

        Map<String, Object> body = runOnMain(mc -> buildBody(mc, target, window, visibleWindow, scope, itemId));
        if (body == null) {
            sendError(exchange, 404, "observer not found");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private @Nullable Map<String, Object> buildBody(MinecraftServer mc, ObserverTarget target,
                                                    ChartWindow window, ChartWindow visibleWindow, ChartScope scope,
                                                    @Nullable String itemId) {
        ServerLevel level = resolveLevel(mc, target.dimension());
        if (level == null) return null;
        ObserverBlockEntity observer = ObserverService.findByPos(level, target.pos()).orElse(null);
        if (observer == null) return null;

        List<ObserverBlockEntity.BoundEntry> bindings = itemChartBindings(observer.getBindings());
        if (visibleWindow == ChartWindow.WEB_DETAIL_1M_1S) {
            WebHighPrecisionSampler.registerDemand(level, target.pos(), itemId);
            WebHighPrecisionSampler.QueryResult highPrecision =
                    WebHighPrecisionSampler.query(level, target.pos(), bindings, itemId);
            if (highPrecision != null) {
                return buildHighPrecisionBody(level, window, visibleWindow, scope, itemId, highPrecision);
            }
        }
        List<ObserverDataPayload.ChartPoint> points =
                HistoryRecorder.querySeries(level, target.pos(), bindings, window, scope, itemId);

        long gameTime = level.getGameTime();
        long latestBucket = latestValidBucket(points);
        // 一个 tick = 0.05 秒
        double bucketSeconds = window.bucketTicks() * 0.05d;

        List<Map<String, Object>> jsonPoints = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            ObserverDataPayload.ChartPoint p = points.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("t", p.slotIndex());
            long bucket = p.bucket();
            if (bucket == Long.MIN_VALUE) {
                continue;
            }
            double bucketEndSeconds = (bucket + 1L) * bucketSeconds;
            double producedPerMinute = toStableRatePerMinute(points, i, window, true);
            double consumedPerMinute = toStableRatePerMinute(points, i, window, false);
            row.put("bucket", bucket);
            row.put("x", Math.min(bucketEndSeconds, gameTime * 0.05d));
            row.put("produced", producedPerMinute);
            row.put("consumed", consumedPerMinute);
            row.put("net", producedPerMinute - consumedPerMinute);
            row.put("stock", p.stock());
            row.put("hasFlow", p.hasFlow());
            row.put("hasStock", p.hasStock());
            row.put("sampleCount", p.sampleCount());
            jsonPoints.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("range", rangeLabel(visibleWindow));
        body.put("bucketCount", window.bucketCount());
        body.put("visibleBucketCount", visibleWindow.bucketCount());
        body.put("bufferMultiplier", Math.max(1, window.bucketCount() / Math.max(1, visibleWindow.bucketCount())));
        body.put("bucketSeconds", bucketSeconds);
        body.put("gameTime", gameTime);
        body.put("latestBucket", latestBucket);
        body.put("serverTimeMs", System.currentTimeMillis());
        body.put("points", jsonPoints);
        body.put("scope", scope == ChartScope.ITEM ? "item" : "global");
        if (itemId != null && !itemId.isBlank()) body.put("itemId", itemId);
        return body;
    }

    private Map<String, Object> buildHighPrecisionBody(ServerLevel level,
                                                       ChartWindow window,
                                                       ChartWindow visibleWindow,
                                                       ChartScope scope,
                                                       @Nullable String itemId,
                                                       WebHighPrecisionSampler.QueryResult highPrecision) {
        long gameTime = level.getGameTime();
        double bucketSeconds = WebHighPrecisionSampler.BUCKET_SECONDS;

        // 滑动窗口求和：把"单 bucket 增量÷间隔"换成"前 N 秒累计÷N"，消除离散事件混叠。
        // detail 档（0.25s 桶）用 3 秒窗 = 12 桶；事件周期更长时再调大。
        final double rateWindowSeconds = 3.0d;
        final int rateWindowBuckets = Math.max(1, (int) Math.round(rateWindowSeconds / bucketSeconds));

        List<WebHighPrecisionSampler.Point> rawPoints = highPrecision.points();
        long[] sumProd = new long[rawPoints.size()];
        long[] sumCons = new long[rawPoints.size()];
        long[] sumTicks = new long[rawPoints.size()];
        long runProd = 0L;
        long runCons = 0L;
        long runTicks = 0L;
        for (int i = 0; i < rawPoints.size(); i++) {
            WebHighPrecisionSampler.Point p = rawPoints.get(i);
            runProd += p.producedRaw();
            runCons += p.consumedRaw();
            runTicks += p.observedTicks();
            int from = i - rateWindowBuckets;
            if (from >= 0) {
                WebHighPrecisionSampler.Point old = rawPoints.get(from);
                runProd -= old.producedRaw();
                runCons -= old.consumedRaw();
                runTicks -= old.observedTicks();
            }
            sumProd[i] = runProd;
            sumCons[i] = runCons;
            sumTicks[i] = runTicks;
        }

        List<Map<String, Object>> jsonPoints = new ArrayList<>(rawPoints.size());
        for (int i = 0; i < rawPoints.size(); i++) {
            WebHighPrecisionSampler.Point point = rawPoints.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            double bucketEndSeconds = (point.bucket() + 1L) * bucketSeconds;
            double producedRate = sumTicks[i] > 0L ? (sumProd[i] / (double) sumTicks[i]) * 1200.0d : 0.0d;
            double consumedRate = sumTicks[i] > 0L ? (sumCons[i] / (double) sumTicks[i]) * 1200.0d : 0.0d;
            row.put("t", point.slotIndex());
            row.put("bucket", point.bucket());
            row.put("x", Math.min(bucketEndSeconds, gameTime * 0.05d));
            row.put("produced", producedRate);
            row.put("consumed", consumedRate);
            row.put("net", producedRate - consumedRate);
            row.put("stock", point.stock());
            // hasFlow 改用窗口内是否有效采样判断：单 bucket 没事件也算"有数据"。
            boolean hasFlow = sumTicks[i] > 0L;
            row.put("hasFlow", hasFlow);
            row.put("hasStock", point.hasStock());
            row.put("sampleCount", point.sampleCount());
            row.put("highPrecision", true);
            jsonPoints.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("range", rangeLabel(visibleWindow));
        // 高精度档 bucket 已缩到 0.25s，需要按真实秒数重新换算可见/缓冲格数。
        double visibleSeconds = visibleWindow.bucketCount() * visibleWindow.bucketTicks() * 0.05d;
        double bufferedSeconds = window.bucketCount() * window.bucketTicks() * 0.05d;
        int visibleBucketCount = Math.max(1, (int) Math.round(visibleSeconds / bucketSeconds));
        int bufferedBucketCount = Math.max(visibleBucketCount, (int) Math.round(bufferedSeconds / bucketSeconds));
        body.put("bucketCount", bufferedBucketCount);
        body.put("visibleBucketCount", visibleBucketCount);
        body.put("bufferMultiplier", Math.max(1, bufferedBucketCount / Math.max(1, visibleBucketCount)));
        body.put("bucketSeconds", bucketSeconds);
        body.put("rateWindowSeconds", rateWindowSeconds);
        body.put("gameTime", gameTime);
        body.put("latestBucket", highPrecision.latestBucket());
        body.put("serverTimeMs", System.currentTimeMillis());
        body.put("points", jsonPoints);
        body.put("scope", scope == ChartScope.ITEM ? "item" : "global");
        body.put("highPrecision", true);
        if (itemId != null && !itemId.isBlank()) body.put("itemId", itemId);
        return body;
    }

    private static long latestValidBucket(List<ObserverDataPayload.ChartPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            long bucket = points.get(i).bucket();
            if (bucket != Long.MIN_VALUE) {
                return bucket;
            }
        }
        return Long.MIN_VALUE;
    }

    private static ChartWindow mapRange(String range) {
        return switch (range == null ? "" : range.toLowerCase()) {
            case "detail" -> ChartWindow.WEB_DETAIL_1M_1S;
            case "medium" -> ChartWindow.WEB_MEDIUM_15M_1M;
            case "long" -> ChartWindow.WEB_LONG_1H_5M;
            default -> ChartWindow.WEB_SHORT_1M_10S;
        };
    }

    private static ChartWindow bufferWindow(ChartWindow visibleWindow) {
        return switch (visibleWindow) {
            case WEB_DETAIL_1M_1S -> ChartWindow.WEB_DETAIL_BUFFER_5M_1S;
            case WEB_MEDIUM_15M_1M -> ChartWindow.WEB_MEDIUM_BUFFER_75M_1M;
            case WEB_LONG_1H_5M -> ChartWindow.WEB_LONG_BUFFER_5H_5M;
            default -> ChartWindow.WEB_SHORT_BUFFER_5M_10S;
        };
    }

    /** Web Overview 的资源曲线只聚合物品/流体网络，避免能源网络污染物品流转曲线。 */
    private static List<ObserverBlockEntity.BoundEntry> itemChartBindings(List<ObserverBlockEntity.BoundEntry> bindings) {
        List<ObserverBlockEntity.BoundEntry> out = new ArrayList<>();
        for (ObserverBlockEntity.BoundEntry binding : bindings) {
            if (!"FLUX_ENERGY".equals(binding.networkType()) && !"MEK_ENERGY".equals(binding.networkType())) {
                out.add(binding);
            }
        }
        return out;
    }

    private static String rangeLabel(ChartWindow w) {
        return switch (w) {
            case WEB_DETAIL_1M_1S -> "detail";
            case WEB_MEDIUM_15M_1M -> "medium";
            case WEB_LONG_1H_5M -> "long";
            default -> "short";
        };
    }

    /** 将 bucket 内累计增量标准化为每分钟速率，保持 Web 各时间颗粒度纵轴口径一致。 */
    private static double toRatePerMinute(double total, ChartWindow window, int sampleCount) {
        if (total <= 0.0d || window.bucketTicks() <= 0L) {
            return 0.0d;
        }
        long observedTicks = sampleCount > 0
                ? Math.min(window.bucketTicks(), sampleCount * SAMPLE_INTERVAL_TICKS)
                : window.bucketTicks();
        return (total / observedTicks) * 1200.0d;
    }

    private static double toStableRatePerMinute(
            List<ObserverDataPayload.ChartPoint> points,
            int index,
            ChartWindow window,
            boolean production
    ) {
        ObserverDataPayload.ChartPoint point = points.get(index);
        if (window != ChartWindow.WEB_DETAIL_BUFFER_5M_1S) {
            return toRatePerMinute(production ? point.production() : point.consumption(), window, point.sampleCount());
        }

        double total = 0.0d;
        long observedTicks = 0L;
        int from = Math.max(0, index - 2);
        int to = Math.min(points.size() - 1, index + 2);
        for (int i = from; i <= to; i++) {
            ObserverDataPayload.ChartPoint candidate = points.get(i);
            if (!candidate.hasFlow() || candidate.sampleCount() <= 0) {
                continue;
            }
            total += production ? candidate.production() : candidate.consumption();
            observedTicks += Math.min(window.bucketTicks(), candidate.sampleCount() * SAMPLE_INTERVAL_TICKS);
        }
        if (total <= 0.0d || observedTicks <= 0L) {
            return 0.0d;
        }
        return (total / observedTicks) * 1200.0d;
    }
}
