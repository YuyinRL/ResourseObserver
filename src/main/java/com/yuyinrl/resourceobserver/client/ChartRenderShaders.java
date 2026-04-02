package com.yuyinrl.resourceobserver.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * 图表渲染着色器注册与管理。
 * 注册两个自定义 GLSL 着色器：
 * 1. chart_line —— 用于绘制图表中的抗锯齿线条
 * 2. chart_downsample_4x —— 用于超采样渲染后的降采样
 * <p>
 * 仅在客户端环境下注册，着色器加载完成后通知 ChartRenderer 清除缓存。
 */
public final class ChartRenderShaders {
    /** 图表线条着色器的资源路径 */
    private static final ResourceLocation CHART_LINE_ID = ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "chart_line");
    /** 降采样着色器的资源路径 */
    private static final ResourceLocation CHART_DOWNSAMPLE_ID = ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "chart_downsample_4x");

    /** 图表线条着色器实例 */
    private static ShaderInstance chartLineShader;
    /** 降采样着色器实例 */
    private static ShaderInstance chartDownsampleShader;

    private ChartRenderShaders() {
    }

    /** 将着色器注册监听器挂载到模组事件总线 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ChartRenderShaders::onRegisterShaders);
    }

    /** 检查着色器是否已全部加载就绪 */
    public static boolean isReady() {
        return chartLineShader != null && chartDownsampleShader != null;
    }

    public static ShaderInstance chartLineShader() {
        return chartLineShader;
    }

    public static ShaderInstance chartDownsampleShader() {
        return chartDownsampleShader;
    }

    /**
     * 着色器注册回调。
     * 使用 POSITION 顶点格式注册着色器，加载完成后清除图表渲染缓存。
     */
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
