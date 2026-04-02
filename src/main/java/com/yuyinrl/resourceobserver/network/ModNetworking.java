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

    /**
     * 注册所有数据包类型及其处理逻辑。
     * 版本号 "5" 用于协议兼容性校验。
     */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ResourceObserverMod.MODID).versioned("5");

        // 服务端 → 客户端：观察者数据负载，在客户端渲染线程处理
        registrar.playToClient(
                ObserverDataPayload.TYPE,
                ObserverDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.yuyinrl.resourceobserver.client.ClientPayloadHandler.handleObserverData(payload)
                )
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
}
