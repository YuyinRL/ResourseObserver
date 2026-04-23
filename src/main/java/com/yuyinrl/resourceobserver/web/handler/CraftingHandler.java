package com.yuyinrl.resourceobserver.web.handler;

import appeng.api.networking.IGrid;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yuyinrl.resourceobserver.integration.CraftingDataCollector;
import com.yuyinrl.resourceobserver.integration.CraftingDataCollector.CraftableEntry;
import com.yuyinrl.resourceobserver.integration.CraftingDataCollector.CraftingJobEntry;
import com.yuyinrl.resourceobserver.integration.CraftingDataCollector.CraftingStorageMetrics;
import com.yuyinrl.resourceobserver.web.WebServerService;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity.BoundEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/observers/{dim}/{x}/{y}/{z}/crafting} —— 合成相关信息：
 * 可合成物品列表、活跃合成任务、存储容量汇总。
 */
public final class CraftingHandler extends BaseApiHandler implements HttpHandler {

    public CraftingHandler(WebServerService server) {
        super(server);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String trimmed = path.endsWith("/crafting")
                ? path.substring(0, path.length() - "/crafting".length())
                : path;
        ObserverTarget target = parseObserverPath(trimmed);
        if (target == null) {
            sendError(exchange, 404, "not found");
            return;
        }
        Map<String, Object> body = runOnMain(mc -> buildCraftingBody(mc, target));
        if (body == null) {
            sendError(exchange, 404, "observer not found");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private @Nullable Map<String, Object> buildCraftingBody(MinecraftServer mc, ObserverTarget target) {
        ServerLevel level = resolveLevel(mc, target.dimension());
        if (level == null) return null;
        BlockPos pos = target.pos();
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ObserverBlockEntity observer)) return null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dimension", target.dimension());
        body.put("x", pos.getX());
        body.put("y", pos.getY());
        body.put("z", pos.getZ());

        List<Map<String, Object>> networks = new ArrayList<>();
        for (BoundEntry binding : observer.getBindings()) {
            if (!"AE2_ITEMS".equals(binding.networkType())) continue;
            IGrid grid = observer.resolveAe2GridForNetwork(binding.networkId());
            if (grid == null) continue;

            Map<String, Object> net = new LinkedHashMap<>();
            net.put("networkId", binding.networkId());
            net.put("targetBlockId", binding.targetBlockId());

            List<CraftableEntry> craftables = CraftingDataCollector.collectCraftables(grid);
            net.put("craftables", toMaps(craftables));

            List<CraftingJobEntry> jobs = CraftingDataCollector.collectActiveJobs(grid);
            net.put("cpus", jobsToMaps(jobs));
            net.put("activeJobCount", jobs.stream().filter(CraftingJobEntry::busy).count());

            CraftingStorageMetrics metrics = CraftingDataCollector.collectStorageMetrics(grid);
            Map<String, Object> storage = new LinkedHashMap<>();
            storage.put("cpuCount", metrics.cpuCount());
            storage.put("busyCpuCount", metrics.busyCpuCount());
            storage.put("totalStorageBytes", metrics.totalStorageBytes());
            storage.put("totalCoProcessors", metrics.totalCoProcessors());
            storage.put("reliable", metrics.reliable());
            net.put("storage", storage);

            networks.add(net);
        }
        body.put("networks", networks);
        return body;
    }

    private List<Map<String, Object>> toMaps(List<CraftableEntry> entries) {
        List<Map<String, Object>> out = new ArrayList<>(entries.size());
        for (CraftableEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", e.itemId());
            row.put("displayName", e.displayName());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> jobsToMaps(List<CraftingJobEntry> jobs) {
        List<Map<String, Object>> out = new ArrayList<>(jobs.size());
        for (CraftingJobEntry j : jobs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cpuName", j.cpuName());
            row.put("busy", j.busy());
            row.put("outputItemId", j.outputItemId());
            row.put("outputDisplayName", j.outputDisplayName());
            row.put("totalAmount", j.totalAmount());
            row.put("remainingAmount", j.remainingAmount());
            row.put("jobId", j.jobId());
            out.add(row);
        }
        return out;
    }
}
