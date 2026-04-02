package com.yuyinrl.resourceobserver.ui.state;

import java.util.Locale;

/**
 * 表格排序模式枚举 —— 定义物品流通表格的排序方式。
 * 每种模式有唯一 ID（用于网络传输）和键名（用于持久化）。
 */
public enum TableSortMode {
    NET_ABS(0, "net_abs", "screen.resourceobserver.overview.filter.sort.net_abs"),       // 按净变化量绝对值排序
    NET(1, "net", "screen.resourceobserver.overview.filter.sort.net"),                     // 按净变化量排序
    PRODUCTION(2, "production", "screen.resourceobserver.overview.filter.sort.production"), // 按生产量排序
    CONSUMPTION(3, "consumption", "screen.resourceobserver.overview.filter.sort.consumption"), // 按消耗量排序
    STOCK_RATIO(4, "stock_ratio", "screen.resourceobserver.overview.filter.sort.stock_ratio"); // 按库存比例排序

    private final int id;
    private final String key;
    private final String translationKey;

    TableSortMode(int id, String key, String translationKey) {
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

    /** 根据 ID 查找排序模式，未找到时默认返回 NET_ABS */
    public static TableSortMode fromId(int id) {
        for (TableSortMode value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return NET_ABS;
    }

    /** 根据键名查找排序模式（不区分大小写），未找到时默认返回 NET_ABS */
    public static TableSortMode fromKey(String key) {
        if (key == null || key.isBlank()) {
            return NET_ABS;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (TableSortMode value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return NET_ABS;
    }
}
