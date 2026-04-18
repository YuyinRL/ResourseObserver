package com.yuyinrl.resourceobserver.ui.state;

import com.yuyinrl.resourceobserver.network.EnumLookup;

/**
 * 表格状态筛选枚举 —— 根据物品的流通状态过滤显示。
 * - ALL：显示所有物品
 * - SURPLUS：仅显示盈余物品（净变化 > 0）
 * - DEFICIT：仅显示亏损物品（净变化 < 0）
 * - CRITICAL：仅显示严重亏损物品（净变化远低于 0 或库存极低）
 */
public enum TableStatusFilter {
    ALL(0, "all", "screen.resourceobserver.overview.filter.status.all"),           // 全部
    SURPLUS(1, "surplus", "screen.resourceobserver.overview.filter.status.surplus"),   // 盈余
    DEFICIT(2, "deficit", "screen.resourceobserver.overview.filter.status.deficit"),   // 亏损
    CRITICAL(3, "critical", "screen.resourceobserver.overview.filter.status.critical"); // 严重亏损

    private final int id;
    private final String key;
    private final String translationKey;

    TableStatusFilter(int id, String key, String translationKey) {
        this.id = id;
        this.key = key;
        this.translationKey = translationKey;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return translationKey;
    }

    /** 根据 ID 查找状态筛选，未找到时默认返回 ALL */
    public static TableStatusFilter fromId(int id) {
        return EnumLookup.fromId(values(), TableStatusFilter::id, id, ALL);
    }

    /** 根据键名查找状态筛选（不区分大小写），未找到时默认返回 ALL */
    public static TableStatusFilter fromKey(String key) {
        return EnumLookup.fromKey(values(), TableStatusFilter::key, key, ALL);
    }
}
