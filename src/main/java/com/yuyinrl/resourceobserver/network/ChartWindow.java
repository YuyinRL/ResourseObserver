package com.yuyinrl.resourceobserver.network;

/**
 * 图表时间窗口枚举 —— 定义图表显示的时间范围和数据分辨率。
 * <p>
 * 每个窗口由以下参数组成：
 * - bucketCount：总桶数（X 轴数据点数量）
 * - bucketTicks：每个桶对应的游戏 tick 数（分辨率）
 * <p>
 * 例如 DAY_24H_5M：288 个桶 × 6000 tick/桶 = 1,728,000 tick ≈ 24 小时，每桶 5 分钟
 */
public enum ChartWindow {
    /** 24小时窗口，每5分钟一个数据点（288个点） */
    DAY_24H_5M(0, 288, 6000L, "24H/5m", "screen.resourceobserver.overview.chart.window.day"),
    /** 7天窗口，每30分钟一个数据点（336个点） */
    WEEK_7D_30M(1, 336, 36000L, "7D/30m", "screen.resourceobserver.overview.chart.window.week"),
    /** 调试用：10分钟窗口，每5秒一个数据点（120个点） */
    // TODO: Remove or gate this debug-only window once short-range chart tuning is done.
    DEBUG_10M_5S(2, 120, 100L, "10m/5s", "screen.resourceobserver.overview.chart.window.debug");

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

    /** 切换到下一个窗口（循环切换，调试窗口回到日视图） */
    public ChartWindow next() {
        return switch (this) {
            case DAY_24H_5M -> WEEK_7D_30M;
            case WEEK_7D_30M -> DAY_24H_5M;
            case DEBUG_10M_5S -> DAY_24H_5M;
        };
    }

    /** 根据 ID 查找窗口枚举值，未找到时默认返回 DAY_24H_5M */
    public static ChartWindow fromId(int id) {
        for (ChartWindow value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return DAY_24H_5M;
    }
}
