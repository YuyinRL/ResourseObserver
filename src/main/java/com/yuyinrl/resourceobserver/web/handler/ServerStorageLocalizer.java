package com.yuyinrl.resourceobserver.web.handler;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.storage.StorageLocalizer;

/**
 * Web 服务端 Storage 本地化适配器。
 * <p>显示名优先使用客户端镜像/服务端资源索引解析结果；翻译键保持原样交给前端兜底处理。</p>
 */
public final class ServerStorageLocalizer implements StorageLocalizer {
    public static final ServerStorageLocalizer INSTANCE = new ServerStorageLocalizer();

    private ServerStorageLocalizer() {
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
    public String localizeKey(String translationKey) {
        return translationKey == null ? "" : translationKey;
    }
}
