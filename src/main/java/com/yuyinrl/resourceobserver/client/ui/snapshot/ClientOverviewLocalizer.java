package com.yuyinrl.resourceobserver.client.ui.snapshot;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewLocalizer;
import com.yuyinrl.resourceobserver.service.snapshot.overview.TableOps;
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
 * <p>仅可在 ClientLevel / Render 线程调用（依赖 BuiltInRegistries 与 Minecraft 客户端 i18n）。
 * 服务端 / 单元测试请使用 {@link OverviewLocalizer#IDENTITY}。
 */
public final class ClientOverviewLocalizer implements OverviewLocalizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientOverviewLocalizer.class);

    /** 单例。无状态，可全局复用。 */
    public static final ClientOverviewLocalizer INSTANCE = new ClientOverviewLocalizer();

    private ClientOverviewLocalizer() {
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
    public String localizeGroupLabel(String groupKey, String fallback) {
        if (TableOps.GROUP_UNGROUPED.equals(groupKey)) {
            return Component.translatable("screen.resourceobserver.overview.group.ungrouped").getString();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return groupKey == null ? "" : groupKey;
    }

    @Override
    public String localizeKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            return "";
        }
        return Component.translatable(translationKey).getString();
    }

    @Override
    public String localizeKey(String translationKey, Object... args) {
        if (translationKey == null || translationKey.isBlank()) {
            return "";
        }
        return Component.translatable(translationKey, args).getString();
    }
}
