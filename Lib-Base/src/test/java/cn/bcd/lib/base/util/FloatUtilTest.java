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
    void roundUsesHalfUpForPositiveAndNegativeValues() {
        assertEquals(2L, FloatUtil.round(1.5));
        assertEquals(-2L, FloatUtil.round(-1.5));
        assertEquals(2, FloatUtil.round(1.5f));
        assertEquals(-2, FloatUtil.round(-1.5f));
    }

    @Test
    void roundUsesDecimalHalfUpRounding() {
        assertEquals(1.24, FloatUtil.round(1.235, 2));
        assertEquals(-1.24, FloatUtil.round(-1.235, 2));
        assertEquals(1.01, FloatUtil.round(1.005, 2));
        assertEquals(-1.01, FloatUtil.round(-1.005, 2));
        assertEquals(0.29, FloatUtil.round(0.29, 2));
        assertEquals(2.0, FloatUtil.round(1.5, 0));
    }

}
