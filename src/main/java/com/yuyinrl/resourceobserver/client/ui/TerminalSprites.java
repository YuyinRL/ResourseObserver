package com.yuyinrl.resourceobserver.client.ui;

import net.minecraft.resources.ResourceLocation;

/**
 * 终端精灵图标常量 —— 定义终端界面中使用的所有图标资源位置。
 * 精灵图存储在 assets/resourceobserver/textures/gui/sprites/ 目录下。
 */
public final class TerminalSprites {
    private TerminalSprites() {
    }

    // ========== KPI 指标卡图标 ==========
    public static final ResourceLocation KPI_PRODUCTION = sprite("terminal/kpi_production");   // 生产量图标
    public static final ResourceLocation KPI_CONSUMPTION = sprite("terminal/kpi_consumption"); // 消耗量图标
    public static final ResourceLocation KPI_STORAGE = sprite("terminal/kpi_storage");         // 库存量图标
    public static final ResourceLocation KPI_EFFICIENCY = sprite("terminal/kpi_efficiency");   // 效率图标

    // ========== 页眉与操作按钮图标 ==========
    public static final ResourceLocation HEADER_STATUS = sprite("terminal/header_status");     // 连接状态图标
    public static final ResourceLocation ACTION_BELL = sprite("terminal/action_bell");         // 通知铃铛图标
    public static final ResourceLocation ACTION_SETTINGS = sprite("terminal/action_settings"); // 设置齿轮图标
    public static final ResourceLocation ACTION_LANG = sprite("terminal/action_lang");         // 语言切换图标

    // ========== 列表与表格图标 ==========
    public static final ResourceLocation WATCH_ITEM = sprite("terminal/watch_item");           // 关注列表物品图标
    public static final ResourceLocation TABLE_ITEM = sprite("terminal/table_item");           // 表格物品图标
    public static final ResourceLocation ICON_STAR = sprite("terminal/icon_star");             // 星标/关注图标

    /**
     * 解析精灵 ID 字符串为 ResourceLocation，无效时返回回退值。
     */
    public static ResourceLocation resolve(String id, ResourceLocation fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        return ResourceLocation.parse(id);
    }

    /** 构造模组命名空间下的精灵资源位置 */
    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath("resourceobserver", path);
    }
}
