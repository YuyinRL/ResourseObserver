package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.client.modernui.ResourceTerminalFragment;
import com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;

/**
 * 客户端数据包处理器 —— 处理服务端发来的观察者数据负载。
 * 仅在客户端逻辑侧加载，在渲染线程执行。
 * <p>
 * 优先检查 ModernUI 终端 Fragment 是否活跃，然后检查原生 Screen，
 * 若都不匹配则打开 ModernUI 版终端界面。
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    /**
     * 处理收到的观察者数据负载。
     * 优先级：ModernUI Fragment > 原生 Screen > 打开新 ModernUI 终端
     */
    public static void handleObserverData(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();

        // 1. 检查 ModernUI 终端 Fragment 是否活跃
        ResourceTerminalFragment modernFragment = ResourceTerminalFragment.getActiveInstance();
        if (modernFragment != null && modernFragment.matchesObserver(payload.observerPos())) {
            modernFragment.applyPayload(payload);
            return;
        }

        // 2. 检查原生 Screen 是否活跃（兼容模式）
        Screen current = mc.screen;
        if (current instanceof ResourceTerminalScreen terminalScreen
                && terminalScreen.matchesObserver(payload.observerPos())) {
            terminalScreen.applyPayload(payload);
            return;
        }

        // 3. 打开新的 ModernUI 终端界面
        ResourceTerminalFragment.open(payload);
    }
}
