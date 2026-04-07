package com.yuyinrl.resourceobserver.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** 客户端快捷键定义。 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.resourceobserver";

    public static final KeyMapping OPEN_MODERN_OVERVIEW_PREVIEW = new KeyMapping(
            "key.resourceobserver.open_modern_preview",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY
    );

    private ModKeyMappings() {
    }
}
