package com.yuyinrl.resourceobserver.client.screen.widget;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线性布局求解器纯逻辑单测。
 *
 * <p>不依赖 Minecraft 类，仅测试 {@link LinearLayoutSolver#solve} 的数学行为。
 * Row/Column widget 内部直接调用本求解器，因此覆盖了它们的核心算法。</p>
 */
class LinearLayoutSolverTest {

    private static LinearLayoutSolver.Slot fixed(int size) {
        return new LinearLayoutSolver.Slot(size, 0);
    }

    private static LinearLayoutSolver.Slot flex(int weight) {
        return new LinearLayoutSolver.Slot(0, weight);
    }

    @Test
    void rectShrinkClampsToZero() {
        Rect r = Rect.of(0, 0, 4, 4);
        Rect s = r.shrink(10);
        assertEquals(0, s.width());
        assertEquals(0, s.height());
    }

    @Test
    void rectContainsRespectsRightExclusive() {
        Rect r = Rect.of(10, 20, 30, 40);
        assertTrue(r.contains(10, 20));
        assertTrue(r.contains(39.9, 59.9));
        assertFalse(r.contains(40, 60));
        assertFalse(r.contains(9, 20));
    }

    @Test
    void rectTranslatePreservesSize() {
        Rect r = Rect.of(5, 5, 20, 30).translate(10, -2);
        assertEquals(15, r.x());
        assertEquals(3, r.y());
        assertEquals(20, r.width());
        assertEquals(30, r.height());
    }

    @Test
    void allocatesFixedAndFlexExactly() {
        int[] sizes = LinearLayoutSolver.solve(
                List.of(fixed(30), flex(1), flex(2)), 120, 0);
        // 剩余 90，分给 weight 1+2=3：30 / 60
        assertArrayEquals(new int[]{30, 30, 60}, sizes);
        assertEquals(120, sum(sizes));
    }

    @Test
    void accountsForSpacing() {
        int[] sizes = LinearLayoutSolver.solve(
                List.of(flex(1), flex(1)), 100, 4);
        // spacing=4，flex 总可用 96，各 48
        assertArrayEquals(new int[]{48, 48}, sizes);
    }

    @Test
    void flexZeroWhenAllFixedExceedsBounds() {
        int[] sizes = LinearLayoutSolver.solve(
                List.of(fixed(80), fixed(80)), 100, 0);
        // 没有 flex，溢出（已知行为）
        assertArrayEquals(new int[]{80, 80}, sizes);
    }

    @Test
    void singleFlexAbsorbsAllRemaining() {
        int[] sizes = LinearLayoutSolver.solve(
                List.of(flex(1)), 77, 0);
        assertArrayEquals(new int[]{77}, sizes);
    }

    @Test
    void allFixedNoFlexLeavesGap() {
        int[] sizes = LinearLayoutSolver.solve(
                List.of(fixed(10), fixed(10)), 100, 0);
        // 仅占 20，剩余 80 不被任何 child 接管
        assertArrayEquals(new int[]{10, 10}, sizes);
        assertEquals(20, sum(sizes));
    }

    @Test
    void roundingRemainderGoesToLastFlex() {
        // 100 像素 - 20 fixed - 0 spacing = 80 flex；3 个 weight 1 槽位 -> floor(26.67)=26 + 26 + 28
        int[] sizes = LinearLayoutSolver.solve(
                List.of(fixed(20), flex(1), flex(1), flex(1)), 100, 0);
        assertEquals(100, sum(sizes));
        assertEquals(20, sizes[0]);
        assertEquals(26, sizes[1]);
        assertEquals(26, sizes[2]);
        assertEquals(28, sizes[3]); // 最后 flex 拿余数
    }

    @Test
    void emptySlotsReturnsEmptyArray() {
        int[] sizes = LinearLayoutSolver.solve(List.of(), 100, 4);
        assertEquals(0, sizes.length);
    }

    @Test
    void negativeWeightCoercedToZero() {
        var slot = new LinearLayoutSolver.Slot(10, -5);
        assertEquals(0, slot.weight());
    }

    @Test
    void zeroTotalSizeStillAllocatesFixed() {
        // 容器为 0 像素时，fixed 仍按声明值返回（溢出）；flex 拿到 0
        int[] sizes = LinearLayoutSolver.solve(
                List.of(fixed(20), flex(1)), 0, 0);
        assertArrayEquals(new int[]{20, 0}, sizes);
    }

    private static int sum(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
}
