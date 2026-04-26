package com.yuyinrl.resourceobserver.client.web;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 客户端线程上的物品图标渲染 —— 把 {@link ItemStack} 渲染到一个离屏 {@link TextureTarget}，
 * 读回像素并以 PNG 形式落盘。
 * <p>
 * 该类只能在客户端 ClassLoader 加载（依赖 {@link Minecraft}/{@link RenderTarget} 等 client-only 类），
 * {@link com.yuyinrl.resourceobserver.web.handler.IconHandler} 通过反射延迟加载本类。
 */
public final class IconRenderer {

    /** 渲染分辨率 —— 32x32 在 DPI 屏幕上仍然清晰，且文件体积小。 */
    private static final int SIZE = 32;
    private static final String CACHE_DIR = "resourceobserver-icons-v4";

    private IconRenderer() {}

    /**
     * 阻塞调用：在客户端线程上渲染物品并写入 PNG，返回缓存文件路径。
     * 若已存在缓存则直接返回；如果物品不存在或渲染失败返回 {@code null}。
     * <p>
     * 调用顺序：
     * <ol>
     *     <li>磁盘缓存命中 → 直接返回</li>
     *     <li>离屏真实渲染（与 ModernUI 的 {@code ItemTextureCache} 使用同一绘制入口）</li>
     * </ol>
     */
    public static @Nullable Path renderToCache(String itemId, String cacheKey) {
        Path dir = Paths.get("cache", CACHE_DIR);
        Path file = dir.resolve(cacheKey + ".png");
        try {
            if (Files.exists(file)) {
                return file;
            }
            Files.createDirectories(dir);
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[IconRenderer] 创建缓存目录失败: {}", e.toString());
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;

        // 防御：如果当前已经在 render 线程，直接放弃离屏渲染（会死锁）
        if (mc.isSameThread()) {
            ResourceObserverMod.LOGGER.debug("[IconRenderer] 跳过 {} 离屏渲染：当前在 render 线程", itemId);
            return null;
        }

        CompletableFuture<NativeImage> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                NativeImage image = renderOnRenderThread(mc, itemId);
                future.complete(image);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try (NativeImage image = future.get(5, TimeUnit.SECONDS)) {
            if (image == null) return null;
            image.writeToFile(file);
            return file;
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.warn("[IconRenderer] 渲染 {} 失败: {}", itemId, t.toString());
            return null;
        }
    }

    /** 在 render thread 上完成离屏渲染并下载像素。 */
    private static NativeImage renderOnRenderThread(Minecraft mc, String itemId) {
        RenderTarget target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, SIZE, SIZE);

        RenderSystem.backupProjectionMatrix();
        Matrix4f ortho = new Matrix4f().setOrtho(0f, SIZE, SIZE, 0f, 1000f, 21000f);
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

        org.joml.Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        mv.translate(0f, 0f, -11000f);
        RenderSystem.applyModelViewMatrix();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        GuiGraphics gg = new GuiGraphics(mc, bufferSource);
        try {
            RenderUtils.drawItemIconOrSprite(gg, itemId, 0, 0, SIZE, TerminalSprites.TABLE_ITEM);
            gg.flush();
        } finally {
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
            RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        }

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        img.downloadTexture(0, false);
        img.flipY();

        target.destroyBuffers();
        return img;
    }
}
