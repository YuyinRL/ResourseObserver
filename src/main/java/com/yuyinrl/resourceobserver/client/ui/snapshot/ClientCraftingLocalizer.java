package com.yuyinrl.resourceobserver.client.ui.snapshot;

import com.yuyinrl.resourceobserver.client.util.ItemNames;
import com.yuyinrl.resourceobserver.client.util.PinyinMatcher;
import com.yuyinrl.resourceobserver.service.snapshot.crafting.CraftingLocalizer;
import com.yuyinrl.resourceobserver.service.snapshot.crafting.CraftingSearchMatcher;

/**
 * 客户端 Crafting 本地化 + 搜索匹配实现 —— 桥接 {@link ItemNames} 和 {@link PinyinMatcher}。
 */
public final class ClientCraftingLocalizer {

    public static final CraftingLocalizer LOCALIZER = ItemNames::localize;

    public static final CraftingSearchMatcher MATCHER = (text, query) -> {
        if (query == null || query.isEmpty()) return true;
        return PinyinMatcher.matches(text == null ? "" : text, query);
    };

    private ClientCraftingLocalizer() {
    }
}
