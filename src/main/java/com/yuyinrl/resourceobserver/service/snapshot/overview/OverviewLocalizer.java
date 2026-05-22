package com.yuyinrl.resourceobserver.service.snapshot.overview;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

/**
 * Overview 本地化策略接口 —— 让 {@link OverviewSnapshotBuilder} 摆脱 MC 注册表 / i18n 直接依赖。
 *
 * <p>三层实现策略：
 * <ul>
 *     <li>客户端：基于 {@code BuiltInRegistries.ITEM} + {@code Component.translatable} 做完整本地化</li>
 *     <li>服务端（未来）：用语言文件原始键名或 fallback 直出</li>
 *     <li>测试 / 工具链：用 {@link #IDENTITY} 直接回退到原值</li>
 * </ul>
 */
public interface OverviewLocalizer {

    /**
     * 解析物品 / 流体的展示名。
     *
     * @param type     条目类型
     * @param itemId   注册 ID（如 "minecraft:iron_ingot"）
     * @param fallback 服务端预填的展示名
     * @return 本地化展示名；任何失败回退到 fallback
     */
    String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback);

    /**
     * 解析分组标签：系统组（如 GROUP_UNGROUPED）翻译为本地语言字符串；
     * 用户自定义分组直接返回 fallback。
     */
    String localizeGroupLabel(String groupKey, String fallback);

    /**
     * 解析 KPI 提示文本翻译键。
     *
     * @param translationKey 翻译键
     * @return 已解析字符串；不可用时返回翻译键本身
     */
    String localizeKey(String translationKey);

    /**
     * 解析带参数的翻译键（用于带格式化字符串的 hint，如 "+5%"）。
     */
    String localizeKey(String translationKey, Object... args);

    /** 恒等实现：所有方法回退到 fallback / 翻译键本身。用于测试 / 服务端裸数据塑形。 */
    OverviewLocalizer IDENTITY = new OverviewLocalizer() {
        @Override
        public String localizeEntryName(ObserverDataPayload.EntryType type, String itemId, String fallback) {
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
    };
}
