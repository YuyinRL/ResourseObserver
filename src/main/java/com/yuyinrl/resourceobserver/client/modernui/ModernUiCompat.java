package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

/**
 * Modern UI 兼容层 —— 负责检测 Modern UI 是否安装以及路由到 Modern UI 界面。
 * <p>
 * 所有 Modern UI 类引用都隔离在此类和 {@code modernui} 包内的其他类中，
 * 防止未安装 Modern UI 时的 {@link ClassNotFoundException}。
 */
public final class ModernUiCompat {

    private static Boolean cachedAvailable;

    private ModernUiCompat() {
    }

    /**
     * 检测 Modern UI mod 是否已加载。
     * 结果会被缓存，避免重复查询。
     */
    public static boolean isModernUiAvailable() {
        if (cachedAvailable == null) {
            cachedAvailable = ModList.get().isLoaded("modernui");
        }
        return cachedAvailable;
    }

    /**
     * 使用 Modern UI Fragment 打开总览界面。
     * <p>
     * 此方法只应在 {@link #isModernUiAvailable()} 返回 {@code true} 时调用。
     * 实际的 Modern UI 类引用延迟到 {@link ModernUiScreenFactory} 中，
     * 确保本类可以安全加载。
     *
     * @param payload 服务端数据负载
     */
    public static void openOverviewScreen(ObserverDataPayload payload) {
        ModernUiScreenFactory.open(payload);
    }

    /**
     * 尝试向当前已打开的 Modern UI 总览界面更新数据。
     *
     * @param payload 服务端数据负载
     * @return 如果成功更新了现有界面返回 {@code true}；否则返回 {@code false}
     */
    public static boolean tryUpdateExistingScreen(ObserverDataPayload payload) {
        return ModernUiScreenFactory.tryUpdate(payload);
    }
}
