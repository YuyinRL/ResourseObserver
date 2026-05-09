package com.yuyinrl.resourceobserver.web.handler;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewLocalizer;

/**
 * Web 服务端 Overview 本地化适配器。
 * <p>显示名优先使用服务端物品名解析；翻译键保持原样交给前端处理。</p>
 */
public final class ServerOverviewLocalizer implements OverviewLocalizer {
    public static final ServerOverviewLocalizer INSTANCE = new ServerOverviewLocalizer();

    private ServerOverviewLocalizer() {
    }

    @Override
    public String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback) {
        ItemNameResolver.Resolved resolved = ItemNameResolver.resolve(itemId);
        if (resolved.displayName() != null && !resolved.displayName().isBlank()) {
            return resolved.displayName();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return itemId == null ? "" : itemId;
    }

    @Override
    public String localizeGroupLabel(String groupKey, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return groupKey == null ? "" : groupKey;
    }

    @Override
    public String localizeKey(String translationKey) {
        return translationKey == null ? "" : translationKey;
    }

    @Override
    public String localizeKey(String translationKey, Object... args) {
        return translationKey == null ? "" : translationKey;
    }
}
