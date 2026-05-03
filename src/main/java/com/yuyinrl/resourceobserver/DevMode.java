package com.yuyinrl.resourceobserver;

import net.neoforged.fml.loading.FMLLoader;

/**
 * 开发/玩家双模式切换工具。
 * 不做功能裁剪，只控制 UI 可见性和物品可获取性。
 */
public final class DevMode {

    private static final boolean IS_DEV = !FMLLoader.isProduction();

    private DevMode() {
    }

    /** 当前是否运行在开发环境（Gradle 启动）。生产环境返回 false。 */
    public static boolean isDev() {
        return IS_DEV;
    }

    /** 当前是否为玩家发布环境。 */
    public static boolean isProduction() {
        return !IS_DEV;
    }
}
