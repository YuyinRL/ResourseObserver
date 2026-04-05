package com.yuyinrl.resourceobserver.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

import java.util.List;

/**
 * 通用渲染工具类 —— 提供终端界面中常用的绘制方法。
 * 包括面板填充、边框绘制、进度条、文本省略、折线、物品图标等。
 */
public final class RenderUtils {
    private RenderUtils() {
    }

    /** 绘制带边框的填充面板 */
    public static void fillPanel(GuiGraphics gfx, UiRect rect, int fillColor, int borderColor) {
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fillColor);
        drawBorder(gfx, rect, borderColor);
    }

    /** 绘制 1px 宽的矩形边框 */
    public static void drawBorder(GuiGraphics gfx, UiRect rect, int color) {
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        gfx.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        gfx.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        gfx.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
    }

    /**
     * 绘制进度条。
     * @param ratio 填充比例（0.0 ~ 1.0）
     */
    public static void drawProgressBar(
            GuiGraphics gfx,
            int x,
            int y,
            int width,
            int height,
            double ratio,
            int bgColor,
            int fgColor
    ) {
        gfx.fill(x, y, x + width, y + height, bgColor);
        int filled = Math.max(0, Math.min(width, (int) Math.round(width * ratio)));
        gfx.fill(x, y, x + filled, y + height, fgColor);
    }

    /**
     * 文本省略处理 —— 当文本超出最大宽度时截断并添加 "..."。
     */
    public static String ellipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int dotWidth = font.width(dots);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (font.width(sb.toString()) + dotWidth > maxWidth) {
                return sb.substring(0, Math.max(0, sb.length() - 1)) + dots;
            }
        }
        return text;
    }

    /**
     * 绘制折线图（简易版）。
     * 将数据点序列归一化后在指定区域内使用 Bresenham 算法绘制连线。
     */
    public static void drawPolyline(
            GuiGraphics gfx,
            List<Double> values,
            int minX,
            int minY,
            int width,
            int height,
            int color
    ) {
        if (values.size() < 2) {
            return;
        }
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double range = Math.max(1.0, max - min);
        int prevX = minX;
        int prevY = minY + height - (int) ((values.get(0) - min) / range * height);
        for (int i = 1; i < values.size(); i++) {
            int x = minX + (int) ((i / (double) (values.size() - 1)) * width);
            int y = minY + height - (int) ((values.get(i) - min) / range * height);
            drawLine(gfx, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * Bresenham 直线算法 —— 在两点之间逐像素绘制线段。
     * 使用整数运算实现高效的光栅化直线绘制。
     */
    public static void drawLine(GuiGraphics gfx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int x = x0;
        int y = y0;
        while (true) {
            gfx.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * 绘制物品图标或回退精灵。
     * 优先尝试从物品注册表获取真实物品图标；
     * 若 itemId 以 "fluid:" 开头，则尝试从流体注册表获取流体纹理并绘制；
     * 失败时使用 fallbackSprite 精灵图作为替代。
     * @return 是否成功绘制了真实物品/流体图标
     */
    public static boolean drawItemIconOrSprite(
            GuiGraphics gfx,
            String itemId,
            int x,
            int y,
            int size,
            ResourceLocation fallbackSprite
    ) {
        // 流体：以 "fluid:" 开头
        if (itemId != null && itemId.startsWith(FLUID_PREFIX)) {
            return drawFluidIconOrSprite(gfx, itemId.substring(FLUID_PREFIX.length()), x, y, size, fallbackSprite);
        }
        // 普通物品
        ItemStack stack = itemStackFromItemId(itemId);
        if (!stack.isEmpty()) {
            float scale = size / 16.0f;
            gfx.pose().pushPose();
            gfx.pose().translate(x, y, 0);
            gfx.pose().scale(scale, scale, 1.0f);
            gfx.renderItem(stack, 0, 0);
            gfx.pose().popPose();
            return true;
        }
        gfx.blitSprite(fallbackSprite, x, y, size, size);
        return false;
    }

    private static final String FLUID_PREFIX = "fluid:";

    /**
     * 绘制流体图标：从流体注册表查找流体，获取静态纹理并带染色绘制。
     * 失败时回退到 fallbackSprite。
     */
    private static boolean drawFluidIconOrSprite(
            GuiGraphics gfx,
            String fluidIdStr,
            int x,
            int y,
            int size,
            ResourceLocation fallbackSprite
    ) {
        try {
            ResourceLocation fluidId = ResourceLocation.parse(fluidIdStr);
            Fluid fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
            if (fluid == null || fluid == Fluids.EMPTY) {
                gfx.blitSprite(fallbackSprite, x, y, size, size);
                return false;
            }
            IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid);
            FluidStack fluidStack = new FluidStack(fluid, 1000);
            ResourceLocation stillTexture = fluidExt.getStillTexture(fluidStack);
            if (stillTexture == null) {
                gfx.blitSprite(fallbackSprite, x, y, size, size);
                return false;
            }
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(stillTexture);
            int tintColor = fluidExt.getTintColor(fluidStack);
            float a = ((tintColor >> 24) & 0xFF) / 255.0f;
            float r = ((tintColor >> 16) & 0xFF) / 255.0f;
            float g = ((tintColor >> 8) & 0xFF) / 255.0f;
            float b = (tintColor & 0xFF) / 255.0f;
            if (a <= 0.0f) {
                a = 1.0f; // 如果 alpha 为 0 则默认不透明
            }
            // 刷新缓冲区，确保后续着色器颜色变更立即生效
            gfx.flush();
            gfx.pose().pushPose();
            gfx.pose().translate(x, y, 0);
            // 显式绑定方块纹理图集，防止纹理未绑定导致渲染异常
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor(r, g, b, a);
            gfx.blit(0, 0, 0, size, size, sprite);
            gfx.flush();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gfx.pose().popPose();
            return true;
        } catch (Exception ignored) {
            gfx.blitSprite(fallbackSprite, x, y, size, size);
            return false;
        }
    }

    /** 从物品 ID 字符串获取 ItemStack，无效 ID 返回空栈 */
    private static ItemStack itemStackFromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
