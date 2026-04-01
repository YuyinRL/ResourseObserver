package com.yuyinrl.resourceobserver.ui.state;

import java.util.Locale;

public enum TableStatusFilter {
    ALL(0, "all", "screen.resourceobserver.overview.filter.status.all"),
    SURPLUS(1, "surplus", "screen.resourceobserver.overview.filter.status.surplus"),
    DEFICIT(2, "deficit", "screen.resourceobserver.overview.filter.status.deficit"),
    CRITICAL(3, "critical", "screen.resourceobserver.overview.filter.status.critical");

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

    public static TableStatusFilter fromId(int id) {
        for (TableStatusFilter value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return ALL;
    }

    public static TableStatusFilter fromKey(String key) {
        if (key == null || key.isBlank()) {
            return ALL;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (TableStatusFilter value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return ALL;
    }
}
