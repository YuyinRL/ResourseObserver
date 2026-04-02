package com.yuyinrl.resourceobserver.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public final class ChartRenderShaders {
    private static final ResourceLocation CHART_LINE_ID = ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "chart_line");
    private static final ResourceLocation CHART_DOWNSAMPLE_ID = ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "chart_downsample_4x");

    private static ShaderInstance chartLineShader;
    private static ShaderInstance chartDownsampleShader;

    private ChartRenderShaders() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ChartRenderShaders::onRegisterShaders);
    }

    public static boolean isReady() {
        return chartLineShader != null && chartDownsampleShader != null;
    }

    public static ShaderInstance chartLineShader() {
        return chartLineShader;
    }

    public static ShaderInstance chartDownsampleShader() {
        return chartDownsampleShader;
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CHART_LINE_ID.toString(), DefaultVertexFormat.POSITION), shader -> {
                chartLineShader = shader;
                ChartRenderer.invalidateAllCaches();
            });
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CHART_DOWNSAMPLE_ID.toString(), DefaultVertexFormat.POSITION), shader -> {
                chartDownsampleShader = shader;
                ChartRenderer.invalidateAllCaches();
            });
        } catch (IOException ex) {
            throw new RuntimeException("Failed to register chart shaders", ex);
        }
    }
}
