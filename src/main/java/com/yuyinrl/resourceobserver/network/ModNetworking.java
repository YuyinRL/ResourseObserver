package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
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
        PayloadRegistrar registrar = event.registrar(ResourceObserverMod.MODID).versioned("1");

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
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        ResourceTerminalItem.sendObserverData(serverPlayer, serverPlayer.serverLevel(), payload.observerPos(), false);
                    }
                })
        );
    }
}
