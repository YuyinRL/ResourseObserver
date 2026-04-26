package com.yuyinrl.resourceobserver.integration;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AE2 合成数据采集器 —— 直接调用 AE2 公共 API 读取合成相关信息。
 * <p>
 * AE2 是 required 依赖，无需反射隔离。所有静态方法在 server 主线程调用即可。
 * 采集项：
 * <ul>
 *   <li>{@link #collectCraftables(IGrid)} 列出网络内可合成物品</li>
 *   <li>{@link #collectActiveJobs(IGrid)} 列出当前所有 CPU 及其任务状态（含每 CPU 的存储/协处理器）</li>
 *   <li>{@link #collectStorageMetrics(IGrid)} 汇总合成 CPU 的存储容量指标</li>
 * </ul>
 */
public final class CraftingDataCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingDataCollector.class);

    /** 可合成物品枚举上限，防止 Patter 特别多时阻塞主线程。 */
    private static final int MAX_CRAFTABLES = 1024;

    /** 一次性日志去重。 */
    private static final Set<String> LOGGED_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private CraftingDataCollector() {
    }

    /** 单个可合成物品条目。 */
    public record CraftableEntry(String itemId, String displayName) {
    }

    /**
     * 单个 CPU 的合成任务信息（无任务时也会返回，便于 UI 展示总槽位）。
     *
     * @param cpuName           CPU 名称（无名时返回 {@code "CPU#N"}）
     * @param outputItemId      任务目标物品 ID（{@code null} 表示空闲）
     * @param outputDisplayName 目标物品显示名
     * @param totalAmount       任务初始总数量
     * @param remainingAmount   剩余未完成数量
     * @param busy              CPU 是否繁忙
     * @param jobId             任务 UUID 字符串（无则空）
     * @param storageBytes      CPU 可用存储字节
     * @param coProcessors      协处理器数
     */
    public record CraftingJobEntry(
            String cpuName,
            @Nullable String outputItemId,
            @Nullable String outputDisplayName,
            long totalAmount,
            long remainingAmount,
            boolean busy,
            @Nullable String jobId,
            long storageBytes,
            int coProcessors,
            // AE2 内部 progress 字段是相对 Integer.MAX_VALUE 的标定值，原始数字无展示意义；
            // 这里直接记录 0..1 的进度比例，由 UI 渲染百分比/条形进度。
            double progressFraction,
            // 计算自 status.elapsedTimeNanos()。
            long elapsedMillis,
            // 关联到 CraftingOrderService 的合成树缓存 ID；空表示该任务不是通过本模组下单的。
            @Nullable String treeId
    ) {
    }

    /** 合成存储容量汇总（按 CPU 聚合）。 */
    public record CraftingStorageMetrics(
            int cpuCount,
            int busyCpuCount,
            long totalStorageBytes,
            int totalCoProcessors,
            boolean reliable
    ) {
        public static CraftingStorageMetrics empty() {
            return new CraftingStorageMetrics(0, 0, 0L, 0, true);
        }
    }

    /** 可合成物品列表（按 AEItemKey 去重，按显示名稳定排序）。 */
    public static List<CraftableEntry> collectCraftables(@Nullable IGrid grid) {
        ICraftingService service = getService(grid);
        if (service == null) return Collections.emptyList();
        try {
            // AE2 1.21.1 的 getCraftables 接收 AEKeyFilter，旧实现误用 Predicate 因此始终返回空 —— 修复。
            AEKeyFilter filter = key -> key instanceof AEItemKey;
            Set<AEKey> raw = service.getCraftables(filter);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<CraftableEntry> out = new ArrayList<>(Math.min(raw.size(), MAX_CRAFTABLES));
            int limit = 0;
            for (AEKey key : raw) {
                if (!(key instanceof AEItemKey ik)) continue;
                Item item = ik.getItem();
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String itemId = id == null ? "unknown" : id.toString();
                String name = new ItemStack(item).getHoverName().getString();
                out.add(new CraftableEntry(itemId, name));
                if (++limit >= MAX_CRAFTABLES) break;
            }
            out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
            return out;
        } catch (Throwable t) {
            logOnce("getCraftables", t);
            return Collections.emptyList();
        }
    }

    /** 活跃合成任务（未忙碌 CPU 也会返回条目，便于 UI 展示总槽位）。 */
    public static List<CraftingJobEntry> collectActiveJobs(@Nullable IGrid grid) {
        ICraftingService service = getService(grid);
        if (service == null) return Collections.emptyList();
        Collection<? extends ICraftingCPU> cpus = safeCpus(service);
        if (cpus.isEmpty()) return Collections.emptyList();
        List<CraftingJobEntry> out = new ArrayList<>(cpus.size());
        int index = 0;
        for (ICraftingCPU cpu : cpus) {
            index++;
            out.add(readCpuJob(cpu, index));
        }
        return out;
    }

    /** 合成 CPU 的存储字节与协处理器汇总。 */
    public static CraftingStorageMetrics collectStorageMetrics(@Nullable IGrid grid) {
        ICraftingService service = getService(grid);
        if (service == null) return CraftingStorageMetrics.empty();
        Collection<? extends ICraftingCPU> cpus = safeCpus(service);
        if (cpus.isEmpty()) return CraftingStorageMetrics.empty();
        int busy = 0;
        long totalStorage = 0L;
        int totalCoProc = 0;
        for (ICraftingCPU cpu : cpus) {
            try {
                if (cpu.isBusy()) busy++;
                totalStorage += cpu.getAvailableStorage();
                totalCoProc += cpu.getCoProcessors();
            } catch (Throwable t) {
                logOnce("cpu.metrics", t);
                return new CraftingStorageMetrics(cpus.size(), busy, totalStorage, totalCoProc, false);
            }
        }
        return new CraftingStorageMetrics(cpus.size(), busy, totalStorage, totalCoProc, true);
    }

    // ======================= 内部工具 =======================

    private static @Nullable ICraftingService getService(@Nullable IGrid grid) {
        if (grid == null) return null;
        try {
            return grid.getCraftingService();
        } catch (Throwable t) {
            logOnce("getCraftingService", t);
            return null;
        }
    }

    private static Collection<? extends ICraftingCPU> safeCpus(ICraftingService service) {
        try {
            return service.getCpus();
        } catch (Throwable t) {
            logOnce("getCpus", t);
            return Collections.emptyList();
        }
    }

    private static CraftingJobEntry readCpuJob(ICraftingCPU cpu, int index) {
        String name = readCpuName(cpu, index);
        boolean busy = false;
        long storageBytes = 0L;
        int coProc = 0;
        try {
            busy = cpu.isBusy();
            storageBytes = cpu.getAvailableStorage();
            coProc = cpu.getCoProcessors();
        } catch (Throwable t) {
            logOnce("cpu.basic", t);
        }

        String outputItemId = null;
        String outputName = null;
        long total = 0L;
        long remaining = 0L;
        double progressFraction = 0.0;
        long elapsedMillis = 0L;
        try {
            CraftingJobStatus status = cpu.getJobStatus();
            if (status != null) {
                GenericStack stack = status.crafting();
                if (stack != null && stack.what() instanceof AEItemKey ik) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(ik.getItem());
                    outputItemId = id == null ? "unknown" : id.toString();
                    outputName = new ItemStack(ik.getItem()).getHoverName().getString();
                }
                // status.crafting().amount() 是真实剩余的终产物数量；
                // status.totalItems()/progress() 是 ElapsedTimeTracker 的 deprecated 方法，
                // total 永远是 Integer.MAX_VALUE，比例才有意义。
                long totalScaled = status.totalItems();
                long progressScaled = status.progress();
                if (totalScaled > 0L) {
                    progressFraction = Math.max(0.0, Math.min(1.0,
                            (double) progressScaled / (double) totalScaled));
                }
                remaining = stack == null ? 0L : Math.max(0L, stack.amount());
                // total 字段保留为 0（未知），UI 不再展示原始物品数。
                total = 0L;
                elapsedMillis = Math.max(0L, status.elapsedTimeNanos() / 1_000_000L);
            }
        } catch (Throwable t) {
            logOnce("cpu.jobStatus", t);
        }
        String treeId = null;
        if (outputItemId != null) {
            try {
                treeId = CraftingOrderService.lookupTreeIdForJob(name, outputItemId);
            } catch (Throwable ignored) {
            }
        }
        return new CraftingJobEntry(name, outputItemId, outputName, total, remaining,
                busy, null, storageBytes, coProc, progressFraction, elapsedMillis, treeId);
    }

    private static String readCpuName(ICraftingCPU cpu, int index) {
        try {
            Component c = cpu.getName();
            if (c != null) {
                String s = c.getString();
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable t) {
            logOnce("cpu.getName", t);
        }
        return "CPU#" + index;
    }

    private static void logOnce(String op, Throwable t) {
        if (LOGGED_FAILURES.add(op)) {
            ResourceObserverMod.LOGGER.warn("[Crafting] AE2 API {} 调用失败: {}", op, t.toString());
        }
    }
}
