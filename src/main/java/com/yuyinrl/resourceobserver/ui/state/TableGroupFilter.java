package com.yuyinrl.resourceobserver.ui.state;

import java.util.Locale;

public enum TableGroupFilter {
    ALL(0, "all", "screen.resourceobserver.overview.filter.group.all"),
    RAW(1, "raw", "screen.resourceobserver.overview.filter.group.raw"),
    INTERMEDIATE(2, "intermediate", "screen.resourceobserver.overview.filter.group.intermediate"),
    FINISHED(3, "finished", "screen.resourceobserver.overview.filter.group.finished"),
    COMMON_PARTS(4, "common_parts", "screen.resourceobserver.overview.filter.group.common_parts");

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

    public static TableGroupFilter fromId(int id) {
        for (TableGroupFilter value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return ALL;
    }

    public static TableGroupFilter fromKey(String key) {
        if (key == null || key.isBlank()) {
            return ALL;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (TableGroupFilter value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return ALL;
    }
}
