package com.yuyinrl.resourceobserver.web.handler;

import appeng.api.networking.IGrid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.integration.CraftingOrderService;
import com.yuyinrl.resourceobserver.integration.CraftingOrderService.PlanResult;
import com.yuyinrl.resourceobserver.service.ObserverService;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity.BoundEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code POST /api/observers/{dim}/{x}/{y}/{z}/crafting/plan}    —— 计算合成计划摘要。
 * {@code POST /api/observers/{dim}/{x}/{y}/{z}/crafting/confirm} —— 用 planId 提交。
 * {@code POST /api/observers/{dim}/{x}/{y}/{z}/crafting/cancel}  —— 主动丢弃缓存。
 */
public final class CraftingOrderHandler extends BaseApiHandler implements HttpHandler {

    private static final Pattern STRING_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?\\d+)");

    public CraftingOrderHandler(WebServerService server) {
        super(server);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String suffix = matchedSuffix(path);
        if (suffix == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        String trimmed = path.substring(0, path.length() - suffix.length());
        ObserverTarget target = parseObserverPath(trimmed);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        Map<String, String> fields = parseBody(exchange);
        switch (suffix) {
            case "/crafting/plan" -> handlePlan(exchange, target, fields);
            case "/crafting/confirm" -> handleConfirm(exchange, fields);
            case "/crafting/cancel" -> handleCancel(exchange, fields);
            case "/crafting/tree" -> handleTree(exchange, fields);
            default -> sendError(exchange, 404, "not found");
        }
    }

    /** 查询合成树详情。 */
    private void handleTree(HttpExchange exchange, Map<String, String> fields) throws IOException {
        String treeId = fields.getOrDefault("treeId", "");
        if (treeId.isEmpty()) {
            sendError(exchange, 400, "missing treeId");
            return;
        }
        com.yuyinrl.resourceobserver.integration.CraftingTreeNode root =
                runOnMain(mc -> CraftingOrderService.lookupTree(treeId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("treeId", treeId);
        body.put("root", root == null ? null : treeToMap(root));
        sendJson(exchange, root == null ? 404 : 200, body);
    }

    private static Map<String, Object> treeToMap(com.yuyinrl.resourceobserver.integration.CraftingTreeNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", n.itemId());
        m.put("entryType", entryType(n.itemId()));
        ItemNameResolver.Resolved r = ItemNameResolver.resolve(n.itemId());
        String dn = r.displayName() != null && !r.displayName().isEmpty() ? r.displayName() : n.displayName();
        m.put("displayName", dn);
        if (r.translationKey() != null) m.put("translationKey", r.translationKey());
        m.put("requiredAmount", n.requiredAmount());
        m.put("perExecOutAmount", n.perExecOutAmount());
        m.put("timesExecuted", n.timesExecuted());
        m.put("isMissing", n.isMissing());
        m.put("isLoop", n.isLoop());
        m.put("truncated", n.truncated());
        List<Map<String, Object>> kids = new ArrayList<>();
        for (var c : n.children()) kids.add(treeToMap(c));
        m.put("children", kids);
        return m;
    }

    /** 计算计划摘要。 */
    private void handlePlan(HttpExchange exchange, ObserverTarget target, Map<String, String> fields) throws IOException {
        String networkId = fields.getOrDefault("networkId", "");
        String itemId = fields.getOrDefault("itemId", "");
        long amount = parseLongOrDefault(fields.get("amount"), 0L);
        if (networkId.isEmpty() || itemId.isEmpty() || amount <= 0L) {
            sendError(exchange, 400, "missing networkId/itemId/amount");
            return;
        }
        CompletableFuture<PlanResult> fut = runOnMain(mc -> startPlanOnMain(mc, target, networkId, itemId, amount));
        if (fut == null) {
            sendError(exchange, 504, "main thread timeout");
            return;
        }
        PlanResult plan;
        try {
            plan = fut.get(12_000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            plan = PlanResult.fail(CraftingOrderService.Status.CALCULATION_TIMEOUT, "wait timed out");
        } catch (Exception e) {
            sendError(exchange, 500, "plan future failed: " + e.getMessage());
            return;
        }
        sendJson(exchange, plan.simulation() || plan.status() != CraftingOrderService.Status.SUCCESS ? 200 : 200,
                planToMap(plan));
    }

    /** 用 planId 提交。 */
    private void handleConfirm(HttpExchange exchange, Map<String, String> fields) throws IOException {
        String planId = fields.getOrDefault("planId", "");
        if (planId.isEmpty()) {
            sendError(exchange, 400, "missing planId");
            return;
        }
        String cpu = fields.getOrDefault("cpu", "");
        String cpuName = cpu.isBlank() ? null : cpu;
        CraftingOrderService.SubmitResult res = runOnMain(mc -> CraftingOrderService.confirmOrder(planId, cpuName));
        if (res == null) {
            sendError(exchange, 504, "main thread timeout");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", res.ok());
        body.put("status", res.status().name());
        body.put("message", res.message() == null ? "" : res.message());
        body.put("linkId", res.linkId() == null ? "" : res.linkId());
        sendJson(exchange, res.ok() ? 200 : 409, body);
    }

    private void handleCancel(HttpExchange exchange, Map<String, String> fields) throws IOException {
        String planId = fields.getOrDefault("planId", "");
        if (!planId.isEmpty()) {
            CraftingOrderService.cancelPlan(planId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        sendJson(exchange, 200, body);
    }

    private CompletableFuture<PlanResult> startPlanOnMain(MinecraftServer mc, ObserverTarget target,
                                                          String networkId, String itemId, long amount) {
        CompletableFuture<PlanResult> out = new CompletableFuture<>();
        ServerLevel level = resolveLevel(mc, target.dimension());
        if (level == null) {
            out.complete(PlanResult.fail(CraftingOrderService.Status.GRID_UNAVAILABLE, "level not loaded"));
            return out;
        }
        BlockPos pos = target.pos();
        if (!level.isLoaded(pos)) {
            out.complete(PlanResult.fail(CraftingOrderService.Status.GRID_UNAVAILABLE, "observer chunk not loaded"));
            return out;
        }
        ObserverBlockEntity observer = ObserverService.findByPos(level, pos).orElse(null);
        if (observer == null) {
            out.complete(PlanResult.fail(CraftingOrderService.Status.GRID_UNAVAILABLE, "observer not found"));
            return out;
        }
        BoundEntry binding = null;
        for (BoundEntry b : observer.getBindings()) {
            if ("AE2_ITEMS".equals(b.networkType()) && networkId.equals(b.networkId())) {
                binding = b;
                break;
            }
        }
        if (binding == null) {
            out.complete(PlanResult.fail(CraftingOrderService.Status.GRID_UNAVAILABLE,
                    "AE2 binding not found: " + networkId));
            return out;
        }
        IGrid grid = observer.resolveAe2GridForNetwork(binding.networkId());
        CraftingOrderService.planOrderAsync(level, grid, itemId, amount, out::complete);
        return out;
    }

    private static Map<String, Object> planToMap(PlanResult plan) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", plan.status() == CraftingOrderService.Status.SUCCESS && !plan.simulation());
        body.put("status", plan.status().name());
        body.put("message", plan.message() == null ? "" : plan.message());
        body.put("planId", plan.planId() == null ? "" : plan.planId());
        body.put("simulation", plan.simulation());
        body.put("bytes", plan.bytes());
        body.put("finalOutputItemId", plan.finalOutputItemId() == null ? "" : plan.finalOutputItemId());
        ItemNameResolver.Resolved foRes = ItemNameResolver.resolve(plan.finalOutputItemId());
        String foDn = foRes.displayName() != null && !foRes.displayName().isEmpty()
                ? foRes.displayName()
                : (plan.finalOutputDisplayName() == null ? "" : plan.finalOutputDisplayName());
        body.put("finalOutputDisplayName", foDn);
        if (foRes.translationKey() != null) body.put("finalOutputTranslationKey", foRes.translationKey());
        body.put("finalOutputAmount", plan.finalOutputAmount());
        body.put("usedItems", stacksToMaps(plan.usedItems()));
        body.put("missingItems", stacksToMaps(plan.missingItems()));
        body.put("emittedItems", stacksToMaps(plan.emittedItems()));
        body.put("patternTimes", stacksToMaps(plan.patternTimes()));
        body.put("cpus", cpusToMaps(plan.cpus()));
        body.put("treeId", plan.treeId() == null ? "" : plan.treeId());
        body.put("tree", plan.treeRoot() == null ? null : treeToMap(plan.treeRoot()));
        return body;
    }

    private static List<Map<String, Object>> stacksToMaps(List<CraftingOrderService.Stack> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (var s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", s.itemId());
            m.put("entryType", entryType(s.itemId()));
            ItemNameResolver.Resolved r = ItemNameResolver.resolve(s.itemId());
            String dn = r.displayName() != null && !r.displayName().isEmpty() ? r.displayName() : s.displayName();
            m.put("displayName", dn);
            if (r.translationKey() != null) m.put("translationKey", r.translationKey());
            m.put("amount", s.amount());
            out.add(m);
        }
        return out;
    }

    private static String entryType(String itemId) {
        return itemId != null && itemId.startsWith("fluid:") ? "FLUID" : "ITEM";
    }

    private static List<Map<String, Object>> cpusToMaps(List<CraftingOrderService.CpuInfo> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (var c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("storageBytes", c.storageBytes());
            m.put("coProcessors", c.coProcessors());
            m.put("busy", c.busy());
            out.add(m);
        }
        return out;
    }

    private static String matchedSuffix(String path) {
        for (String s : new String[]{"/crafting/plan", "/crafting/confirm", "/crafting/cancel", "/crafting/tree"}) {
            if (path.endsWith(s)) return s;
        }
        return null;
    }

    private static Map<String, String> parseBody(HttpExchange exchange) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream is = exchange.getRequestBody()) {
            byte[] data = is.readAllBytes();
            String text = new String(data, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return map;
            Matcher s = STRING_FIELD.matcher(text);
            while (s.find()) map.put(s.group(1), s.group(2));
            Matcher n = NUMBER_FIELD.matcher(text);
            while (n.find()) map.putIfAbsent(n.group(1), n.group(2));
        }
        return map;
    }

    private static long parseLongOrDefault(String s, long fallback) {
        if (s == null) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
