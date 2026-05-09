package com.yuyinrl.resourceobserver.client.screen.theme;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 新 Vanilla UI 框架使用到的纹理 / sprite ID 常量集中表。
 *
 * <p>所有 widget 渲染时引用本类常量，便于审计与未来切皮肤。</p>
 */
public final class Sprites {

    private Sprites() {}

    /** 模组域内构建 sprite 路径。 */
    public static ResourceLocation modSprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, path);
    }

    /** 原版域内引用（用于复用 vanilla GUI 资源）。 */
    public static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    // ---- 占位：F0 阶段先不实际使用纹理，仅用 fill；这里为后续 widget 预留命名 -------
    public static final ResourceLocation BUTTON_FRAME = vanilla("widget/button");
    public static final ResourceLocation BUTTON_FRAME_HIGHLIGHTED = vanilla("widget/button_highlighted");
    public static final ResourceLocation BUTTON_FRAME_DISABLED = vanilla("widget/button_disabled");
}
