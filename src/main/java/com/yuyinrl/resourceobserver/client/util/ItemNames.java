package com.yuyinrl.resourceobserver.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 客户端物品名称本地化工具。
 * <p>
 * 服务端（尤其是专用服务端）只加载 en_us 语言文件，导致 {@code ItemStack.getHoverName()} 在那里
 * 返回英文。本工具运行在真正的客户端，{@link net.minecraft.locale.Language#getInstance()} 已加载
 * 玩家选择的语言（含 zh_cn），因此可重新解析 itemId → 翻译键 → 本地化字符串。
 */
public final class ItemNames {

    private ItemNames() {}

    /**
     * 根据 itemId 返回当前客户端语言下的物品显示名。
     *
     * @param itemId   形如 "minecraft:diamond" 的资源 ID
     * @param fallback 解析失败或为空时使用的回退名（通常为服务端给的 displayName）
     * @return 本地化显示名；解析失败时返回 fallback
     */
    public static String localize(String itemId, String fallback) {
        if (itemId == null || itemId.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            return fallback == null ? itemId : fallback;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) {
            return fallback == null ? itemId : fallback;
        }
        try {
            String name = new ItemStack(item).getHoverName().getString();
            if (name != null && !name.isEmpty()) {
                return name;
            }
            String desc = item.getDescriptionId();
            if (desc != null && !desc.isEmpty()) {
                String translated = Component.translatable(desc).getString();
                if (translated != null && !translated.isEmpty() && !translated.equals(desc)) {
                    return translated;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback == null || fallback.isEmpty() ? itemId : fallback;
    }
}
