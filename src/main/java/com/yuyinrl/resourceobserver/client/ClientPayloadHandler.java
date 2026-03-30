package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;

/**
 * Client-side handler for incoming network payloads.
 * This class must only be loaded on the client (logical client side).
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    /**
     * Called on the client render thread when an ObserverDataPayload arrives.
     */
    public static void handleObserverData(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current instanceof ResourceTerminalScreen terminalScreen
                && terminalScreen.matchesObserver(payload.observerPos())) {
            terminalScreen.applyPayload(payload);
        } else {
            mc.setScreen(new ResourceTerminalScreen(payload));
        }
    }
}

