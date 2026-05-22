package com.yuyinrl.resourceobserver.client.ui.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FormatUtils 单元测试 —— 验证紧凑数值/百分比/分组键归一化的边界条件。
 */
class FormatUtilsTest {

    @Test
    void formatCompactBelowThousand() {
        assertEquals("0", FormatUtils.formatCompact(0));
        assertEquals("999", FormatUtils.formatCompact(999));
        assertEquals("-999", FormatUtils.formatCompact(-999));
    }

    @Test
    void formatCompactKilo() {
        assertEquals("1.0K", FormatUtils.formatCompact(1_000));
        assertEquals("1.5K", FormatUtils.formatCompact(1_500));
        assertEquals("999.9K", FormatUtils.formatCompact(999_900));
    }

    @Test
    void formatCompactMegaAndGiga() {
        assertEquals("1.0M", FormatUtils.formatCompact(1_000_000));
        assertEquals("2.5M", FormatUtils.formatCompact(2_500_000));
        assertEquals("1.0B", FormatUtils.formatCompact(1_000_000_000));
        assertEquals("3.5B", FormatUtils.formatCompact(3_500_000_000L));
    }

    @Test
    void formatCompactNegativeUsesMagnitude() {
        // 量级阈值用绝对值判断，但实际数值保留符号
        assertEquals("-1.5K", FormatUtils.formatCompact(-1_500));
        assertEquals("-2.0M", FormatUtils.formatCompact(-2_000_000));
    }

    @Test
    void formatPercentReturnsNaForNegative() {
        assertEquals("N/A", FormatUtils.formatPercent(-0.01));
    }

    @Test
    void formatPercentBasic() {
        assertEquals("0.0%", FormatUtils.formatPercent(0.0));
        assertEquals("50.0%", FormatUtils.formatPercent(0.5));
        assertEquals("100.0%", FormatUtils.formatPercent(1.0));
        assertEquals("33.3%", FormatUtils.formatPercent(1.0 / 3.0));
    }

    @Test
    void normalizeGroupKeyHandlesNullAndBlank() {
        assertEquals("default", FormatUtils.normalizeGroupKey(null, "default"));
        assertEquals("default", FormatUtils.normalizeGroupKey("", "default"));
        assertEquals("default", FormatUtils.normalizeGroupKey("   ", "default"));
    }

    @Test
    void normalizeGroupKeyKeepsNonBlank() {
        assertEquals("alpha", FormatUtils.normalizeGroupKey("alpha", "default"));
        assertEquals(" trimme ", FormatUtils.normalizeGroupKey(" trimme ", "default"));
    }
}
