package com.yuyinrl.resourceobserver.integration;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * AE2 一键下单服务（异步、两阶段）。
 * <p>
 * 流程： plan → review → confirm。
 * <ol>
 *   <li>{@link #planOrderAsync} —— 启动 AE2 计算线程，tick 轮询 Future 完成后把
 *       {@link ICraftingPlan} 缓存到 {@link #PLAN_CACHE}（带 TTL），把摘要回调给调用方。</li>
 *   <li>客户端在审阅 UI 中决定是否继续。</li>
 *   <li>{@link #confirmOrderAsync} —— 用 planId 取出缓存计划并 {@code submitJob}。</li>
 * </ol>
 * 超时不会调 {@code Future.cancel(true)}，避免 AE2 计算线程被中断后日志报错。
 */
public final class CraftingOrderService {

    private static final int CALC_TIMEOUT_TICKS = 200;   // 计算 10 秒超时
    private static final int PLAN_TTL_TICKS = 600;       // 计划缓存 30 秒后失效
    private static final int JOB_TREE_TTL_TICKS = 30 * 60 * 20; // 已提交任务的树最长保留 30 分钟
    private static final int MAX_PENDING = 32;
    private static final int MAX_LIST_ENTRIES = 64;       // 摘要中物品行数上限
    private static final int TREE_MAX_DEPTH = 8;
    private static final int TREE_MAX_NODES = 256;

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final ConcurrentHashMap<String, CachedPlan> PLAN_CACHE = new ConcurrentHashMap<>();
    /** treeId → 树根（review 阶段 + 已提交任务都用此查询）。 */
    private static final ConcurrentHashMap<String, CachedTree> TREE_CACHE = new ConcurrentHashMap<>();
    /** "cpuName@finalOutputItemId" → treeId，CraftingDataCollector 用其反查正在跑的任务对应的树。 */
    private static final ConcurrentHashMap<String, String> JOB_TREE_INDEX = new ConcurrentHashMap<>();

    private CraftingOrderService() {
    }

    /** 计划/提交阶段统一状态码。 */
    public enum Status {
        SUCCESS,
        ITEM_NOT_FOUND,
        GRID_UNAVAILABLE,
        CALCULATION_TIMEOUT,
        SIMULATION_REQUIRED,
        SUBMIT_FAILED,
        BAD_AMOUNT,
        TOO_MANY_PENDING,
        PLAN_NOT_FOUND
    }

    /** 摘要中的单条物品行。 */
    public record Stack(String itemId, String displayName, long amount) {
    }

    /** CPU 列表中的单台 CPU 描述。 */
    public record CpuInfo(String name, long storageBytes, int coProcessors, boolean busy) {
    }

    /** plan 阶段返回的计划摘要。 */
    public record PlanResult(
            Status status,
            @Nullable String message,
            @Nullable String planId,
            boolean simulation,
            long bytes,
            @Nullable String finalOutputItemId,
            @Nullable String finalOutputDisplayName,
            long finalOutputAmount,
            List<Stack> usedItems,
            List<Stack> missingItems,
            List<Stack> emittedItems,
            // 本次计划用到的样板及触发次数（按主输出聚合）。
            List<Stack> patternTimes,
            // 网络中可用的 CPU 列表，供前端选择（null = 自动）。
            List<CpuInfo> cpus,
            // 合成树标识（与 TREE_CACHE 对齐）；为空表示没构建出树。
            @Nullable String treeId,
            // 完整树根（review 阶段直接展示，不必再次请求）。
            @Nullable CraftingTreeNode treeRoot
    ) {
        public boolean ok() {
            return status == Status.SUCCESS && !simulation;
        }

        public static PlanResult fail(Status s, String msg) {
            return new PlanResult(s, msg, null, false, 0L, null, null, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
        }
    }

    /** confirm 阶段返回的提交结果。 */
    public record SubmitResult(Status status, @Nullable String message, @Nullable String linkId) {
        public boolean ok() {
            return status == Status.SUCCESS;
        }

        public static SubmitResult success(@Nullable String linkId) {
            return new SubmitResult(Status.SUCCESS, null, linkId);
        }

        public static SubmitResult fail(Status s, String msg) {
            return new SubmitResult(s, msg, null);
        }
    }

    // ----------------------------------------------------------------- plan

    /**
     * 异步发起合成计划计算。必须在服务端主线程调用。
     */
    public static void planOrderAsync(Level level,
                                      @Nullable IGrid grid,
                                      String itemId,
                                      long amount,
                                      Consumer<PlanResult> onResult) {
        PlanResult immediate = startPlan(level, grid, itemId, amount, onResult);
        if (immediate != null) {
            onResult.accept(immediate);
        }
    }

    private static @Nullable PlanResult startPlan(Level level,
                                                  @Nullable IGrid grid,
                                                  String itemId,
                                                  long amount,
                                                  Consumer<PlanResult> onResult) {
        if (amount <= 0L) {
            return PlanResult.fail(Status.BAD_AMOUNT, "amount must be > 0");
        }
        if (grid == null) {
            return PlanResult.fail(Status.GRID_UNAVAILABLE, "AE2 grid not available");
        }
        ResourceLocation rl;
        try {
            rl = ResourceLocation.parse(itemId);
        } catch (Exception e) {
            return PlanResult.fail(Status.ITEM_NOT_FOUND, "invalid item id: " + itemId);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) {
            return PlanResult.fail(Status.ITEM_NOT_FOUND, "unknown item: " + itemId);
        }
        AEItemKey key = AEItemKey.of(item);
        if (key == null) {
            return PlanResult.fail(Status.ITEM_NOT_FOUND, "cannot wrap as AEItemKey: " + itemId);
        }

        ICraftingService service;
        try {
            service = grid.getCraftingService();
        } catch (Throwable t) {
            return PlanResult.fail(Status.GRID_UNAVAILABLE, "cannot get crafting service: " + t.getMessage());
        }
        if (service == null) {
            return PlanResult.fail(Status.GRID_UNAVAILABLE, "no crafting service");
        }

        // 网络中没有可生产此物品的样板 → 直接返回，不要走 AE2 模拟，避免把"终产物"当成"缺失材料"显示。
        int patternCount = 0;
        try {
            var patterns = service.getCraftingFor(key);
            patternCount = patterns == null ? 0 : patterns.size();
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] getCraftingFor 异常: {}", t.toString());
        }
        boolean canEmit = false;
        try {
            canEmit = service.canEmitFor(key);
        } catch (Throwable ignored) {
        }
        ResourceObserverMod.LOGGER.info(
                "[Crafting] plan request item={} amount={} patterns={} canEmit={} cpus={}",
                itemId, amount, patternCount, canEmit,
                safeCpuCount(service));
        if (patternCount == 0 && !canEmit) {
            return PlanResult.fail(Status.ITEM_NOT_FOUND,
                    "no crafting pattern in network for " + itemId);
        }

        try {
            var patterns = service.getCraftingFor(key);
            int idx = 0;
            for (var pd : patterns) {
                idx++;
                String defName;
                try {
                    defName = String.valueOf(pd.getDefinition());
                } catch (Throwable t) {
                    defName = "<err:" + t.getMessage() + ">";
                }
                StringBuilder out = new StringBuilder();
                try {
                    var outputs = pd.getOutputs();
                    if (outputs != null) {
                        for (var gs : outputs) {
                            out.append(gs.what() == null ? "null" : gs.what().getId())
                                    .append('×').append(gs.amount()).append("; ");
                        }
                    }
                } catch (Throwable t) {
                    out.append("<err:").append(t.getMessage()).append('>');
                }
                StringBuilder ins = new StringBuilder();
                try {
                    var inputs = pd.getInputs();
                    if (inputs != null) {
                        for (var in : inputs) {
                            var poss = in.getPossibleInputs();
                            if (poss != null && poss.length > 0 && poss[0] != null) {
                                var w = poss[0].what();
                                ins.append(w == null ? "null" : w.getId())
                                        .append('×').append(poss[0].amount() * in.getMultiplier())
                                        .append("; ");
                            } else {
                                ins.append("<empty>; ");
                            }
                        }
                    }
                } catch (Throwable t) {
                    ins.append("<err:").append(t.getMessage()).append('>');
                }
                ResourceObserverMod.LOGGER.info(
                        "[Crafting]   pattern#{} def={} primaryOut={} outputs=[{}] inputs=[{}]",
                        idx, defName,
                        pd.getPrimaryOutput() == null ? "null" : pd.getPrimaryOutput().what(),
                        out.toString().trim(), ins.toString().trim());
                try {
                    var po = pd.getPrimaryOutput();
                    if (po != null && po.what() != null) {
                        var pk = po.what();
                        ResourceObserverMod.LOGGER.info(
                                "[Crafting]     keyEquals={} requestHash={} patternHash={} requestClass={} patternClass={}",
                                key.equals(pk), key.hashCode(), pk.hashCode(),
                                key.getClass().getSimpleName(), pk.getClass().getSimpleName());
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] pattern dump failed: {}", t.toString());
        }

        if (PENDING.size() >= MAX_PENDING) {
            return PlanResult.fail(Status.TOO_MANY_PENDING, "too many pending crafting orders");
        }

        ICraftingSimulationRequester requester = new SimRequester(grid);
        Future<ICraftingPlan> future;
        try {
            future = service.beginCraftingCalculation(level, requester, key, amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] beginCraftingCalculation 失败: {}", t.toString());
            return PlanResult.fail(Status.SUBMIT_FAILED, "calculation start failed: " + t.getMessage());
        }

        PENDING.add(new Pending(service, future, onResult, CALC_TIMEOUT_TICKS));
        return null;
    }

    // -------------------------------------------------------------- confirm

    /** 用 planId 提交之前缓存的计划。cpuName 为 null 时让 AE2 自动分配。 */
    public static SubmitResult confirmOrder(String planId, @Nullable String cpuName) {
        CachedPlan cached = PLAN_CACHE.remove(planId);
        if (cached == null) {
            return SubmitResult.fail(Status.PLAN_NOT_FOUND, "plan expired or not found: " + planId);
        }
        ICraftingCPU targetCpu = null;
        if (cpuName != null && !cpuName.isBlank()) {
            try {
                for (ICraftingCPU cpu : cached.service.getCpus()) {
                    if (cpu == null) continue;
                    String n = cpu.getName() == null ? "" : cpu.getName().getString();
                    if (cpuName.equals(n)) {
                        targetCpu = cpu;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        ICraftingSubmitResult result;
        try {
            result = cached.service.submitJob(cached.plan, null, targetCpu, false, IActionSource.empty());
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] submitJob 失败: {}", t.toString());
            return SubmitResult.fail(Status.SUBMIT_FAILED, "submitJob threw: " + t.getMessage());
        }
        if (result == null || !result.successful()) {
            String code = result == null ? "null" : String.valueOf(result.errorCode());
            return SubmitResult.fail(Status.SUBMIT_FAILED, "submit rejected: " + code);
        }

        // 提交成功 —— 把 plan 关联的树迁移为长期缓存，并按 cpu+输出物 建立索引
        String treeId = PLAN_TREE_LINK.remove(planId);
        if (treeId != null) {
            CachedTree ct = TREE_CACHE.get(treeId);
            if (ct != null) {
                ct.remainingTicks = JOB_TREE_TTL_TICKS;
                String outId = ct.root.itemId();
                String key = jobIndexKey(targetCpu == null ? null : safeCpuName(targetCpu), outId);
                if (key != null) JOB_TREE_INDEX.put(key, treeId);
            }
        }
        String linkId = result.link() == null ? null : String.valueOf(result.link());
        return SubmitResult.success(linkId);
    }

    /** 兼容旧调用：默认让 AE2 自动分配 CPU。 */
    public static SubmitResult confirmOrder(String planId) {
        return confirmOrder(planId, null);
    }

    /** 取消已缓存的计划（释放 AE2 资源）。 */
    public static void cancelPlan(String planId) {
        PLAN_CACHE.remove(planId);
        String treeId = PLAN_TREE_LINK.remove(planId);
        if (treeId != null) TREE_CACHE.remove(treeId);
    }

    /** 按 treeId 查询合成树（review 阶段或运行中任务点击时调用）。 */
    @Nullable
    public static CraftingTreeNode lookupTree(String treeId) {
        if (treeId == null || treeId.isBlank()) return null;
        CachedTree ct = TREE_CACHE.get(treeId);
        return ct == null ? null : ct.root;
    }

    /** 反查正在运行的某个 CPU 任务对应的合成树 ID（无则返回 null）。 */
    @Nullable
    public static String lookupTreeIdForJob(@Nullable String cpuName, @Nullable String outputItemId) {
        String key = jobIndexKey(cpuName, outputItemId);
        return key == null ? null : JOB_TREE_INDEX.get(key);
    }

    @Nullable
    private static String jobIndexKey(@Nullable String cpuName, @Nullable String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) return null;
        return (cpuName == null ? "" : cpuName) + "@" + outputItemId;
    }

    private static String safeCpuName(ICraftingCPU cpu) {
        try {
            return cpu.getName() == null ? "" : cpu.getName().getString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** PLAN_TREE_LINK：planId → treeId，提交时迁移到 JOB_TREE_INDEX 并续期。 */
    private static final ConcurrentHashMap<String, String> PLAN_TREE_LINK = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ tick pump

    /** 每个 server tick 末尾轮询 Future 与缓存过期。 */
    @EventBusSubscriber(modid = ResourceObserverMod.MODID)
    public static final class TickPump {
        private TickPump() {
        }

        @SubscribeEvent
        public static void onTick(ServerTickEvent.Post event) {
            if (!PENDING.isEmpty()) {
                Iterator<Pending> it = PENDING.iterator();
                while (it.hasNext()) {
                    Pending p = it.next();
                    if (p.future.isDone()) {
                        it.remove();
                        finalizePlan(p);
                        continue;
                    }
                    p.remainingTicks--;
                    if (p.remainingTicks <= 0) {
                        it.remove();
                        // 不取消 Future，让其在后台跑完即被丢弃，避免 InterruptedException
                        p.callback.accept(PlanResult.fail(Status.CALCULATION_TIMEOUT, "calculation timed out"));
                    }
                }
            }
            if (!PLAN_CACHE.isEmpty()) {
                Iterator<java.util.Map.Entry<String, CachedPlan>> it = PLAN_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    CachedPlan c = it.next().getValue();
                    c.remainingTicks--;
                    if (c.remainingTicks <= 0) {
                        it.remove();
                    }
                }
            }
            if (!TREE_CACHE.isEmpty()) {
                Iterator<java.util.Map.Entry<String, CachedTree>> it = TREE_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    java.util.Map.Entry<String, CachedTree> e = it.next();
                    CachedTree c = e.getValue();
                    c.remainingTicks--;
                    if (c.remainingTicks <= 0) {
                        it.remove();
                        // 同步清理 JOB_TREE_INDEX 中指向此 treeId 的条目
                        String dead = e.getKey();
                        JOB_TREE_INDEX.entrySet().removeIf(en -> dead.equals(en.getValue()));
                    }
                }
            }
        }
    }

    private static int safeCpuCount(ICraftingService service) {
        try {
            return service.getCpus() == null ? 0 : service.getCpus().size();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void finalizePlan(Pending p) {
        ICraftingPlan plan;
        try {
            plan = p.future.get();
        } catch (Throwable t) {
            p.callback.accept(PlanResult.fail(Status.SUBMIT_FAILED, "calculation failed: " + t.getMessage()));
            return;
        }
        if (plan == null) {
            p.callback.accept(PlanResult.fail(Status.SUBMIT_FAILED, "no plan"));
            return;
        }

        // 摘要数据
        GenericStack out = plan.finalOutput();
        String outId = (out != null && out.what() instanceof AEItemKey ik) ? aeItemKeyId(ik) : null;
        String outName = (out != null && out.what() instanceof AEItemKey ik) ? aeItemKeyDisplayName(ik) : null;
        long outAmt = out == null ? 0L : out.amount();

        List<Stack> used = keyCounterToStacks(plan.usedItems());
        List<Stack> missing = keyCounterToStacks(plan.missingItems());
        List<Stack> emitted = keyCounterToStacks(plan.emittedItems());
        List<Stack> patternTimes = patternTimesToStacks(plan);
        List<CpuInfo> cpus = collectCpus(p.service);
        boolean simulation = plan.simulation() || !missing.isEmpty();

        ResourceObserverMod.LOGGER.info(
                "[Crafting] plan finalized output={}×{} simulation={} bytes={} used={} missing={} emitted={} multiPath={}",
                outId, outAmt, simulation, plan.bytes(),
                used.size(), missing.size(), emitted.size(), plan.multiplePaths());
        if (!missing.isEmpty()) {
            for (Stack m : missing) {
                ResourceObserverMod.LOGGER.info("[Crafting]   missing: {} ×{} ({})",
                        m.itemId(), m.amount(), m.displayName());
            }
        }

        // 即使存在缺料，AE2 仍允许提交（任务会挂起等待材料）。
        // 所以无论 simulation 与否都缓存计划并返回 planId，由 UI 展示缺料列表后让用户决定。
        String planId = UUID.randomUUID().toString();
        PLAN_CACHE.put(planId, new CachedPlan(plan, p.service, PLAN_TTL_TICKS));

        // 构建合成树（review 阶段内联展示；提交后会迁移到长期缓存）
        CraftingTreeNode root = null;
        String treeId = null;
        try {
            root = buildTree(plan);
            if (root != null) {
                treeId = UUID.randomUUID().toString();
                TREE_CACHE.put(treeId, new CachedTree(root, PLAN_TTL_TICKS));
            }
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] buildTree 失败: {}", t.toString());
        }
        // 把 treeId 关联到 planId，确认提交时迁移。
        if (treeId != null) {
            PLAN_TREE_LINK.put(planId, treeId);
        }

        Status status = simulation ? Status.SIMULATION_REQUIRED : Status.SUCCESS;
        String message = simulation ? "missing materials" : null;
        p.callback.accept(new PlanResult(status, message,
                planId, simulation, plan.bytes(), outId, outName, outAmt,
                used, missing, emitted, patternTimes, cpus, treeId, root));
    }

    /** 把 ICraftingPlan.patternTimes() 转换成扁平 Stack 列表（itemId/displayName/times）。 */
    private static List<Stack> patternTimesToStacks(ICraftingPlan plan) {
        try {
            var map = plan.patternTimes();
            if (map == null || map.isEmpty()) return List.of();
            List<Stack> list = new ArrayList<>();
            int count = 0;
            for (var e : map.entrySet()) {
                if (count++ >= MAX_LIST_ENTRIES) break;
                IPatternDetails pd = e.getKey();
                if (pd == null) continue;
                GenericStack po = pd.getPrimaryOutput();
                if (po == null || !(po.what() instanceof AEItemKey ik)) continue;
                long times = e.getValue() == null ? 0L : e.getValue();
                list.add(new Stack(aeItemKeyId(ik), aeItemKeyDisplayName(ik), times));
            }
            return list;
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] patternTimes 收集失败: {}", t.toString());
            return List.of();
        }
    }

    /** 收集网络中所有 CPU 的信息以供前端选择。 */
    private static List<CpuInfo> collectCpus(ICraftingService service) {
        try {
            var set = service.getCpus();
            if (set == null || set.isEmpty()) return List.of();
            List<CpuInfo> list = new ArrayList<>();
            for (ICraftingCPU cpu : set) {
                if (cpu == null) continue;
                String name;
                try {
                    name = cpu.getName() == null ? "" : cpu.getName().getString();
                } catch (Throwable t) {
                    name = "";
                }
                long sb = 0L;
                int co = 0;
                boolean busy = false;
                try { sb = cpu.getAvailableStorage(); } catch (Throwable ignored) {}
                try { co = cpu.getCoProcessors(); } catch (Throwable ignored) {}
                try { busy = cpu.isBusy(); } catch (Throwable ignored) {}
                list.add(new CpuInfo(name, sb, co, busy));
            }
            return list;
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] CPU 列表收集失败: {}", t.toString());
            return List.of();
        }
    }

    private static List<Stack> keyCounterToStacks(@Nullable KeyCounter kc) {
        if (kc == null || kc.isEmpty()) {
            return List.of();
        }
        List<Stack> list = new ArrayList<>();
        int count = 0;
        for (Object2LongMap.Entry<AEKey> e : kc) {
            if (count++ >= MAX_LIST_ENTRIES) break;
            AEKey key = e.getKey();
            if (!(key instanceof AEItemKey ik)) continue;
            list.add(new Stack(aeItemKeyId(ik), aeItemKeyDisplayName(ik), e.getLongValue()));
        }
        return list;
    }

    private static String aeItemKeyId(AEItemKey ik) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(ik.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static String aeItemKeyDisplayName(AEItemKey ik) {
        try {
            ItemStack stack = ik.toStack(1);
            Component c = stack.getHoverName();
            return c == null ? aeItemKeyId(ik) : c.getString();
        } catch (Throwable ignored) {
            return aeItemKeyId(ik);
        }
    }

    // ============================================================ tree build

    /**
     * 从 ICraftingPlan 构建合成树。
     * <p>
     * 算法：
     * <ol>
     *   <li>用 {@code patternTimes()} 建立 primaryOutput AEItemKey → IPatternDetails 索引；</li>
     *   <li>从 finalOutput 起递归。当前节点若在索引中找得到样板，则展开为内部节点：
     *       记录每次执行的 primaryOutput 数量（{@code perExecOutAmount}）和总执行次数；
     *       为样板的每个输入构造子节点，子节点 requiredAmount = inputPerExec × times。</li>
     *   <li>叶子节点：若在 missingItems 中标记 missing，否则普通叶子。</li>
     *   <li>访问中维护 visiting set 检测循环；超出深度或节点配额时插入截断标记。</li>
     * </ol>
     * 同物品在不同路径完整展开（不去重，按用户偏好）。
     */
    @Nullable
    static CraftingTreeNode buildTree(ICraftingPlan plan) {
        if (plan == null) return null;
        GenericStack out = plan.finalOutput();
        if (out == null || !(out.what() instanceof AEItemKey rootKey)) return null;

        // 1) primaryOutput → IPatternDetails 索引
        Map<AEItemKey, IPatternDetails> patternIdx = new HashMap<>();
        try {
            var ptMap = plan.patternTimes();
            if (ptMap != null) {
                for (Map.Entry<IPatternDetails, Long> e : ptMap.entrySet()) {
                    IPatternDetails pd = e.getKey();
                    if (pd == null) continue;
                    GenericStack po = pd.getPrimaryOutput();
                    if (po == null || !(po.what() instanceof AEItemKey pk)) continue;
                    patternIdx.putIfAbsent(pk, pd);
                }
            }
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[Crafting] 构建样板索引失败: {}", t.toString());
        }

        // 2) 缺失材料集合（用于叶子标记）
        java.util.Set<String> missingIds = new java.util.HashSet<>();
        try {
            KeyCounter kc = plan.missingItems();
            if (kc != null) {
                for (Object2LongMap.Entry<AEKey> e : kc) {
                    if (e.getKey() instanceof AEItemKey ik) missingIds.add(aeItemKeyId(ik));
                }
            }
        } catch (Throwable ignored) {
        }

        // 3) 配套样板执行次数（用 patternTimes Map 重新查找）
        Map<IPatternDetails, Long> timesMap;
        try {
            timesMap = plan.patternTimes();
            if (timesMap == null) timesMap = Map.of();
        } catch (Throwable t) {
            timesMap = Map.of();
        }

        int[] budget = new int[]{TREE_MAX_NODES};
        java.util.HashSet<String> visiting = new java.util.HashSet<>();
        return buildNode(rootKey, out.amount(), patternIdx, timesMap, missingIds, 0, budget, visiting);
    }

    private static CraftingTreeNode buildNode(AEItemKey key, long requiredAmount,
                                              Map<AEItemKey, IPatternDetails> patternIdx,
                                              Map<IPatternDetails, Long> timesMap,
                                              java.util.Set<String> missingIds,
                                              int depth, int[] budget,
                                              java.util.Set<String> visiting) {
        String id = aeItemKeyId(key);
        String name = aeItemKeyDisplayName(key);
        if (depth >= TREE_MAX_DEPTH) {
            return CraftingTreeNode.leaf(id, name, requiredAmount, false);
        }
        if (budget[0] <= 0) {
            return CraftingTreeNode.leaf(id, name, requiredAmount, false);
        }
        budget[0]--;

        IPatternDetails pd = patternIdx.get(key);
        if (pd == null) {
            // 叶子（原料或缺料）
            return CraftingTreeNode.leaf(id, name, requiredAmount, missingIds.contains(id));
        }

        if (!visiting.add(id)) {
            return CraftingTreeNode.loopMarker(id, name, requiredAmount);
        }

        try {
            long perExecOut;
            try {
                GenericStack po = pd.getPrimaryOutput();
                perExecOut = po == null ? 1L : Math.max(1L, po.amount());
            } catch (Throwable t) {
                perExecOut = 1L;
            }
            Long boxed = timesMap.get(pd);
            long times = boxed == null ? 0L : boxed;
            // 备用：若 timesMap 不可用，则用需求 / perExecOut 上取整
            if (times <= 0) {
                times = (requiredAmount + perExecOut - 1) / Math.max(1L, perExecOut);
            }
            // 对应 requiredAmount 重新校正为 perExecOut × times（理论产出，可能 ≥ 上游需求）
            long actualOut = perExecOut * times;

            List<CraftingTreeNode> children = new ArrayList<>();
            try {
                IPatternDetails.IInput[] ins = pd.getInputs();
                if (ins != null) {
                    for (IPatternDetails.IInput in : ins) {
                        if (in == null) continue;
                        var poss = in.getPossibleInputs();
                        if (poss == null || poss.length == 0 || poss[0] == null) continue;
                        var inWhat = poss[0].what();
                        long perExecIn = poss[0].amount() * Math.max(1L, in.getMultiplier());
                        long childReq = perExecIn * times;
                        if (inWhat instanceof AEItemKey ik) {
                            children.add(buildNode(ik, childReq, patternIdx, timesMap, missingIds,
                                    depth + 1, budget, visiting));
                        } else {
                            // 流体或其它键暂不递归，作为叶子展示
                            String inId = inWhat == null ? "unknown" : String.valueOf(inWhat.getId());
                            String inName;
                            try {
                                inName = inWhat == null ? inId : inWhat.getDisplayName().getString();
                            } catch (Throwable t) {
                                inName = inId;
                            }
                            children.add(CraftingTreeNode.leaf(inId, inName, childReq, false));
                        }
                    }
                }
            } catch (Throwable t) {
                ResourceObserverMod.LOGGER.warn("[Crafting] 收集样板输入失败: {}", t.toString());
            }

            return new CraftingTreeNode(id, name, actualOut, perExecOut, times, false, false, false, children);
        } finally {
            visiting.remove(id);
        }
    }

    // ---------------------------------------------------------- inner types

    private static final class CachedTree {
        final CraftingTreeNode root;
        int remainingTicks;

        CachedTree(CraftingTreeNode root, int remainingTicks) {
            this.root = root;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class Pending {
        final ICraftingService service;
        final Future<ICraftingPlan> future;
        final Consumer<PlanResult> callback;
        int remainingTicks;

        Pending(ICraftingService service, Future<ICraftingPlan> future,
                Consumer<PlanResult> callback, int remainingTicks) {
            this.service = service;
            this.future = future;
            this.callback = callback;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class CachedPlan {
        final ICraftingPlan plan;
        final ICraftingService service;
        int remainingTicks;

        CachedPlan(ICraftingPlan plan, ICraftingService service, int remainingTicks) {
            this.plan = plan;
            this.service = service;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class SimRequester implements ICraftingSimulationRequester {
        private final @Nullable IGrid grid;

        SimRequester(@Nullable IGrid grid) {
            this.grid = grid;
        }

        @Override
        public IActionSource getActionSource() {
            return IActionSource.empty();
        }

        @Override
        public @Nullable IGridNode getGridNode() {
            // AE2 的 CraftingTreeNode.buildChildPatterns 在此返回 null 时会跳过样板注册，
            // 导致 simulate 阶段把终产物本身归入 missingItems。必须返回网络中的任意节点。
            if (grid == null) return null;
            try {
                IGridNode pivot = grid.getPivot();
                if (pivot != null) return pivot;
                Iterator<IGridNode> it = grid.getNodes().iterator();
                if (it.hasNext()) return it.next();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}
