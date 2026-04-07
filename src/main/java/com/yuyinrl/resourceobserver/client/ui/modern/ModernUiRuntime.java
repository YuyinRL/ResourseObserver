package com.yuyinrl.resourceobserver.client.ui.modern;

import net.neoforged.fml.ModList;

/** ModernUI 运行时检测辅助。 */
public final class ModernUiRuntime {
    private static final String MODERN_UI_MOD_ID = "modernui";

    private ModernUiRuntime() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MODERN_UI_MOD_ID);
    }
}
