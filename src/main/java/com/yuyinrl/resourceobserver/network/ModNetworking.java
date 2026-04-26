package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组网络通道注册与数据包处理。
 * <p>
 * 注册三种数据包：
 * 1. ObserverDataPayload（服务端→客户端）：携带观察者数据，打开/刷新终端 GUI
 * 2. ObserverRefreshRequestPayload（客户端→服务端）：终端 GUI 打开期间的定期刷新请求
 * 3. ObserverUiActionPayload（客户端→服务端）：用户在 GUI 中的操作（关注、分组、排序等）
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    /** 将网络数据包注册监听器挂载到模组事件总线 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloads);
    }

    /** 注册所有数据包类型及其处理逻辑。版本号 "7" 用于协议兼容性校验。 */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ResourceObserverMod.MODID).versioned("8");

        // 服务端 → 客户端：观察者数据负载，在客户端渲染线程处理
        registrar.playToClient(
                ObserverDataPayload.TYPE,
                ObserverDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.yuyinrl.resourceobserver.client.ClientPayloadHandler.handleObserverData(payload)
                )
        );

        // 服务端 → 客户端：合成计划摘要（请求计划后由服务端推送）
        registrar.playToClient(
                CraftingPlanResultPayload.TYPE,
                CraftingPlanResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.yuyinrl.resourceobserver.client.ClientPayloadHandler.handleCraftingPlanResult(payload)
                )
        );

        // 服务端 → 客户端：合成树详情
        registrar.playToClient(
                CraftingTreeResponsePayload.TYPE,
                CraftingTreeResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.yuyinrl.resourceobserver.client.ClientPayloadHandler.handleCraftingTreeResponse(payload)
                )
        );

        // 客户端 → 服务端：请求合成树
        registrar.playToServer(
                CraftingTreeRequestPayload.TYPE,
                CraftingTreeRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
                    handleTreeRequest(serverPlayer, payload);
                })
        );

        // 客户端 → 服务端：刷新请求，服务端重新采集数据并回传
        registrar.playToServer(
                ObserverRefreshRequestPayload.TYPE,
                ObserverRefreshRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        ResourceTerminalItem.sendObserverData(
                                serverPlayer,
                                serverPlayer.serverLevel(),
                                payload.observerPos(),
                                false,
                                payload.chartWindow(),
                                payload.chartScope(),
                                payload.scopeItemId()
                        );
                    }
                })
        );

        // 客户端 → 服务端：UI 操作请求（关注/分组/排序/筛选等）
        registrar.playToServer(
                ObserverUiActionPayload.TYPE,
                ObserverUiActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    UiActionType action = payload.actionType();
                    // 拦截：合成相关操作不走偏好持久化路径
                    if (action == UiActionType.PLACE_CRAFT_ORDER) {
                        handleCraftPlan(serverPlayer, payload);
                        return;
                    }
                    if (action == UiActionType.CONFIRM_CRAFT_ORDER) {
                        handleCraftConfirm(serverPlayer, payload);
                        return;
                    }
                    if (action == UiActionType.CANCEL_CRAFT_PLAN) {
                        String pid = payload.actionValue();
                        if (pid != null && !pid.isBlank()) {
                            com.yuyinrl.resourceobserver.integration.CraftingOrderService.cancelPlan(pid);
                        }
                        return;
                    }

                    // 应用 UI 操作到服务端持久化的玩家偏好中
                    PlayerUiPrefsSavedData.ActionResult result = PlayerUiPrefsSavedData.get(serverPlayer.serverLevel())
                            .applyAction(
                                    serverPlayer.getUUID(),
                                    payload.actionType(),
                                    payload.itemId(),
                                    payload.itemIds(),
                                    payload.actionValue()
                            );
                    // 操作失败时向玩家发送错误提示
                    if (!result.success() && !result.failMessageKey().isBlank()) {
                        serverPlayer.sendSystemMessage(Component.translatable(result.failMessageKey(), PlayerUiPrefsSavedData.WATCHLIST_LIMIT));
                    }

                    // 操作完成后立即回传最新数据给客户端
                    ResourceTerminalItem.sendObserverData(
                            serverPlayer,
                            serverPlayer.serverLevel(),
                            payload.observerPos(),
                            false,
                            payload.chartWindow(),
                            payload.chartScope(),
                            payload.scopeItemId()
                    );
                })
        );
    }

    /** 处理 PLACE_CRAFT_ORDER：发起计划计算，完成后向玩家推送 {@link CraftingPlanResultPayload}。 */
    private static void handleCraftPlan(ServerPlayer player, ObserverUiActionPayload payload) {
        try {
            String networkId = (payload.itemIds() == null || payload.itemIds().isEmpty())
                    ? null : payload.itemIds().get(0);
            String itemId = payload.itemId();
            long amount;
            try {
                amount = Long.parseLong(payload.actionValue() == null ? "0" : payload.actionValue().trim());
            } catch (NumberFormatException ex) {
                amount = 0L;
            }
            if (networkId == null || networkId.isBlank() || itemId == null || itemId.isBlank() || amount <= 0L) {
                sendPlanError(player, "BAD_AMOUNT", "下单参数无效");
                return;
            }
            net.minecraft.world.level.block.entity.BlockEntity be =
                    player.serverLevel().getBlockEntity(payload.observerPos());
            if (!(be instanceof com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity observer)) {
                sendPlanError(player, "GRID_UNAVAILABLE", "观察者不存在");
                return;
            }
            appeng.api.networking.IGrid grid = observer.resolveAe2GridForNetwork(networkId);
            com.yuyinrl.resourceobserver.integration.CraftingOrderService.planOrderAsync(
                    player.serverLevel(), grid, itemId, amount,
                    plan -> sendPlanResult(player, plan));
        } catch (Throwable t) {
            sendPlanError(player, "SUBMIT_FAILED", "下单异常：" + t.getMessage());
        }
    }

    /** 处理 CONFIRM_CRAFT_ORDER：actionValue="planId" 或 "planId|cpuName"，提交之前缓存的计划。 */
    private static void handleCraftConfirm(ServerPlayer player, ObserverUiActionPayload payload) {
        String raw = payload.actionValue();
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("[ResourceObserver] 计划 ID 为空"));
            return;
        }
        String planId = raw;
        String cpuName = null;
        int sep = raw.indexOf('|');
        if (sep >= 0) {
            planId = raw.substring(0, sep);
            cpuName = sep + 1 < raw.length() ? raw.substring(sep + 1) : null;
            if (cpuName != null && cpuName.isBlank()) cpuName = null;
        }
        com.yuyinrl.resourceobserver.integration.CraftingOrderService.SubmitResult res =
                com.yuyinrl.resourceobserver.integration.CraftingOrderService.confirmOrder(planId, cpuName);
        if (res.ok()) {
            player.sendSystemMessage(Component.literal("[ResourceObserver] 已下单"));
        } else {
            player.sendSystemMessage(Component.literal("[ResourceObserver] 下单失败："
                    + res.status().name() + (res.message() == null ? "" : " - " + res.message())));
        }
    }

    private static void sendPlanError(ServerPlayer player, String status, String message) {
        sendPlanResult(player, new com.yuyinrl.resourceobserver.integration.CraftingOrderService.PlanResult(
                com.yuyinrl.resourceobserver.integration.CraftingOrderService.Status.valueOf(status),
                message, null, false, 0L, null, null, 0L,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null, null));
    }

    private static void sendPlanResult(ServerPlayer player,
                                       com.yuyinrl.resourceobserver.integration.CraftingOrderService.PlanResult plan) {
        java.util.List<String> used = stacksToFlat(plan.usedItems());
        java.util.List<String> missing = stacksToFlat(plan.missingItems());
        java.util.List<String> emitted = stacksToFlat(plan.emittedItems());
        java.util.List<String> patternTimes = stacksToFlat(plan.patternTimes());
        java.util.List<String> cpus = cpusToFlat(plan.cpus());
        CraftingPlanResultPayload payload = new CraftingPlanResultPayload(
                plan.status().name(),
                plan.message() == null ? "" : plan.message(),
                plan.planId() == null ? "" : plan.planId(),
                plan.simulation(),
                plan.bytes(),
                plan.finalOutputItemId() == null ? "" : plan.finalOutputItemId(),
                plan.finalOutputDisplayName() == null ? "" : plan.finalOutputDisplayName(),
                plan.finalOutputAmount(),
                used, missing, emitted, patternTimes, cpus,
                plan.treeId() == null ? "" : plan.treeId(),
                plan.treeRoot());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    /** 处理客户端发起的合成树请求，从服务端缓存查询并回包。 */
    private static void handleTreeRequest(ServerPlayer player, CraftingTreeRequestPayload req) {
        String tid = req.treeId();
        if (tid == null || tid.isBlank()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new CraftingTreeResponsePayload("", 0.0, null));
            return;
        }
        com.yuyinrl.resourceobserver.integration.CraftingTreeNode root =
                com.yuyinrl.resourceobserver.integration.CraftingOrderService.lookupTree(tid);
        // progressFraction 由 CraftingDataCollector 在 ObserverData 中持续推送；
        // 这里只回 root，前端用本地缓存的进度数据展示
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new CraftingTreeResponsePayload(tid, 0.0, root));
    }

    private static java.util.List<String> stacksToFlat(
            java.util.List<com.yuyinrl.resourceobserver.integration.CraftingOrderService.Stack> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>(list.size() * 3);
        for (var s : list) {
            out.add(s.itemId() == null ? "" : s.itemId());
            out.add(s.displayName() == null ? "" : s.displayName());
            out.add(Long.toString(s.amount()));
        }
        return out;
    }

    private static java.util.List<String> cpusToFlat(
            java.util.List<com.yuyinrl.resourceobserver.integration.CraftingOrderService.CpuInfo> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>(list.size() * 4);
        for (var c : list) {
            out.add(c.name() == null ? "" : c.name());
            out.add(Long.toString(c.storageBytes()));
            out.add(Integer.toString(c.coProcessors()));
            out.add(c.busy() ? "1" : "0");
        }
        return out;
    }
}
