package com.yuyinrl.resourceobserver.service.snapshot.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 负载分段构建器 —— 按设备分类聚合能耗形成柱状图分段。
 *
 * <p>规则：
 * <ul>
 *     <li>聚合 {@code energyPerTick}（负数视为 0）到 {@link PowerSnapshot.LoadCategory}</li>
 *     <li>过滤百分比 &lt; 0.5% 的细碎分段</li>
 *     <li>按百分比降序排列</li>
 * </ul>
 */
public final class PowerLoadSegmenter {

    public static final double MIN_PERCENT_THRESHOLD = 0.5;

    private PowerLoadSegmenter() {
    }

    public static List<PowerSnapshot.LoadSegmentSnapshot> build(List<PowerSnapshot.DeviceSnapshot> devices) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        Map<PowerSnapshot.LoadCategory, Long> totals = new EnumMap<>(PowerSnapshot.LoadCategory.class);
        long grand = 0L;
        for (PowerSnapshot.DeviceSnapshot d : devices) {
            long add = Math.max(0L, d.energyPerTick());
            PowerSnapshot.LoadCategory cat = d.category() == null ? PowerSnapshot.LoadCategory.OTHER : d.category();
            totals.merge(cat, add, Long::sum);
            grand += add;
        }
        if (grand <= 0L) return List.of();

        List<PowerSnapshot.LoadSegmentSnapshot> segments = new ArrayList<>();
        for (Map.Entry<PowerSnapshot.LoadCategory, Long> e : totals.entrySet()) {
            double pct = (e.getValue() * 100.0) / grand;
            if (pct < MIN_PERCENT_THRESHOLD) continue;
            segments.add(new PowerSnapshot.LoadSegmentSnapshot(
                    e.getKey(),
                    PowerCategoryClassifier.translationKey(e.getKey()),
                    pct
            ));
        }
        segments.sort(Comparator.comparingDouble(PowerSnapshot.LoadSegmentSnapshot::percentage).reversed());
        return segments;
    }
}
