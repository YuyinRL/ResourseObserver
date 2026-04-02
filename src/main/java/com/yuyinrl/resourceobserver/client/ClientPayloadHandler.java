package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;

/**
 * 客户端数据包处理器 —— 处理服务端发来的观察者数据负载。
 * 仅在客户端逻辑侧加载，在渲染线程执行。
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    /**
     * 处理收到的观察者数据负载。
     * 如果当前已打开同一观察者的终端界面，则更新数据；
     * 否则创建新的终端界面。
     */
    public static void handleObserverData(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current instanceof ResourceTerminalScreen terminalScreen
                && terminalScreen.matchesObserver(payload.observerPos())) {
            terminalScreen.applyPayload(payload); // 更新现有界面的数据
        } else {
            mc.setScreen(new ResourceTerminalScreen(payload)); // 打开新的终端界面
        }
    }
}
