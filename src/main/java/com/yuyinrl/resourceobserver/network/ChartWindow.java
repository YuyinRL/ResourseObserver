package com.yuyinrl.resourceobserver.network;

public enum ChartWindow {
    DAY_24H_5M(0, 288, 6000L, "24H/5m", "screen.resourceobserver.overview.chart.window.day"),
    WEEK_7D_30M(1, 336, 36000L, "7D/30m", "screen.resourceobserver.overview.chart.window.week");

    private final int id;
    private final int bucketCount;
    private final long bucketTicks;
    private final String shortLabel;
    private final String translationKey;

    ChartWindow(int id, int bucketCount, long bucketTicks, String shortLabel, String translationKey) {
        this.id = id;
        this.bucketCount = bucketCount;
        this.bucketTicks = bucketTicks;
        this.shortLabel = shortLabel;
        this.translationKey = translationKey;
    }

    public int id() {
        return id;
    }

    public int bucketCount() {
        return bucketCount;
    }

    public long bucketTicks() {
        return bucketTicks;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public String translationKey() {
        return translationKey;
    }

    public static ChartWindow fromId(int id) {
        for (ChartWindow value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return DAY_24H_5M;
    }
}
