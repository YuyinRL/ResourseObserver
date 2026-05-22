package com.yuyinrl.resourceobserver.web.handler;

import com.yuyinrl.resourceobserver.service.snapshot.power.PowerLocalizer;

/**
 * Web 服务端 Power 本地化适配器。
 * <p>翻译键保持原样交给前端处理。</p>
 */
public final class ServerPowerLocalizer implements PowerLocalizer {
    public static final ServerPowerLocalizer INSTANCE = new ServerPowerLocalizer();

    private ServerPowerLocalizer() {
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
