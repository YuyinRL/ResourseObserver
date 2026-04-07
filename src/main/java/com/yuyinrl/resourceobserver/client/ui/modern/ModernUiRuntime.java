package com.yuyinrl.resourceobserver.client.ui.modern;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** ModernUI 运行时检测与入口尝试。 */
public final class ModernUiRuntime {
    private static final String MODERN_UI_MOD_ID = "modernui";

    private ModernUiRuntime() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MODERN_UI_MOD_ID);
    }

    /**
     * 通过反射尝试打开 ModernUI Fragment。
     * 说明：为兼容不同 ModernUI 版本，运行时扫描 UIManager 的静态入口。
     */
    public static boolean tryOpenOverviewPreview(Minecraft mc) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object fragment = new ModernUiOverviewFragment();
            Class<?> fragmentClass = Class.forName("icyllis.modernui.fragment.Fragment");
            Class<?> managerClass = Class.forName("icyllis.modernui.mc.UIManager");

            for (Method method : managerClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(fragmentClass)) {
                    method.invoke(null, fragment);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }
}
