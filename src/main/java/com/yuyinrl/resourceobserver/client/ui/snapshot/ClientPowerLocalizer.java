package com.yuyinrl.resourceobserver.client.ui.snapshot;

import com.yuyinrl.resourceobserver.service.snapshot.power.PowerLocalizer;
import net.minecraft.network.chat.Component;

/**
 * 客户端 Power 本地化实现 —— 基于 {@code Component.translatable}。
 */
public final class ClientPowerLocalizer implements PowerLocalizer {

    public static final ClientPowerLocalizer INSTANCE = new ClientPowerLocalizer();

    private ClientPowerLocalizer() {
    }

    @Override
    public String localizeKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) return "";
        return Component.translatable(translationKey).getString();
    }

    @Override
    public String localizeKey(String translationKey, Object... args) {
        if (translationKey == null || translationKey.isBlank()) return "";
        return Component.translatable(translationKey, args).getString();
    }
}
