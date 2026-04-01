package com.yuyinrl.resourceobserver.client.ui;

import net.minecraft.resources.ResourceLocation;

public final class TerminalSprites {
    private TerminalSprites() {
    }

    public static final ResourceLocation KPI_PRODUCTION = sprite("terminal/kpi_production");
    public static final ResourceLocation KPI_CONSUMPTION = sprite("terminal/kpi_consumption");
    public static final ResourceLocation KPI_STORAGE = sprite("terminal/kpi_storage");
    public static final ResourceLocation KPI_EFFICIENCY = sprite("terminal/kpi_efficiency");
    public static final ResourceLocation HEADER_STATUS = sprite("terminal/header_status");
    public static final ResourceLocation ACTION_BELL = sprite("terminal/action_bell");
    public static final ResourceLocation ACTION_SETTINGS = sprite("terminal/action_settings");
    public static final ResourceLocation ACTION_LANG = sprite("terminal/action_lang");
    public static final ResourceLocation WATCH_ITEM = sprite("terminal/watch_item");
    public static final ResourceLocation TABLE_ITEM = sprite("terminal/table_item");
    public static final ResourceLocation ICON_STAR = sprite("terminal/icon_star");

    public static ResourceLocation resolve(String id, ResourceLocation fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        return ResourceLocation.parse(id);
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath("resourceobserver", path);
    }
}
