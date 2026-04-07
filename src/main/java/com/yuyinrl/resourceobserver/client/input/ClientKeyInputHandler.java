package com.yuyinrl.resourceobserver.client.input;

import com.yuyinrl.resourceobserver.client.screen.OverviewConfigPreviewScreen;
import com.yuyinrl.resourceobserver.client.ui.modern.ModernUiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 客户端输入监听：注册并处理 Ctrl+K 打开 Overview 迁移预览。 */
public final class ClientKeyInputHandler {

    private ClientKeyInputHandler() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientKeyInputHandler::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientKeyInputHandler::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.OPEN_MODERN_OVERVIEW_PREVIEW);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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
        if (ModernUiRuntime.tryOpenOverviewPreview(mc)) {
            return;
        }
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.resourceobserver.modernui.missing"));
        }
        mc.setScreen(new OverviewConfigPreviewScreen(mc.screen));
    }

    private static boolean hasCtrlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
