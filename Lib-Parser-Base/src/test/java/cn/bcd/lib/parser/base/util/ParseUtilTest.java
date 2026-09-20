package cn.bcd.lib.parser.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParseUtilTest {
    @Test
    void replacesOnlyTheSupportedValueVariable() {
        assertEquals("(rawValue+1)/2.5", ParseUtil.replaceValExprToCode(" (x + 1) / 2.5 ", "rawValue"));
        assertThrows(RuntimeException.class, () -> ParseUtil.replaceValExprToCode("y + 1", "rawValue"));
        assertThrows(RuntimeException.class, () -> ParseUtil.replaceValExprToCode("Math.abs(x)", "rawValue"));
    }

    @Test
    void usesFloatUtilRoundingBehavior() {
        assertEquals(-2L, ParseUtil.round(-1.5d));
        assertEquals(-2, ParseUtil.round(-1.5f));
        assertEquals(1.01d, ParseUtil.round(1.005d, 2));
        assertThrows(IllegalArgumentException.class, () -> ParseUtil.round(1.0d, 11));
    }
}
