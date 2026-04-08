package com.yuyinrl.resourceobserver.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.modernui.OverviewPreviewLauncher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端按键绑定与触发逻辑。
 */
public final class ClientKeyBindings {
    private static final KeyMapping OPEN_MODERNUI_OVERVIEW_PREVIEW = new KeyMapping(
            "key.resourceobserver.modernui_preview",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.resourceobserver"
    );
    private static boolean wasPreviewKeyDown;

    private ClientKeyBindings() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientKeyBindings::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientKeyBindings::onClientTickPost);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODERNUI_OVERVIEW_PREVIEW);
        ResourceObserverMod.LOGGER.info("Registered key mapping: key.resourceobserver.modernui_preview (default: J)");
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasPreviewKeyDown = false;
            return;
        }

        if (mc.screen != null) {
            wasPreviewKeyDown = false;
            return;
        }

        boolean downNow = OPEN_MODERNUI_OVERVIEW_PREVIEW.isDown();
        boolean edgeTriggered = downNow && !wasPreviewKeyDown;
        wasPreviewKeyDown = downNow;

        if (OPEN_MODERNUI_OVERVIEW_PREVIEW.consumeClick() || edgeTriggered) {
            OverviewPreviewLauncher.open();
        }
    }
}
