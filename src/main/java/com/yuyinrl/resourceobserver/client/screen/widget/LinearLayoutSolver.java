package com.yuyinrl.resourceobserver.client.screen.widget;

import java.util.List;

/**
 * 线性布局（Row / Column）的纯数学求解器。
 *
 * <p>把 fixed + flex 槽位的尺寸分配从 {@link Row} / {@link Column} 中抽离，便于
 * 不依赖 Minecraft 类的单元测试。</p>
 *
 * <p>规则：
 * <ul>
 *   <li>固定槽位（{@code weight == 0}）使用 {@code fixedSize}</li>
 *   <li>flex 槽位（{@code weight > 0}）瓜分 {@code totalSize - 总固定 - 总 spacing}</li>
 *   <li>整数除法余数累计到最后一个 flex 槽位，保证总和精确等于剩余空间</li>
 *   <li>无 flex 时多余空间留空（不填补）</li>
 * </ul>
 */
public final class LinearLayoutSolver {

    /** 槽位描述：固定尺寸 + 权重。weight=0 表示固定槽位。 */
    public record Slot(int fixedSize, int weight) {
        public Slot {
            if (fixedSize < 0) fixedSize = 0;
            if (weight < 0) weight = 0;
        }
    }

    private LinearLayoutSolver() {}

    /**
     * 解算每个槽位最终分配到的尺寸。
     *
     * @param slots 槽位列表
     * @param totalSize 容器在主轴方向的总尺寸（像素）
     * @param spacing 槽位之间的间距（像素，>= 0）
     * @return 与 slots 等长的数组，每项为对应槽位最终尺寸（>= 0）
     */
    public static int[] solve(List<Slot> slots, int totalSize, int spacing) {
        int n = slots == null ? 0 : slots.size();
        int[] result = new int[n];
        if (n == 0) return result;
        int s = Math.max(0, spacing);

        int totalFixed = 0;
        int totalWeight = 0;
        int lastFlexIdx = -1;
        for (int i = 0; i < n; i++) {
            Slot slot = slots.get(i);
            totalFixed += slot.fixedSize;
            totalWeight += slot.weight;
            if (slot.weight > 0) lastFlexIdx = i;
        }

        int spacingTotal = s * Math.max(0, n - 1);
        int flexAvail = Math.max(0, totalSize - totalFixed - spacingTotal);

        int allocated = 0;
        for (int i = 0; i < n; i++) {
            Slot slot = slots.get(i);
            if (slot.weight > 0 && totalWeight > 0) {
                if (i == lastFlexIdx) {
                    result[i] = Math.max(0, flexAvail - allocated);
                } else {
                    int w = (int) ((long) flexAvail * slot.weight / totalWeight);
                    result[i] = w;
                    allocated += w;
                }
            } else {
                result[i] = slot.fixedSize;
            }
        }
        return result;
    }
}
