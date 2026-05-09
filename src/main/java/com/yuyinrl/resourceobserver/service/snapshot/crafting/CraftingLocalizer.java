package com.yuyinrl.resourceobserver.service.snapshot.crafting;

/**
 * 客户端注入的物品名本地化抽象。
 *
 * <p>service 层不依赖 MC 客户端类型 / JEC 拼音。{@code IDENTITY} 默认实现直接返回
 * {@code displayName}（或 itemId 兜底），客户端通过 {@code ClientCraftingLocalizer}
 * 提供真正的 {@link net.minecraft.client.resources.language.I18n} + {@code ItemNames} 实现。
 */
public interface CraftingLocalizer {

    /** 物品名本地化：itemId / displayName 任一可空，返回值不为 null */
    String localizeItem(String itemId, String displayName);

    /** 默认实现：直接返回 displayName，空则返回 itemId，再空返回 "" */
    CraftingLocalizer IDENTITY = (itemId, displayName) -> {
        if (displayName != null && !displayName.isBlank()) return displayName;
        if (itemId != null) return itemId;
        return "";
    };
}
