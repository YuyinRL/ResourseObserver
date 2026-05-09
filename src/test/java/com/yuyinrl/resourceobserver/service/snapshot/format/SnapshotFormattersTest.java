package com.yuyinrl.resourceobserver.service.snapshot.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SnapshotFormatters 单元测试 —— 覆盖每个格式器的关键边界。
 */
class SnapshotFormattersTest {

    // --- formatCompact (long) ---

    @Test
    void formatCompact_smallValues_returnsPlainDecimal() {
        assertEquals("0", SnapshotFormatters.formatCompact(0));
        assertEquals("1", SnapshotFormatters.formatCompact(1));
        assertEquals("999", SnapshotFormatters.formatCompact(999));
        assertEquals("-500", SnapshotFormatters.formatCompact(-500));
    }

    @Test
    void formatCompact_thousands_usesK() {
        assertEquals("1.0K", SnapshotFormatters.formatCompact(1000));
        assertEquals("1.5K", SnapshotFormatters.formatCompact(1500));
        assertEquals("999.9K", SnapshotFormatters.formatCompact(999_900));
    }

    @Test
    void formatCompact_millions_usesM() {
        assertEquals("1.0M", SnapshotFormatters.formatCompact(1_000_000));
        assertEquals("2.5M", SnapshotFormatters.formatCompact(2_500_000));
    }

    @Test
    void formatCompact_billions_usesB() {
        assertEquals("1.0B", SnapshotFormatters.formatCompact(1_000_000_000L));
        assertEquals("3.4B", SnapshotFormatters.formatCompact(3_400_000_000L));
    }

    // --- formatExactCount ---

    @Test
    void formatExactCount_addsThousandsSeparator() {
        assertEquals("0", SnapshotFormatters.formatExactCount(0));
        assertEquals("1,234", SnapshotFormatters.formatExactCount(1234));
        assertEquals("1,234,567", SnapshotFormatters.formatExactCount(1_234_567));
        assertEquals("-1,000", SnapshotFormatters.formatExactCount(-1000));
    }

    // --- formatByteLike / formatExactByteLike ---

    @Test
    void formatByteLike_appendsBSuffix() {
        assertEquals("0 B", SnapshotFormatters.formatByteLike(0));
        assertEquals("1.5K B", SnapshotFormatters.formatByteLike(1500));
        assertEquals("1.0M B", SnapshotFormatters.formatByteLike(1_000_000));
    }

    @Test
    void formatExactByteLike_keepsExactCountWithSuffix() {
        assertEquals("0 B", SnapshotFormatters.formatExactByteLike(0));
        assertEquals("1,234 B", SnapshotFormatters.formatExactByteLike(1234));
        assertEquals("1,234,567 B", SnapshotFormatters.formatExactByteLike(1_234_567));
    }

    // --- formatCompactDouble ---

    @Test
    void formatCompactDouble_integerValues_useThousandsFormat() {
        assertEquals("0", SnapshotFormatters.formatCompactDouble(0.0));
        assertEquals("42", SnapshotFormatters.formatCompactDouble(42.0));
        assertEquals("999", SnapshotFormatters.formatCompactDouble(999.0));
    }

    @Test
    void formatCompactDouble_fractional_oneDecimalThenTrim() {
        assertEquals("1.5", SnapshotFormatters.formatCompactDouble(1.5));
        assertEquals("3.7", SnapshotFormatters.formatCompactDouble(3.7));
    }

    @Test
    void formatCompactDouble_largeValues_keepDecimalBeforeUnit() {
        // trimTrailingZeros 只裁剪末尾的 '0'/'.'，K/M/B 后缀挡在前面，
        // 所以 1000.0 → "1.0K"、2_000_000.0 → "2.0M"，与 OverviewViewModelMapper 现有行为一致
        assertEquals("1.0K", SnapshotFormatters.formatCompactDouble(1000.0));
        assertEquals("1.5K", SnapshotFormatters.formatCompactDouble(1500.0));
        assertEquals("2.0M", SnapshotFormatters.formatCompactDouble(2_000_000.0));
        assertEquals("1.0B", SnapshotFormatters.formatCompactDouble(1_000_000_000.0));
    }

