package com.yuyinrl.resourceobserver.integration;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AE2 合成数据采集器 —— 按需从 AE2 网络读取合成相关信息。
 * <p>
 * 采用反射调用 {@code ICraftingService}、{@code ICraftingCPU} 等接口以隔离 AE2 API 版本差异：
 * <ul>
 *   <li>{@link #collectCraftables(IGrid)} 列出网络内可合成物品</li>
 *   <li>{@link #collectActiveJobs(IGrid)} 列出当前所有 CPU 及其任务状态</li>
 *   <li>{@link #collectStorageMetrics(IGrid)} 汇总合成 CPU 的存储容量指标</li>
 * </ul>
 * <p>
 * 反射失败时返回空集合并记录一次性 DEBUG 日志，不抛异常。
 */
public final class CraftingDataCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingDataCollector.class);

    /** 可合成物品枚举上限，防止 Patter 特别多时阻塞主线程。 */
    private static final int MAX_CRAFTABLES = 1024;

    /** 反射方法缓存（按类 + 方法名）。 */
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /** 已记录过的失败签名，避免刷屏。 */
    private static final Set<String> LOGGED_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private CraftingDataCollector() {
    }

    /**
     * 单个可合成物品条目。
     *
     * @param itemId     注册 ID（如 {@code minecraft:iron_ingot}）
     * @param displayName 物品显示名称
     */
    public record CraftableEntry(String itemId, String displayName) {
    }

    /**
     * 单个正在运行的合成任务。
     *
     * @param cpuName          CPU 名称（无则 "CPU#N"）
     * @param outputItemId     任务目标物品 ID（null 表示 CPU 空闲）
     * @param outputDisplayName 目标物品显示名称
     * @param totalAmount      任务初始总数量
     * @param remainingAmount  剩余未完成数量
     * @param busy             CPU 是否繁忙
     * @param jobId            任务 UUID 字符串（可能为空）
     */
    public record CraftingJobEntry(
            String cpuName,
            @Nullable String outputItemId,
            @Nullable String outputDisplayName,
            long totalAmount,
            long remainingAmount,
            boolean busy,
            @Nullable String jobId
    ) {
    }

    /**
     * 合成存储容量汇总（按 CPU 聚合）。
     *
     * @param cpuCount          CPU 数量
     * @param busyCpuCount      正在忙碌的 CPU 数量
     * @param totalStorageBytes 全部 CPU 的可用存储字节总和
     * @param totalCoProcessors 协处理器总数
     * @param reliable          是否所有字段都成功读取（反射失败时为 false）
     */
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
        if (grid == null) {
            return Collections.emptyList();
        }
        Object service = getCraftingService(grid);
        if (service == null) {
            return Collections.emptyList();
        }
        try {
            Method getCraftables = resolveMethod(service.getClass(), "getCraftables",
                    java.util.function.Predicate.class);
            if (getCraftables == null) {
                return Collections.emptyList();
            }
            java.util.function.Predicate<AEKey> filter = k -> k instanceof AEItemKey;
            @SuppressWarnings("unchecked")
            Collection<AEKey> raw = (Collection<AEKey>) getCraftables.invoke(service, filter);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<CraftableEntry> out = new ArrayList<>(Math.min(raw.size(), MAX_CRAFTABLES));
            int limit = 0;
            for (AEKey key : raw) {
                if (!(key instanceof AEItemKey ik)) continue;
                Item item = ik.getItem();
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String itemId = id == null ? "unknown" : id.toString();
                String name = Component.translatable(item.getDescriptionId()).getString();
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
        if (grid == null) {
            return Collections.emptyList();
        }
        Object service = getCraftingService(grid);
        if (service == null) {
            return Collections.emptyList();
        }
        Collection<?> cpus = getCpus(service);
        if (cpus.isEmpty()) {
            return Collections.emptyList();
        }
        List<CraftingJobEntry> out = new ArrayList<>(cpus.size());
        int index = 0;
        for (Object cpu : cpus) {
            index++;
            out.add(readCpuJob(cpu, index));
        }
        return out;
    }

    /** 合成 CPU 的存储字节与协处理器汇总。 */
    public static CraftingStorageMetrics collectStorageMetrics(@Nullable IGrid grid) {
        if (grid == null) {
            return CraftingStorageMetrics.empty();
        }
        Object service = getCraftingService(grid);
        if (service == null) {
            return CraftingStorageMetrics.empty();
        }
        Collection<?> cpus = getCpus(service);
        if (cpus.isEmpty()) {
            return CraftingStorageMetrics.empty();
        }
        int busy = 0;
        long totalStorage = 0L;
        int totalCoProc = 0;
        boolean reliable = true;
        for (Object cpu : cpus) {
            Boolean busyFlag = invokeBoolean(cpu, "isBusy");
            if (Boolean.TRUE.equals(busyFlag)) busy++;

            Long storage = invokeLong(cpu, "getAvailableStorage");
            if (storage == null) {
                reliable = false;
            } else {
                totalStorage += storage;
            }

            Integer coProc = invokeInt(cpu, "getCoProcessors");
            if (coProc != null) {
                totalCoProc += coProc;
            }
        }
        return new CraftingStorageMetrics(cpus.size(), busy, totalStorage, totalCoProc, reliable);
    }

    // ======================= 内部反射工具 =======================

    private static @Nullable Object getCraftingService(IGrid grid) {
        try {
            Method m = resolveMethod(grid.getClass(), "getCraftingService");
            if (m == null) return null;
            return m.invoke(grid);
        } catch (Throwable t) {
            logOnce("getCraftingService", t);
            return null;
        }
    }

    private static Collection<?> getCpus(Object service) {
        try {
            Method m = resolveMethod(service.getClass(), "getCpus");
            if (m == null) return Collections.emptyList();
            Object result = m.invoke(service);
            if (result instanceof Collection<?> c) return c;
        } catch (Throwable t) {
            logOnce("getCpus", t);
        }
        return Collections.emptyList();
    }

    private static CraftingJobEntry readCpuJob(Object cpu, int index) {
        String name = readCpuName(cpu, index);
        Boolean busyFlag = invokeBoolean(cpu, "isBusy");
        boolean busy = Boolean.TRUE.equals(busyFlag);

        Object finalOutput = invoke(cpu, "getFinalOutput");
        String outputItemId = null;
        String outputName = null;
        long total = 0L;
        if (finalOutput != null) {
            AEKey key = extractKey(finalOutput);
            Long amount = extractAmount(finalOutput);
            if (key instanceof AEItemKey ik) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(ik.getItem());
                outputItemId = id == null ? "unknown" : id.toString();
                outputName = new ItemStack(ik.getItem()).getHoverName().getString();
            }
            if (amount != null) total = amount;
        }
        Long remaining = invokeLong(cpu, "getRemainingItemCount");
        if (remaining == null) remaining = invokeLong(cpu, "getRemaining");

        Object uuid = invoke(cpu, "getJob");
        String jobId = uuid instanceof UUID u ? u.toString() : null;

        return new CraftingJobEntry(
                name,
                outputItemId,
                outputName,
                total,
                remaining == null ? 0L : remaining,
                busy,
                jobId
        );
    }

    private static String readCpuName(Object cpu, int index) {
        Object n = invoke(cpu, "getName");
        if (n instanceof Component c) {
            String s = c.getString();
            if (!s.isEmpty()) return s;
        } else if (n instanceof String s && !s.isEmpty()) {
            return s;
        }
        return "CPU#" + index;
    }

    private static @Nullable AEKey extractKey(Object stackLike) {
        Object k = invoke(stackLike, "what");
        if (k == null) k = invoke(stackLike, "getKey");
        return k instanceof AEKey ae ? ae : null;
    }

    private static @Nullable Long extractAmount(Object stackLike) {
        Long v = invokeLong(stackLike, "amount");
        if (v != null) return v;
        return invokeLong(stackLike, "getAmount");
    }

    private static @Nullable Method resolveMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        String key = cls.getName() + "#" + name + "(" + paramTypes.length + ")";
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != paramTypes.length) continue;
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        }
        return null;
    }

    private static @Nullable Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = resolveMethod(target.getClass(), methodName);
            if (m == null) return null;
            return m.invoke(target);
        } catch (Throwable t) {
            logOnce(methodName, t);
            return null;
        }
    }

    private static @Nullable Long invokeLong(Object target, String methodName) {
        Object r = invoke(target, methodName);
        if (r instanceof Number n) return n.longValue();
        return null;
    }

    private static @Nullable Integer invokeInt(Object target, String methodName) {
        Object r = invoke(target, methodName);
        if (r instanceof Number n) return n.intValue();
        return null;
    }

    private static @Nullable Boolean invokeBoolean(Object target, String methodName) {
        Object r = invoke(target, methodName);
        if (r instanceof Boolean b) return b;
        return null;
    }

    private static void logOnce(String op, Throwable t) {
        if (LOGGED_FAILURES.add(op)) {
            ResourceObserverMod.LOGGER.debug("[Crafting] 反射调用 {} 失败: {}", op, t.toString());
        }
    }
}
