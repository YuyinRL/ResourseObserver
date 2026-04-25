package com.yuyinrl.resourceobserver.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
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
        Map<String, String> query = parseQuery(uri.getRawQuery());
        Map<String, Object> body = runOnMain(mc -> buildBody(mc, target, query));
        if (body == null) {
            sendError(exchange, 404, "observer not found");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private @Nullable Map<String, Object> buildBody(MinecraftServer mc, ObserverTarget target,
                                                    Map<String, String> query) {
        ServerLevel level = resolveLevel(mc, target.dimension());
        if (level == null) return null;
        BlockEntity raw = level.getBlockEntity(target.pos());
        if (!(raw instanceof ObserverBlockEntity observer)) return null;

        int offset = Math.max(0, parseInt(query.get("offset"), 0));
        int limit = Math.max(1, Math.min(MAX_LIMIT, parseInt(query.get("limit"), DEFAULT_LIMIT)));
        String q = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        String sort = normalizeSort(query.getOrDefault("sort", "amount"));
        String dir = "asc".equalsIgnoreCase(query.get("dir")) ? "asc" : "desc";
        boolean alertsOnly = Boolean.parseBoolean(query.getOrDefault("alertsOnly", "false"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ObserverBlockEntity.BoundEntry binding : observer.getBindings()) {
            if (!"AE2_ITEMS".equals(binding.networkType())) {
                continue;
            }
            appendRows(observer, binding.networkId(), q, alertsOnly, rows);
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

    private static void appendRows(ObserverBlockEntity observer, String networkId, String q, boolean alertsOnly,
                                   List<Map<String, Object>> rows) {
        Map<String, Long> amounts = observer.getAe2ItemAmountsFor(networkId);
        Map<String, Double> prodRates = observer.getAe2ItemProdRatesPerMinFor(networkId);
        Map<String, Double> consRates = observer.getAe2ItemConsRatesPerMinFor(networkId);
        Map<String, Double> netRates = observer.getAe2ItemRatesPerMinFor(networkId);

        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            String id = e.getKey();
            ItemNameResolver.Resolved name = ItemNameResolver.resolve(id);
            String displayName = name.displayName() == null ? id : name.displayName();
            if (!q.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(q)
                    && !displayName.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }

            double production = prodRates.getOrDefault(id, 0.0d);
            double consumption = consRates.getOrDefault(id, 0.0d);
            double net = netRates.getOrDefault(id, 0.0d);
            long amount = e.getValue();
            double remainingMinutes = amount / Math.max(consumption, 1.0d);
            if (alertsOnly && remainingMinutes >= 30.0d) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("displayName", displayName);
            if (name.translationKey() != null) row.put("translationKey", name.translationKey());
            row.put("amount", amount);
            row.put("production", production);
            row.put("consumption", consumption);
            row.put("net", net);
            row.put("capacity", Math.max(amount * 1.5d, 1000.0d));
            row.put("remainingMinutes", remainingMinutes);
            row.put("networkId", networkId);
            rows.add(row);
        }
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

    private static Map<String, String> parseQuery(@Nullable String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(urlDecode(pair), "");
            } else {
                out.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
            }
        }
        return out;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