    // --- formatExactDouble ---

    @Test
    void formatExactDouble_integerLike_usesThousandsFormat() {
        assertEquals("0", SnapshotFormatters.formatExactDouble(0.0));
        assertEquals("1,234", SnapshotFormatters.formatExactDouble(1234.0));
        assertEquals("1,000,000", SnapshotFormatters.formatExactDouble(1_000_000.0));
    }

    @Test
    void formatExactDouble_fractional_keepsTwoDecimalsThenTrim() {
        assertEquals("1.23", SnapshotFormatters.formatExactDouble(1.23));
        assertEquals("1.2", SnapshotFormatters.formatExactDouble(1.2));
        assertEquals("1,234.56", SnapshotFormatters.formatExactDouble(1234.56));
    }

    // --- trimTrailingZeros ---

    @Test
    void trimTrailingZeros_handlesEdgeCases() {
        assertNull(SnapshotFormatters.trimTrailingZeros(null));
        assertEquals("", SnapshotFormatters.trimTrailingZeros(""));
        assertEquals("123", SnapshotFormatters.trimTrailingZeros("123"));
        assertEquals("1.5", SnapshotFormatters.trimTrailingZeros("1.50"));
        assertEquals("1", SnapshotFormatters.trimTrailingZeros("1.00"));
        assertEquals("1.5", SnapshotFormatters.trimTrailingZeros("1.5"));
        assertEquals("0", SnapshotFormatters.trimTrailingZeros("0.0"));
        assertEquals("100", SnapshotFormatters.trimTrailingZeros("100"));
    }

    @Test
    void trimTrailingZeros_doesNotPenetrateSuffix() {
        // 后缀字符（K/M/B/%/单位）会挡住裁剪，因为只有结尾连续的 '0' 才会被剥离
        assertEquals("1.5K", SnapshotFormatters.trimTrailingZeros("1.5K"));
        assertEquals("2.0M", SnapshotFormatters.trimTrailingZeros("2.0M"));
        assertEquals("1.0%", SnapshotFormatters.trimTrailingZeros("1.0%"));
    }

    // --- formatSignedPercent ---

    @Test
    void formatSignedPercent_includesSign() {
        // % 后缀阻挡尾零裁剪，因此始终保留一位小数（与 mapper 现状一致）
        assertEquals("+5.0%", SnapshotFormatters.formatSignedPercent(5.0));
        assertEquals("-3.5%", SnapshotFormatters.formatSignedPercent(-3.5));
        assertEquals("+0.0%", SnapshotFormatters.formatSignedPercent(0.0));
    }

    @Test
    void formatSignedPercent_nanOrInfinite_returnsNa() {
        assertEquals("N/A", SnapshotFormatters.formatSignedPercent(Double.NaN));
        assertEquals("N/A", SnapshotFormatters.formatSignedPercent(Double.POSITIVE_INFINITY));
        assertEquals("N/A", SnapshotFormatters.formatSignedPercent(Double.NEGATIVE_INFINITY));
    }

    // --- formatSignedPoints ---

    @Test
    void formatSignedPoints_includesSignAndUnit() {
        // " pts" 后缀阻挡尾零裁剪
        assertEquals("+1.2 pts", SnapshotFormatters.formatSignedPoints(1.2));
        assertEquals("-3.4 pts", SnapshotFormatters.formatSignedPoints(-3.4));
        assertEquals("+0.0 pts", SnapshotFormatters.formatSignedPoints(0.0));
    }

    @Test
    void formatSignedPoints_nan_returnsNa() {
        assertEquals("N/A", SnapshotFormatters.formatSignedPoints(Double.NaN));
    }

    // --- formatPercent (F2.A 新增) ---

