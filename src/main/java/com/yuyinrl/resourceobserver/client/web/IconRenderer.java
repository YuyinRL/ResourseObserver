package com.yuyinrl.resourceobserver.client.web;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
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
    private static final String FLUID_PREFIX = "fluid:";

    private IconRenderer() {}

    /**
     * 阻塞调用：在客户端线程上渲染物品并写入 PNG，返回缓存文件路径。
     * 若已存在缓存则直接返回；如果物品不存在或渲染失败返回 {@code null}。
     */
    public static @Nullable Path renderToCache(String itemId, String cacheKey) {
        Path dir = Paths.get("cache", "resourceobserver-icons-v2");
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

        IconSource icon = resolveIcon(itemId);
        if (icon == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;

        CompletableFuture<NativeImage> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                NativeImage image = renderOnRenderThread(mc, icon);
                future.complete(image);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try (NativeImage image = future.get(2, TimeUnit.SECONDS)) {
            if (image == null) return null;
            image.writeToFile(file);
            return file;
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.debug("[IconRenderer] 渲染 {} 失败: {}", itemId, t.toString());
            return null;
        }
    }

    /**
     * 把 ID 解析为可渲染图标源。
     * <p>支持普通物品 ID、普通流体 ID，以及 {@code fluid:minecraft:water} 形式的显式流体 ID。
     */
    private static @Nullable IconSource resolveIcon(String iconId) {
        if (iconId == null || iconId.isBlank()) return null;

        if (iconId.startsWith(FLUID_PREFIX)) {
            ResourceLocation fluidId = ResourceLocation.tryParse(iconId.substring(FLUID_PREFIX.length()));
            Fluid fluid = resolveFluid(fluidId);
            return fluid != null ? IconSource.fluid(fluid) : null;
        }

        ResourceLocation rl = ResourceLocation.tryParse(iconId);
        if (rl == null) return null;

        if (BuiltInRegistries.ITEM.containsKey(rl)) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) return IconSource.item(stack);
            }
        }

        Fluid fluid = resolveFluid(rl);
        if (fluid != null) return IconSource.fluid(fluid);

        return null;
    }

    private static @Nullable Fluid resolveFluid(@Nullable ResourceLocation rl) {
        if (rl == null || !BuiltInRegistries.FLUID.containsKey(rl)) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }

    /** 在 render thread 上完成离屏渲染并下载像素。 */
    private static NativeImage renderOnRenderThread(Minecraft mc, IconSource icon) {
        RenderTarget target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        Matrix4f ortho = new Matrix4f().setOrtho(0f, 16f, 16f, 0f, 1000f, 21000f);
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

        org.joml.Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        mv.translate(0f, 0f, -11000f);
        RenderSystem.applyModelViewMatrix();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        GuiGraphics gg = new GuiGraphics(mc, bufferSource);
        try {
            if (icon.stack() != null) {
                gg.renderItem(icon.stack(), 0, 0);
            } else if (icon.fluid() != null) {
                renderFluidIcon(mc, gg, icon.fluid());
            }
            gg.flush();
        } finally {
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
        }

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        img.downloadTexture(0, false);
        img.flipY();

        target.destroyBuffers();
        return img;
    }

    /** 直接绘制流体 still texture，适配没有桶物品的流体、气体与浆液。 */
    private static void renderFluidIcon(Minecraft mc, GuiGraphics gg, Fluid fluid) {
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid);
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        ResourceLocation stillTexture = fluidExt.getStillTexture(fluidStack);
        if (stillTexture == null) {
            throw new IllegalStateException("missing fluid still texture: "
                    + BuiltInRegistries.FLUID.getKey(fluid));
        }

        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int tintColor = fluidExt.getTintColor(fluidStack);
        float a = ((tintColor >> 24) & 0xFF) / 255.0f;
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        if (a <= 0.0f) {
            a = 1.0f;
        }

        gg.flush();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderColor(r, g, b, a);
        gg.blit(0, 0, 0, 16, 16, sprite);
        gg.flush();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private record IconSource(@Nullable ItemStack stack, @Nullable Fluid fluid) {
        private static IconSource item(ItemStack stack) {
            return new IconSource(stack, null);
        }

        private static IconSource fluid(Fluid fluid) {
            return new IconSource(null, fluid);
        }
    }
}
