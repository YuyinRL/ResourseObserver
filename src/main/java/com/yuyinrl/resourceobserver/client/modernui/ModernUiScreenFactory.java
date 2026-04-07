package com.yuyinrl.resourceobserver.client.modernui;

import icyllis.modernui.mc.MuiModApi;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Modern UI 屏幕工厂 —— 隔离所有 Modern UI 类引用。
 * <p>
 * 此类仅在 Modern UI 已安装时被加载（由 {@link ModernUiCompat} 守护调用），
 * 避免未安装时的 {@link ClassNotFoundException}。
 */
final class ModernUiScreenFactory {

    private ModernUiScreenFactory() {
    }

    /** 当前活跃的 OverviewFragment 引用（弱持有，用于数据更新） */
    private static OverviewFragment activeFragment;

    /**
     * 创建并打开 Modern UI Overview 屏幕。
     */
    static void open(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        OverviewFragment fragment = new OverviewFragment(payload);
        activeFragment = fragment;
        var screen = MuiModApi.get().createScreen(fragment);
        mc.setScreen(screen);
    }

    /**
     * 尝试更新已存在的 Modern UI Overview 屏幕。
     *
     * @return {@code true} 如果成功更新
     */
    static boolean tryUpdate(ObserverDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current == null || activeFragment == null) {
            return false;
        }
        // 检查当前屏幕是否为 MuiScreen（Modern UI 创建的），
        // 并且 Fragment 仍然活跃且匹配同一观察者
        if (activeFragment.isAdded()
                && activeFragment.matchesObserver(payload.observerPos())) {
            activeFragment.applyPayload(payload);
            return true;
        }
        return false;
    }
}
