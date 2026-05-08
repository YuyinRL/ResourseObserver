package com.yuyinrl.resourceobserver.world.block.entity;

/**
 * 绑定统计数据 —— 记录某个网络绑定的累计采样结果。
 * 采用不可变 record，每次采样生成新的实例。
 *
 * @param currentValue  当前值（物品总量/能量存储量）
 * @param capacity      容量/种类数
 * @param totalProduced 累计生产量（所有增量之和）
 * @param totalConsumed 累计消耗量（所有减量绝对值之和）
 * @param previousValue 上次采样值，用于计算 delta（-1 表示首次采样）
 */
public record BindingStats(
        long currentValue,
        long capacity,
        long totalProduced,
        long totalConsumed,
        long previousValue
) {
    /**
     * 根据新采样值计算更新后的统计数据（简单差值模式）。
     * 首次采样（previousValue &lt; 0）时不计入生产/消耗，只记录基准值。
     * 正差值计入生产量，负差值绝对值计入消耗量。
     */
    public BindingStats withSample(long newValue, long newCapacity) {
        if (previousValue < 0) {
            return new BindingStats(newValue, newCapacity, 0, 0, newValue);
        }
        long delta = newValue - previousValue;
        long produced = totalProduced;
        long consumed = totalConsumed;
        if (delta > 0) {
            produced += delta;
        } else if (delta < 0) {
            consumed += (-delta);
        }
        return new BindingStats(newValue, newCapacity, produced, consumed, newValue);
    }

    /**
     * 根据已计算好的生产/消耗增量更新统计数据（AE2 逐物品统计模式）。
     * 增量值强制非负，避免负增量污染累计数据。
     */
    public BindingStats withDelta(long newValue, long newCapacity, long producedDelta, long consumedDelta) {
        return new BindingStats(
                newValue,
                newCapacity,
                totalProduced + Math.max(0, producedDelta),
                totalConsumed + Math.max(0, consumedDelta),
                newValue
        );
    }

    /** 创建空的初始统计，previousValue = -1 表示尚未进行首次采样 */
    public static BindingStats empty() {
        return new BindingStats(0, 0, 0, 0, -1);
    }
}
