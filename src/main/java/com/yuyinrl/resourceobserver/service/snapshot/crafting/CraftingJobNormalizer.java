package com.yuyinrl.resourceobserver.service.snapshot.crafting;

/**
 * CPU 任务进度规范化工具。
 *
 * <p>规则：
 * <ul>
 *     <li>{@code total} / {@code remaining} 钳到 ≥ 0</li>
 *     <li>{@code progress} 钳到 [0, 1]</li>
 *     <li>若 progress=0 且 total>0，则用 {@code (total - remaining) / total} 重算</li>
 * </ul>
 */
public final class CraftingJobNormalizer {

    private CraftingJobNormalizer() {
    }

    public record Normalized(long total, long remaining, double progress) {
    }

    public static Normalized normalize(long total, long remaining, double progressFraction) {
        long t = Math.max(0L, total);
        long r = Math.max(0L, remaining);
        double p = Math.max(0.0, Math.min(1.0, progressFraction));
        if (p == 0.0 && t > 0L) {
            long done = Math.max(0L, t - r);
            p = Math.min(1.0, (double) done / (double) t);
        }
        return new Normalized(t, r, p);
    }
}
