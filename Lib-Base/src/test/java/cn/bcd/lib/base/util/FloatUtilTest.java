package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatUtilTest {

    @Test
    void comparisonUsesMutuallyExclusiveToleranceRange() {
        assertTrue(FloatUtil.eq(1.005, 1.0, 2));
        assertFalse(FloatUtil.gt(1.005, 1.0, 2));
        assertFalse(FloatUtil.lt(1.005, 1.0, 2));

        assertTrue(FloatUtil.gt(1.02, 1.0, 2));
        assertTrue(FloatUtil.lt(1.0, 1.02, 2));
    }

    @Test
    void comparisonHandlesNonFiniteValues() {
        assertTrue(FloatUtil.eq(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 2));
        assertTrue(FloatUtil.gt(Double.POSITIVE_INFINITY, 1.0, 2));
        assertTrue(FloatUtil.lt(Double.NEGATIVE_INFINITY, 1.0, 2));

        assertFalse(FloatUtil.eq(Double.NaN, Double.NaN, 2));
        assertFalse(FloatUtil.gt(Double.NaN, 1.0, 2));
        assertFalse(FloatUtil.lt(Double.NaN, 1.0, 2));
    }

    @Test
    void roundUsesHalfUpForPositiveAndNegativeValues() {
        assertEquals(2L, FloatUtil.round(1.5));
        assertEquals(-2L, FloatUtil.round(-1.5));
        assertEquals(2, FloatUtil.round(1.5f));
        assertEquals(-2, FloatUtil.round(-1.5f));
    }

    @Test
    void roundHandlesNonFiniteValues() {
        assertEquals(0L, FloatUtil.round(Double.NaN));
        assertEquals(Long.MAX_VALUE, FloatUtil.round(Double.POSITIVE_INFINITY));
        assertEquals(Long.MIN_VALUE, FloatUtil.round(Double.NEGATIVE_INFINITY));
        assertEquals(Integer.MAX_VALUE, FloatUtil.round(Float.POSITIVE_INFINITY));
        assertEquals(Integer.MIN_VALUE, FloatUtil.round(Float.NEGATIVE_INFINITY));
    }

    @Test
    void formatUsesDecimalHalfUpRounding() {
        assertEquals(1.24, FloatUtil.format(1.235, 2));
        assertEquals(-1.24, FloatUtil.format(-1.235, 2));
        assertEquals(1.01, FloatUtil.format(1.005, 2));
        assertEquals(-1.01, FloatUtil.format(-1.005, 2));
        assertEquals(0.29, FloatUtil.format(0.29, 2));
        assertEquals(2.0, FloatUtil.format(1.5, 0));
    }

    @Test
    void rejectsUnsupportedScale() {
        assertThrows(IllegalArgumentException.class, () -> FloatUtil.eq(1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> FloatUtil.format(1, 11));
    }
}
