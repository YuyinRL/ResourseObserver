package com.yuyinrl.resourceobserver.client.ui.snapshot;

import com.yuyinrl.resourceobserver.client.util.PinyinMatcher;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageLocalizer;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageSnapshotBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端实现：基于 {@code BuiltInRegistries.ITEM} + {@code Component.translatable}
 * 把 Snapshot 中的翻译键 / 物品 ID 解析为本地化字符串。
 *
 * <p>同时提供 {@link #PINYIN_MATCHER} 让 {@link StorageSnapshotBuilder} 支持中文拼音首字母搜索。
 *
 * <p>仅可在 ClientLevel / Render 线程调用。服务端 / 测试请使用 {@link StorageLocalizer#IDENTITY}。
 */
public final class ClientStorageLocalizer implements StorageLocalizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStorageLocalizer.class);

    /** 客户端单例 */
    public static final ClientStorageLocalizer INSTANCE = new ClientStorageLocalizer();

    /** 拼音首字母匹配器适配 {@link StorageSnapshotBuilder.SearchMatcher} */
    public static final StorageSnapshotBuilder.SearchMatcher PINYIN_MATCHER = PinyinMatcher::matches;

    private ClientStorageLocalizer() {
    }

    @Override
    public String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback) {
        if (itemId == null || itemId.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        if (type == ObserverDataPayload.EntryType.FLUID) {
            return (fallback == null || fallback.isBlank()) ? itemId : fallback;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item != Items.AIR) {
                String translated = new ItemStack(item).getHoverName().getString();
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("localizeEntryName failed for {}: {}", itemId, e.toString());
        }
        return (fallback == null || fallback.isBlank()) ? itemId : fallback;
    }

    @Override
    public String localizeKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            return "";
        }
        return Component.translatable(translationKey).getString();
    }
}
