package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.client.modernui.ResourceTerminalFragment;
import com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.WebTokenPayload;

/**
 * 客户端数据包处理器 —— 处理服务端发来的观察者数据负载。
 * 仅在客户端逻辑侧加载，在渲染线程执行。
 * <p>
 * 当前唯一的终端 UI 实现是 ModernUI Fragment；老的原生 {@code ResourceTerminalScreen}
 * 已于 v0.10.0-alpha 退役，{@link ResourceTerminalFragment} 是统一入口。
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    /**
     * 处理收到的观察者数据负载。
     * 若 ModernUI Fragment 已活跃且匹配同一个 Observer 则就地刷新，否则打开新终端。
     */
    public static void handleObserverData(ObserverDataPayload payload) {
        ResourceTerminalFragment modernFragment = ResourceTerminalFragment.getActiveInstance();
        if (modernFragment != null && modernFragment.matchesObserver(payload.observerPos())) {
            modernFragment.applyPayload(payload);
            return;
        }
        ResourceTerminalFragment.open(payload);
    }

    /** 处理服务端推送的合成计划摘要：交给当前活跃的 ModernUI Fragment 弹窗展示。 */
    public static void handleCraftingPlanResult(CraftingPlanResultPayload payload) {
        ResourceTerminalFragment modernFragment = ResourceTerminalFragment.getActiveInstance();
        if (modernFragment != null) {
            modernFragment.applyCraftingPlanResult(payload);
        }
    }

    /** 处理服务端推送的合成树详情：交给当前活跃的 ModernUI Fragment。 */
    public static void handleCraftingTreeResponse(
            com.yuyinrl.resourceobserver.network.CraftingTreeResponsePayload payload) {
        ResourceTerminalFragment modernFragment = ResourceTerminalFragment.getActiveInstance();
        if (modernFragment != null) {
            modernFragment.applyCraftingTreeResponse(payload);
        }
    }

    /**
     * 处理服务端推送的 Web 访问 Token / 链接：交给当前活跃的 ModernUI Fragment 弹出对话框。
     * 玩家未打开终端时不应收到此包；若收到则静默丢弃。
     */
    public static void handleWebToken(WebTokenPayload payload) {
        ResourceTerminalFragment modernFragment = ResourceTerminalFragment.getActiveInstance();
        if (modernFragment != null) {
            modernFragment.applyWebToken(payload);
        }
    }
}
