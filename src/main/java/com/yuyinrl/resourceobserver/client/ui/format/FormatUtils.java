package com.yuyinrl.resourceobserver.client.ui.format;

import java.util.Locale;

/** 共享格式化工具 —— 统一 ViewModelMapper 间重复的格式化/归一化逻辑 */
public final class FormatUtils {
    private FormatUtils() {}

    /** 紧凑数值格式化 (1.5K, 2.3M, 1.0B) */
    public static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (abs >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (abs >= 1_000L) return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        return Long.toString(value);
    }

    /** 百分比格式化 */
    public static String formatPercent(double ratio) {
        if (ratio < 0.0) return "N/A";
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /** 规范化分组键：空值映射到默认值 */
    public static String normalizeGroupKey(String raw, String defaultKey) {
        return raw == null || raw.isBlank() ? defaultKey : raw;
    }
}
