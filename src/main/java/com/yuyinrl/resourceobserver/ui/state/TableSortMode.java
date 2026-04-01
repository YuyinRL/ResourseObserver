package com.yuyinrl.resourceobserver.ui.state;

import java.util.Locale;

public enum TableSortMode {
    NET_ABS(0, "net_abs", "screen.resourceobserver.overview.filter.sort.net_abs"),
    NET(1, "net", "screen.resourceobserver.overview.filter.sort.net"),
    PRODUCTION(2, "production", "screen.resourceobserver.overview.filter.sort.production"),
    CONSUMPTION(3, "consumption", "screen.resourceobserver.overview.filter.sort.consumption"),
    STOCK_RATIO(4, "stock_ratio", "screen.resourceobserver.overview.filter.sort.stock_ratio");

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

    public static TableSortMode fromId(int id) {
        for (TableSortMode value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return NET_ABS;
    }

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
