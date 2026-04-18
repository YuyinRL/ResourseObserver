package com.yuyinrl.resourceobserver.ui.state;

import com.yuyinrl.resourceobserver.network.EnumLookup;

/**
 * 表格分组筛选枚举 —— 按物品分类过滤表格显示。
 * 这些是预定义的系统分组，玩家还可以创建自定义分组。
 * - ALL：显示所有分组的物品
 * - RAW：原材料分组
 * - INTERMEDIATE：中间产物分组
 * - FINISHED：成品分组
 * - COMMON_PARTS：通用零件分组
 */
public enum TableGroupFilter {
    ALL(0, "all", "screen.resourceobserver.overview.filter.group.all"),                     // 全部
    RAW(1, "raw", "screen.resourceobserver.overview.filter.group.raw"),                     // 原材料
    INTERMEDIATE(2, "intermediate", "screen.resourceobserver.overview.filter.group.intermediate"), // 中间产物
    FINISHED(3, "finished", "screen.resourceobserver.overview.filter.group.finished"),       // 成品
    COMMON_PARTS(4, "common_parts", "screen.resourceobserver.overview.filter.group.common_parts"); // 通用零件

    private final int id;
    private final String key;
    private final String translationKey;

    TableGroupFilter(int id, String key, String translationKey) {
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

    /** 根据 ID 查找分组筛选，未找到时默认返回 ALL */
    public static TableGroupFilter fromId(int id) {
        return EnumLookup.fromId(values(), TableGroupFilter::id, id, ALL);
    }

    /** 根据键名查找分组筛选（不区分大小写），未找到时默认返回 ALL */
    public static TableGroupFilter fromKey(String key) {
        return EnumLookup.fromKey(values(), TableGroupFilter::key, key, ALL);
    }
}
