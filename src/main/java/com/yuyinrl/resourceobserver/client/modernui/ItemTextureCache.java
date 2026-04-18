package com.yuyinrl.resourceobserver.client.modernui;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.ui.TerminalSprites;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品图标纹理缓存 —— 使用 FBO 离屏渲染物品/流体图标，
 * 将像素数据读回 CPU 创建 MUI {@link Bitmap}，
 * 再由 {@link InlineItemIconView#onDraw} 在 MUI 渲染线程上
 * 延迟创建 {@link Image} 并通过 {@code Canvas.drawImage()} 绘制。
 * <p>
 * 两阶段缓存设计：
 * <ol>
 *   <li><b>bitmapCache</b>：CPU 端像素数据，在 Minecraft 渲染线程（Render.Post）创建</li>
 *   <li><b>imageCache</b>：GPU 端 MUI 纹理，在 MUI 绘制线程（onDraw）延迟创建</li>
 * </ol>
 * 分离两阶段确保 GPU 纹理在正确的渲染上下文中创建。
 */
public final class ItemTextureCache {

    /** 离屏渲染尺寸（像素） */
    private static final int ICON_SIZE = 32;
    /** 每帧最大渲染数量，防止帧率骤降 */
    private static final int MAX_RENDERS_PER_FRAME = 50;

    private static final ItemTextureCache INSTANCE = new ItemTextureCache();

    /** itemId → CPU 端 Bitmap（由 Minecraft 渲染线程创建） */
    private final Map<String, Bitmap> bitmapCache = new ConcurrentHashMap<>();
    /** itemId → GPU 端 MUI Image（由 MUI 绘制线程延迟创建） */
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    /** 待渲染的 itemId 集合 */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    /** 渲染失败的 itemId 集合（避免反复重试） */
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    /** 共享离屏 FBO（延迟初始化） */
    private TextureTarget fbo;

    private ItemTextureCache() {}

    public static ItemTextureCache getInstance() {
        return INSTANCE;
    }

    /**
     * 请求缓存指定物品的图标纹理。
     * 若已缓存或已失败则忽略；否则加入待渲染队列。
     */
    public void requestItem(String itemId) {
        if (itemId != null && !itemId.isBlank()
                && !bitmapCache.containsKey(itemId)
                && !failed.contains(itemId)) {
            pending.add(itemId);
        }
    }

    /**
     * 检查指定物品的 Bitmap 是否已就绪（CPU 端缓存）。
     */
    public boolean hasBitmap(String itemId) {
        return itemId != null && bitmapCache.containsKey(itemId);
    }

    /**
     * 获取或延迟创建 MUI Image —— 应在 MUI 绘制线程中调用。
     * <p>
     * 首次调用时从 Bitmap 创建 Image（GPU 纹理上传），
     * 后续调用直接返回缓存的 Image。
     *
     * @return Image 或 null（Bitmap 尚未就绪）
     */
    @Nullable
    public Image getOrCreateImage(String itemId) {
        if (itemId == null) return null;

        // 快速路径：Image 已缓存
        Image image = imageCache.get(itemId);
        if (image != null) return image;

        // 检查 Bitmap 是否就绪
        Bitmap bitmap = bitmapCache.get(itemId);
        if (bitmap == null) return null;

        // 在 MUI 绘制线程上创建 Image（确保正确的 GPU 上下文）
        try {
            var rc = Core.peekUiRecordingContext();
            if (rc == null) {
                return null;
            }
            image = Image.createTextureFromBitmap(rc, bitmap);
            if (image != null) {
                imageCache.put(itemId, image);
            } else {
                ResourceObserverMod.LOGGER.warn("[ItemTextureCache] createTextureFromBitmap returned null for: {}", itemId);
            }
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[ItemTextureCache] Failed to create MUI Image for: {}", itemId, e);
        }
        return image;
    }

    /**
     * 在 {@code ScreenEvent.Render.Post} 中调用 —— 处理待渲染队列。
     * <p>
     * 将物品渲染到 FBO，读回像素创建 Bitmap（CPU 端）。
     * Image 的创建推迟到 MUI 绘制线程中。
     */
    public void renderPending(GuiGraphics gfx) {
        if (pending.isEmpty()) return;

        // 延迟创建 FBO
        if (fbo == null) {
            fbo = new TextureTarget(ICON_SIZE, ICON_SIZE, true, Minecraft.ON_OSX);
        }

        // 取出本帧要处理的物品
        List<String> batch = new ArrayList<>(Math.min(pending.size(), MAX_RENDERS_PER_FRAME));
        var iter = pending.iterator();
        while (iter.hasNext() && batch.size() < MAX_RENDERS_PER_FRAME) {
            String id = iter.next();
            iter.remove();
            if (!bitmapCache.containsKey(id) && !failed.contains(id)) {
                batch.add(id);
            }
        }
        if (batch.isEmpty()) return;

        // 先刷新 gfx 中可能残留的主屏幕绘制命令（避免在 FBO 上下文中误刷到 FBO）
        gfx.flush();

        int successCount = 0;
        try (var snapshot = new GlStateSnapshot()) {
            for (String itemId : batch) {
                if (renderSingleItem(gfx, itemId)) {
                    successCount++;
                }
            }
        }

        if (successCount > 0) {
            ResourceObserverMod.LOGGER.debug("[ItemTextureCache] rendered {} bitmaps this frame (batch={})",
                    successCount, batch.size());
        }
    }

    /**
     * GL 渲染状态快照 —— 保存并在关闭时自动恢复视口、投影矩阵、裁剪测试等 GL 状态。
     * 配合 try-with-resources 使用，确保即使发生异常也能正确恢复状态。
     */
    private static final class GlStateSnapshot implements AutoCloseable {
        private final int prevViewportW;
        private final int prevViewportH;
        private final Matrix4f savedProjection;
        private final boolean scissorWasEnabled;
        private final int[] savedScissorBox;

        GlStateSnapshot() {
            var window = Minecraft.getInstance().getWindow();
            this.prevViewportW = window.getWidth();
            this.prevViewportH = window.getHeight();
            this.savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            this.scissorWasEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
            this.savedScissorBox = new int[4];
            if (scissorWasEnabled) {
                GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, savedScissorBox);
                RenderSystem.disableScissor();
            }
        }

        @Override
        public void close() {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(0, 0, prevViewportW, prevViewportH);
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            if (scissorWasEnabled) {
                RenderSystem.enableScissor(
                        savedScissorBox[0], savedScissorBox[1],
                        savedScissorBox[2], savedScissorBox[3]);
            }
        }
    }

    /**
     * 渲染单个物品到 FBO 并读回为 Bitmap 存入缓存。
     * @return true 如果成功
     */
    private boolean renderSingleItem(GuiGraphics gfx, String itemId) {
        try {
            // 清空 FBO（透明背景）
            fbo.setClearColor(0f, 0f, 0f, 0f);
            fbo.clear(Minecraft.ON_OSX);
            fbo.bindWrite(true);
            RenderSystem.viewport(0, 0, ICON_SIZE, ICON_SIZE);

            // 设置正交投影匹配 FBO 尺寸（Minecraft GUI 坐标系：Y 轴向下）
            Matrix4f projection = new Matrix4f().setOrtho(
                    0, ICON_SIZE,  // left, right
                    ICON_SIZE, 0,  // bottom, top (flipped Y for GUI)
                    1000, 21000);  // near, far (matching Minecraft GUI)
            RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

            // ★ 注意：不要在 PoseStack 中添加 Z 偏移！
            // RenderSystem.getModelViewMatrix() 已经包含了 Minecraft GUI 的
            // translate(0,0,-11000)，如果在 PoseStack 中再次添加同样的偏移，
            // 会导致最终 Z 坐标超出远裁剪面 (-21000)，所有顶点被裁掉。
            gfx.pose().pushPose();
            gfx.pose().setIdentity();

            // renderItem 内部已调用 flush()，绘制会直接输出到当前绑定的 FBO
            RenderUtils.drawItemIconOrSprite(gfx, itemId, 0, 0, ICON_SIZE, TerminalSprites.TABLE_ITEM);
            // 保险起见再 flush 一次（处理流体/sprite 等走不同渲染路径的情况）
            gfx.flush();

            gfx.pose().popPose();

            // 将 FBO 内容通过 glReadPixels 读回为 Bitmap
            Bitmap bitmap = readFboToBitmap(itemId);
            if (bitmap != null) {
                bitmapCache.put(itemId, bitmap);
                return true;
            } else {
                failed.add(itemId);
            }
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[ItemTextureCache] Failed to cache icon for item: {}", itemId, e);
            failed.add(itemId);
        }
        return false;
    }

    /**
     * 从当前绑定的 FBO 通过 {@code glReadPixels} 读取像素并创建 MUI Bitmap。
     * <p>
     * 使用 {@code glReadPixels} 而非 {@code NativeImage.downloadTexture}，
     * 更直接、更可靠，与 ChartRenderer 保持一致。
     * <p>
     * OpenGL FBO 坐标系 Y=0 在底部，但我们的正交投影已翻转 Y，
     * 所以纹理中 Y=0 行就是 GUI 的顶部行，需要翻转读回的数据。
     */
    @Nullable
    private Bitmap readFboToBitmap(String itemId) {
        try {
            // 确保 GPU 渲染完成
            GL11C.glFinish();

            // 绑定 FBO 读取（bindWrite 同时也设置了 READ_FRAMEBUFFER）
            fbo.bindWrite(false);

            // glReadPixels 直接从 framebuffer 读取 RGBA 数据
            ByteBuffer pixels = BufferUtils.createByteBuffer(ICON_SIZE * ICON_SIZE * 4);
            GL11C.glReadPixels(0, 0, ICON_SIZE, ICON_SIZE, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);

            // glReadPixels 返回的是 OpenGL 坐标（Y=0 在底部），需要上下翻转
            // 翻转后写入 Bitmap 用的 ByteBuffer
            ByteBuffer flipped = ByteBuffer.allocateDirect(ICON_SIZE * ICON_SIZE * 4);
            flipped.order(ByteOrder.nativeOrder());
            int rowBytes = ICON_SIZE * 4;
            for (int row = ICON_SIZE - 1; row >= 0; row--) {
                pixels.position(row * rowBytes);
                pixels.limit(row * rowBytes + rowBytes);
                flipped.put(pixels);
            }
            flipped.flip();

            Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Format.RGBA_8888);
            // 参数顺序: (Buffer src, int rowStride, int x, int y, int width, int height)
            boolean ok = bitmap.copyPixelsFromBuffer(flipped, ICON_SIZE * 4, 0, 0, ICON_SIZE, ICON_SIZE);
            if (!ok) {
                ResourceObserverMod.LOGGER.warn("[ItemTextureCache] copyPixelsFromBuffer returned false for '{}'", itemId);
                bitmap.close();
                return null;
            }
            return bitmap;
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[ItemTextureCache] Failed to read FBO pixels for: {}", itemId, e);
            return null;
        }
    }

    /** 清空全部缓存（在界面关闭时调用） */
    public void clear() {
        for (Image img : imageCache.values()) {
            try { img.close(); } catch (Exception ignored) {}
        }
        imageCache.clear();
        for (Bitmap bmp : bitmapCache.values()) {
            try { bmp.close(); } catch (Exception ignored) {}
        }
        bitmapCache.clear();
        pending.clear();
        failed.clear();
    }

    /** 释放 FBO 资源 */
    public void dispose() {
        clear();
        if (fbo != null) {
            fbo.destroyBuffers();
            fbo = null;
        }
    }
}
