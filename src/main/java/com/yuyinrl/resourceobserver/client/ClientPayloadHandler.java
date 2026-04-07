package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.client.modernui.ModernUiCompat;
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
     * <p>
     * 路由优先级：
     * <ol>
     *   <li>如果 Modern UI 已安装且当前有活跃的 Modern UI 界面 → 更新现有 Modern UI 界面</li>
     *   <li>如果当前有原生终端界面且匹配同一观察者 → 更新原生界面</li>
     *   <li>如果 Modern UI 已安装 → 打开新的 Modern UI 界面</li>
     *   <li>否则 → 打开新的原生终端界面</li>
     * </ol>
     */
    public static void handleObserverData(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;

        // 尝试更新已有的原生终端界面
        if (current instanceof ResourceTerminalScreen terminalScreen
                && terminalScreen.matchesObserver(payload.observerPos())) {
            terminalScreen.applyPayload(payload);
            return;
        }

        // 尝试 Modern UI 路径
        if (ModernUiCompat.isModernUiAvailable()) {
            // 尝试更新已有的 Modern UI 界面
            if (ModernUiCompat.tryUpdateExistingScreen(payload)) {
                return;
            }
            // 打开新的 Modern UI 界面
            ModernUiCompat.openOverviewScreen(payload);
            return;
        }

        // 回退到原生终端界面
        mc.setScreen(new ResourceTerminalScreen(payload));
    }
}
