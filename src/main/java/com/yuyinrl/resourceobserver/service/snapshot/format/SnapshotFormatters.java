package com.yuyinrl.resourceobserver.service.snapshot.format;

import java.util.Locale;

/**
 * Snapshot 数值格式化工具集 —— 服务端塑形产物的纯逻辑格式器。
 *
 * <p>F1.A.2 从 {@code OverviewViewModelMapper} 迁出来的 8 个私有方法集中至此，
 * 作为后续 {@code OverviewSnapshotBuilder} 与三方消费者的共享工具。
 *
 * <p>设计约束：
 * <ul>
 *     <li>纯函数、无副作用、无 MC / NeoForge 依赖（保证可单测）</li>
 *     <li>统一 {@link Locale#ROOT}：避免本地化导致小数点 / 千分位差异</li>
 *     <li>NaN / Infinite 输入返回固定占位（"N/A"）；调用方负责 i18n 替换</li>
 * </ul>
 */
public final class SnapshotFormatters {
    /** NaN / Infinite 时的回退占位 */
    public static final String NA_PLACEHOLDER = "N/A";

    private SnapshotFormatters() {
    }

    /**
     * 紧凑整数格式：1500 → "1.5K"，1234567 → "1.2M"，2000000000 → "2.0B"。
     * 小于 1000 直接返回十进制字符串。
     */
    public static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    /** 带千分位逗号的精确整数：1234567 → "1,234,567" */
    public static String formatExactCount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** 紧凑字节量：1234 → "1.2K B" */
    public static String formatByteLike(long value) {
        return formatCompact(value) + " B";
    }

    /** 精确字节量：1234567 → "1,234,567 B" */
    public static String formatExactByteLike(long value) {
        return formatExactCount(value) + " B";
    }

    /**
     * 紧凑浮点格式：处理整数化、千分位、单位后缀（K/M/B）、尾零裁剪。
     * <ul>
     *     <li>{@code |v| >= 1e9} → "1.5B"</li>
     *     <li>{@code |v| >= 1e6} → "1.5M"</li>
     *     <li>{@code |v| >= 1e3} → "1.5K"</li>
     *     <li>近似整数 → "%,d" 千分位整数</li>
     *     <li>否则 → "%,.1f" 一位小数 + 尾零裁剪</li>
     * </ul>
     */
    public static String formatCompactDouble(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0d));
        }
        if (abs >= 1_000_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0d));
        }
        if (abs >= 1_000.0d) {
            return trimTrailingZeros(String.format(Locale.ROOT, "%.1fK", value / 1_000.0d));
        }
        if (Math.abs(value - Math.rint(value)) < 1.0E-6d) {
            return String.format(Locale.ROOT, "%,d", (long) Math.rint(value));
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%,.1f", value));
    }

    /** 精确浮点：近似整数走整数格式，否则保留两位小数并裁剪尾零 */
    public static String formatExactDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6d) {
            return String.format(Locale.ROOT, "%,d", (long) Math.rint(value));
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%,.2f", value));
    }

    /**
     * 裁剪小数尾零：
     * <ul>
     *     <li>{@code "1.50"} → {@code "1.5"}</li>
     *     <li>{@code "1.00"} → {@code "1"}</li>
     *     <li>{@code "0.0"} → {@code "0"}</li>
     *     <li>无小数点的字符串原样返回</li>
     * </ul>
     */
    public static String trimTrailingZeros(String value) {
        if (value == null || value.isEmpty() || !value.contains(".")) {
            return value;
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return end <= 0 ? "0" : value.substring(0, end);
    }

    /** 带符号百分比：+5.0% / -3.5% / NaN → "N/A" */
    public static String formatSignedPercent(double value) {
        if (!Double.isFinite(value)) {
            return NA_PLACEHOLDER;
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%+.1f%%", value));
    }

    /** 带符号分值：+1.2 pts / -2.5 pts / NaN → "N/A" */
    public static String formatSignedPoints(double value) {
        if (!Double.isFinite(value)) {
            return NA_PLACEHOLDER;
        }
        return trimTrailingZeros(String.format(Locale.ROOT, "%+.1f pts", value));
    }
}
