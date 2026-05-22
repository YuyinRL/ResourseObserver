package com.yuyinrl.resourceobserver.service.snapshot.power;

/**
 * Power 域本地化抽象 —— 服务端默认 IDENTITY；客户端实现走 {@code Component.translatable}。
 */
public interface PowerLocalizer {

    PowerLocalizer IDENTITY = new PowerLocalizer() {
        @Override
        public String localizeKey(String translationKey) {
            return translationKey == null ? "" : translationKey;
        }

        @Override
        public String localizeKey(String translationKey, Object... args) {
            return translationKey == null ? "" : translationKey;
        }
    };

    String localizeKey(String translationKey);

    String localizeKey(String translationKey, Object... args);
}
