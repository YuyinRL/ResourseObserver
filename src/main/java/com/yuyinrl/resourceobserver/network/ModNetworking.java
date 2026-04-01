package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ResourceObserverMod.MODID).versioned("5");

        registrar.playToClient(
                ObserverDataPayload.TYPE,
                ObserverDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.yuyinrl.resourceobserver.client.ClientPayloadHandler.handleObserverData(payload)
                )
        );

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

        registrar.playToServer(
                ObserverUiActionPayload.TYPE,
                ObserverUiActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    PlayerUiPrefsSavedData.ActionResult result = PlayerUiPrefsSavedData.get(serverPlayer.serverLevel())
                            .applyAction(
                                    serverPlayer.getUUID(),
                                    payload.actionType(),
                                    payload.itemId(),
                                    payload.itemIds(),
                                    payload.actionValue()
                            );
                    if (!result.success() && !result.failMessageKey().isBlank()) {
                        serverPlayer.sendSystemMessage(Component.translatable(result.failMessageKey(), PlayerUiPrefsSavedData.WATCHLIST_LIMIT));
                    }

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
