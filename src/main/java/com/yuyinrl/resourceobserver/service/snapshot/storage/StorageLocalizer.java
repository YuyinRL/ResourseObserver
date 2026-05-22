package com.yuyinrl.resourceobserver.service.snapshot.storage;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

/**
 * Storage 本地化策略接口 —— 让 {@link StorageSnapshotBuilder} 摆脱 MC 注册表 / i18n 直接依赖。
 *
 * <p>同 {@code OverviewLocalizer}，提供三层实现：
 * <ul>
 *     <li>客户端：基于 {@code BuiltInRegistries.ITEM} 解析物品名 / {@code Component.translatable} 解析翻译键</li>
 *     <li>服务端（未来）：fallback 直出</li>
 *     <li>测试：{@link #IDENTITY}</li>
 * </ul>
 */
public interface StorageLocalizer {

    /** 解析物品名，失败回退 fallback */
    String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback);

    /** 解析翻译键 */
    String localizeKey(String translationKey);

    /** Healthy / Alert 状态文本翻译 */
    default String localizeStatus(boolean alert) {
        return localizeKey(alert
                ? "screen.resourceobserver.storage.node.status.alert"
                : "screen.resourceobserver.storage.node.status.healthy");
    }

    /** 恒等实现：所有方法回退到 fallback / 翻译键本身 */
    StorageLocalizer IDENTITY = new StorageLocalizer() {
        @Override
        public String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback) {
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
            return itemId == null ? "" : itemId;
        }

        @Override
        public String localizeKey(String translationKey) {
            return translationKey == null ? "" : translationKey;
        }
    };
}
