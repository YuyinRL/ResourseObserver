package com.yuyinrl.resourceobserver.service.snapshot.power;

/**
 * 网络余量计算器 —— 计算 {@code OverloadInfoSnapshot} 的 headroomPercent / reservePerTick。
 */
public final class PowerOverloadInfoCalculator {

    private PowerOverloadInfoCalculator() {
    }

    public static PowerSnapshot.OverloadInfoSnapshot compute(long totalInputPerTick, long totalOutputPerTick) {
        double headroomPercent = totalInputPerTick > 0
                ? Math.max(0.0, (totalInputPerTick - totalOutputPerTick) * 100.0 / totalInputPerTick)
                : 0.0;
        long reservePerTick = Math.max(0L, totalInputPerTick - totalOutputPerTick);
        return new PowerSnapshot.OverloadInfoSnapshot(headroomPercent, reservePerTick);
    }
}
