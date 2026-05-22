package com.yuyinrl.resourceobserver.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.modernui.ItemTextureCache;
import com.yuyinrl.resourceobserver.client.modernui.OverviewPreviewFragment;
import com.yuyinrl.resourceobserver.client.modernui.OverviewPreviewLauncher;
import com.yuyinrl.resourceobserver.client.modernui.ResourceTerminalFragment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

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
    private static final KeyMapping OPEN_TERMINAL_V2 = new KeyMapping(
            "key.resourceobserver.open_terminal_v2",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.resourceobserver"
    );
    private static boolean wasPreviewKeyDown;
    private static boolean wasV2KeyDown;

    private ClientKeyBindings() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientKeyBindings::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientKeyBindings::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ClientKeyBindings::onScreenKeyPressed);
        NeoForge.EVENT_BUS.addListener(ClientKeyBindings::onScreenRenderPost);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MODERNUI_OVERVIEW_PREVIEW);
        event.register(OPEN_TERMINAL_V2);
        ResourceObserverMod.LOGGER.info("Registered key mapping: key.resourceobserver.modernui_preview (default: J)");
        ResourceObserverMod.LOGGER.info("Registered key mapping: key.resourceobserver.open_terminal_v2 (default: F8)");
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasPreviewKeyDown = false;
            wasV2KeyDown = false;
            return;
        }

        if (mc.screen != null) {
            wasPreviewKeyDown = false;
            wasV2KeyDown = false;
            return;
        }

        boolean previewDown = OPEN_MODERNUI_OVERVIEW_PREVIEW.isDown();
        boolean previewEdge = previewDown && !wasPreviewKeyDown;
        wasPreviewKeyDown = previewDown;

        if (OPEN_MODERNUI_OVERVIEW_PREVIEW.consumeClick() || previewEdge) {
            OverviewPreviewLauncher.open();
        }

        boolean v2Down = OPEN_TERMINAL_V2.isDown();
        boolean v2Edge = v2Down && !wasV2KeyDown;
        wasV2KeyDown = v2Down;

        if (OPEN_TERMINAL_V2.consumeClick() || v2Edge) {
            mc.setScreen(new com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreenV2());
        }
    }

    /**
     * 按下背包键（默认 E）时关闭 ModernUI 终端/预览界面。
     * 当文本输入处于活动状态时（如分组名输入框）不关闭，以免吞掉输入。
     */
    private static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }

        ResourceTerminalFragment terminal = ResourceTerminalFragment.getActiveInstance();
        if (terminal != null) {
            if (terminal.isTextInputActive()) return;
            mc.setScreen(null);
            event.setCanceled(true);
            return;
        }

        if (OverviewPreviewFragment.getActiveInstance() != null) {
            mc.setScreen(null);
            event.setCanceled(true);
        }
    }

    /**
     * 在屏幕渲染后：
     * 1. 处理物品图标纹理缓存的待渲染队列（离屏 FBO 渲染）
     * 2. 原版风格的悬浮 Tooltip（Fragment 侧预构建好 tooltip 行列表）
     */
    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ResourceTerminalFragment terminal = ResourceTerminalFragment.getActiveInstance();
        if (terminal == null) return;

        Minecraft mc = Minecraft.getInstance();
        var gfx = event.getGuiGraphics();

        // ---- 1. 处理物品图标纹理缓存（离屏渲染，不影响当前帧） ----
        ItemTextureCache.getInstance().renderPending(gfx);

        // ---- 2. 绘制 Tooltip（不受裁剪影响） ----
        List<Component> lines = terminal.getHoveredTooltipLines();
        if (lines != null && !lines.isEmpty()) {
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            gfx.renderTooltip(mc.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }
}
