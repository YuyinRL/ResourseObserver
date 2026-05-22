package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshotBuilder;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.web.util.QueryUtil;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code GET /api/observers/{dim}/{x}/{y}/{z}/items}
 * <p>
 * 分页返回 AE2 物品快照，避免把大型网络的全部物品塞进 Observer detail。
 * 支持 offset/limit/q/sort/dir/alertsOnly，供 Web 侧列表与九宫格视图共用。
 */
public final class ItemsHandler extends BaseApiHandler implements HttpHandler {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    public ItemsHandler(WebServerService server) {
        super(server);
    }

    /**
     * /api/observers/{id}/items —— 物品分页/排序/筛选入口。
     * 解析 query 参数后切到主线程读取 Observer 数据并构造响应。
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String trimmed = path.endsWith("/items")
                ? path.substring(0, path.length() - "/items".length())
                : path;
        ObserverTarget target = parseObserverPath(trimmed);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        Map<String, String> query = QueryUtil.parseQuery(uri.getRawQuery());
        AccessResult<Map<String, Object>> res = runOnMainWithAccess(exchange, target,
                (mc, observer) -> buildBody(observer, query));
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

    private @Nullable Map<String, Object> buildBody(ObserverBlockEntity observer, Map<String, String> query) {
        int offset = Math.max(0, parseInt(query.get("offset"), 0));
        int limit = Math.max(1, Math.min(MAX_LIMIT, parseInt(query.get("limit"), DEFAULT_LIMIT)));
        String q = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        String sort = normalizeSort(query.getOrDefault("sort", "amount"));
        String dir = "asc".equalsIgnoreCase(query.get("dir")) ? "asc" : "desc";
        boolean alertsOnly = Boolean.parseBoolean(query.getOrDefault("alertsOnly", "false"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BoundEntry binding : observer.getBindings()) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                continue;
            }
            StorageSnapshot snapshot = StorageSnapshotBuilder.fromObserver(
                    observer,
                    binding.networkId(),
                    alertsOnly,
                    new HashMap<>(),
                    q,
                    ServerStorageLocalizer.INSTANCE,
                    StorageSnapshotBuilder.SearchMatcher.DEFAULT,
                    null
            );
            rows.addAll(itemRows(snapshot, binding.networkId()));
        }

        rows.sort(comparator(sort, dir));

        int total = rows.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<Map<String, Object>> page = new ArrayList<>(rows.subList(from, to));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("offset", offset);
        body.put("limit", limit);
        body.put("sort", sort);
        body.put("dir", dir);
        body.put("items", page);
        return body;
    }

    static List<Map<String, Object>> itemRows(StorageSnapshot snapshot, String networkId) {
        List<Map<String, Object>> rows = new ArrayList<>(snapshot.items().size());
        for (StorageSnapshot.ItemRowSnapshot item : snapshot.items()) {
            rows.add(itemRow(item, networkId));
        }
        return rows;
    }

    static Map<String, Object> itemRow(StorageSnapshot.ItemRowSnapshot item, String networkId) {
        ItemNameResolver.Resolved name = ItemNameResolver.resolve(item.itemId());
        double consumption = Math.max(0.0d, item.burnRatePerMin());
        double production = Math.max(0.0d, item.delta() + consumption);
        double remainingMinutes = item.localAmount() / Math.max(consumption, 1.0d);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.itemId());
        row.put("displayName", item.displayName());
        if (name.translationKey() != null) row.put("translationKey", name.translationKey());
        row.put("amount", item.localAmount());
        row.put("production", production);
        row.put("consumption", consumption);
        row.put("net", item.delta());
        row.put("capacity", Math.max(item.localAmount() * 1.5d, 1000.0d));
        row.put("remainingMinutes", remainingMinutes);
        row.put("networkId", networkId);
        row.put("localAmount", item.localAmount());
        row.put("globalAmount", item.globalAmount());
        row.put("delta", item.delta());
        row.put("alertLevel", item.alertLevel().name());
        row.put("groupKey", item.groupKey());
        row.put("burnRatePerMin", item.burnRatePerMin());
        row.put("estimatedBufferText", item.estimatedBufferText());
        row.put("bufferRatio", item.bufferRatio());
        row.put("iconSprite", item.iconSprite());
        return row;
    }

    static List<Map<String, Object>> kpis(StorageSnapshot snapshot) {
        List<Map<String, Object>> out = new ArrayList<>(snapshot.kpis().size());
        for (StorageSnapshot.KpiSnapshot kpi : snapshot.kpis()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", kpi.labelKey());
            row.put("value", kpi.value());
            row.put("status", kpi.status().name());
            out.add(row);
        }
        return out;
    }

    static List<Map<String, Object>> usageSegments(StorageSnapshot snapshot) {
        List<Map<String, Object>> out = new ArrayList<>(snapshot.usageSegments().size());
        for (StorageSnapshot.UsageSegmentSnapshot segment : snapshot.usageSegments()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupKey", segment.groupKey());
            row.put("displayName", segment.fallbackName());
            row.put("percentage", segment.percentage());
            row.put("color", colorForSlot(segment.colorSlot()));
            out.add(row);
        }
        return out;
    }

    static @Nullable Map<String, Object> nodeSummary(StorageSnapshot snapshot, String networkId) {
        for (StorageSnapshot.NodeSnapshot node : snapshot.nodes()) {
            if (!node.nodeId().equals(networkId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nodeId", node.nodeId());
            row.put("displayName", node.displayName());
            row.put("networkType", node.networkType());
            row.put("iconSprite", node.iconSprite());
            row.put("itemCount", node.itemCount());
            row.put("capacityRatio", node.capacityRatio());
            row.put("selected", node.selected());
            row.put("usedFormatted", node.usedFormatted());
            row.put("totalFormatted", node.totalFormatted());
            row.put("statusKey", node.statusKey());
            row.put("statusAlert", node.statusAlert());
            row.put("coordinatesText", node.coordinatesText());
            return row;
        }
        return null;
    }

    private static String colorForSlot(StorageSnapshot.GroupColorSlot slot) {
        return switch (slot) {
            case RAW -> "#06b6d4";
            case INTERMEDIATE -> "#f59e0b";
            case FINISHED -> "#10b981";
            case OTHER -> "#3b82f6";
        };
    }

    private static Comparator<Map<String, Object>> comparator(String sort, String dir) {
        Comparator<Map<String, Object>> cmp = switch (sort) {
            case "name" -> Comparator.comparing(row -> String.valueOf(row.get("displayName")), String.CASE_INSENSITIVE_ORDER);
            case "production", "consumption", "net", "remainingMinutes" -> Comparator.comparingDouble(row -> number(row.get(sort)));
            default -> Comparator.comparingDouble(row -> number(row.get("amount")));
        };
        return "asc".equals(dir) ? cmp : cmp.reversed();
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0d;
    }

    private static String normalizeSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "name", "production", "consumption", "net", "remainingMinutes" -> sort;
            default -> "amount";
        };
    }

    private static int parseInt(@Nullable String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
