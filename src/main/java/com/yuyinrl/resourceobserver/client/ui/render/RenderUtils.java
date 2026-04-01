package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class RenderUtils {
    private RenderUtils() {
    }

    public static void fillPanel(GuiGraphics gfx, UiRect rect, int fillColor, int borderColor) {
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fillColor);
        drawBorder(gfx, rect, borderColor);
    }

    public static void drawBorder(GuiGraphics gfx, UiRect rect, int color) {
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        gfx.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        gfx.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        gfx.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
    }

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

    public static boolean drawItemIconOrSprite(
            GuiGraphics gfx,
            String itemId,
            int x,
            int y,
            int size,
            ResourceLocation fallbackSprite
    ) {
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