    @Test
    void formatPercent_basic() {
        assertEquals("0.0%", SnapshotFormatters.formatPercent(0.0));
        assertEquals("50.0%", SnapshotFormatters.formatPercent(0.5));
        assertEquals("100.0%", SnapshotFormatters.formatPercent(1.0));
        assertEquals("12.3%", SnapshotFormatters.formatPercent(0.123));
    }

    @Test
    void formatPercent_negativeOrInvalid_returnsNa() {
        assertEquals("N/A", SnapshotFormatters.formatPercent(-0.1));
        assertEquals("N/A", SnapshotFormatters.formatPercent(Double.NaN));
        assertEquals("N/A", SnapshotFormatters.formatPercent(Double.POSITIVE_INFINITY));
    }

    // --- formatBufferSeconds (F2.A 新增) ---

    @Test
    void formatBufferSeconds_zeroOrNegative_returnsInfinity() {
        assertEquals("∞", SnapshotFormatters.formatBufferSeconds(0));
        assertEquals("∞", SnapshotFormatters.formatBufferSeconds(-1));
    }

    @Test
    void formatBufferSeconds_secondsOnly() {
        assertEquals("30s", SnapshotFormatters.formatBufferSeconds(30));
        assertEquals("59s", SnapshotFormatters.formatBufferSeconds(59));
    }

    @Test
    void formatBufferSeconds_minutesAndSeconds() {
        assertEquals("1m 30s", SnapshotFormatters.formatBufferSeconds(90));
        assertEquals("5m 0s", SnapshotFormatters.formatBufferSeconds(300));
    }

    @Test
    void formatBufferSeconds_hoursMinutesSeconds() {
        assertEquals("1h 1m 40s", SnapshotFormatters.formatBufferSeconds(3700));
    }

    @Test
    void formatBufferSeconds_days() {
        assertEquals("1d 0h 0m 0s", SnapshotFormatters.formatBufferSeconds(86400));
    }

    // --- bufferRatioFromSeconds (F2.A 新增) ---

    @Test
    void bufferRatio_zeroOrInvalid_returnsZero() {
        assertEquals(0.0, SnapshotFormatters.bufferRatioFromSeconds(0), 1e-9);
        assertEquals(0.0, SnapshotFormatters.bufferRatioFromSeconds(-10), 1e-9);
        assertEquals(0.0, SnapshotFormatters.bufferRatioFromSeconds(Double.NaN), 1e-9);
    }

    @Test
    void bufferRatio_dangerZone() {
        assertEquals(0.30, SnapshotFormatters.bufferRatioFromSeconds(30), 1e-9);
        assertEquals(0.15, SnapshotFormatters.bufferRatioFromSeconds(15), 1e-9);
    }

    @Test
    void bufferRatio_warningZone() {
        // 30 ~ 300s → 0.30 ~ 0.70
        assertEquals(0.70, SnapshotFormatters.bufferRatioFromSeconds(300), 1e-9);
        // 165 = 中点 → 0.50
        assertEquals(0.50, SnapshotFormatters.bufferRatioFromSeconds(165), 1e-9);
    }

    @Test
    void bufferRatio_safeZoneClamps() {
        assertEquals(1.0, SnapshotFormatters.bufferRatioFromSeconds(3600), 1e-9);
        assertEquals(1.0, SnapshotFormatters.bufferRatioFromSeconds(99999), 1e-9);
    }

    // --- normalizeGroupKey (F2.A 新增) ---

    @Test
    void normalizeGroupKey_nullOrBlankReturnsDefault() {
        assertEquals("ungrouped", SnapshotFormatters.normalizeGroupKey(null, "ungrouped"));
        assertEquals("ungrouped", SnapshotFormatters.normalizeGroupKey("", "ungrouped"));
        assertEquals("ungrouped", SnapshotFormatters.normalizeGroupKey("   ", "ungrouped"));
    }

    @Test
    void normalizeGroupKey_validReturnsAsIs() {
        assertEquals("raw", SnapshotFormatters.normalizeGroupKey("raw", "ungrouped"));
        assertEquals("custom", SnapshotFormatters.normalizeGroupKey("custom", "ungrouped"));
    }
}
