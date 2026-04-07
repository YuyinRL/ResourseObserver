package com.yuyinrl.resourceobserver.client.input;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.screen.OverviewConfigPreviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** 客户端输入监听：注册并处理 Ctrl+K 打开 Overview 迁移预览。 */
@EventBusSubscriber(modid = ResourceObserverMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeyInputHandler {

    private ClientKeyInputHandler() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.OPEN_MODERN_OVERVIEW_PREVIEW);
    }

    @EventBusSubscriber(modid = ResourceObserverMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Runtime {

        private Runtime() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return;
            }
            if (!ModKeyMappings.OPEN_MODERN_OVERVIEW_PREVIEW.consumeClick()) {
                return;
            }
            if (!hasCtrlDown()) {
                return;
            }
            if (mc.screen instanceof ChatScreen) {
                return;
            }
            mc.setScreen(new OverviewConfigPreviewScreen(mc.screen));
        }

        private static boolean hasCtrlDown() {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
    }
}
