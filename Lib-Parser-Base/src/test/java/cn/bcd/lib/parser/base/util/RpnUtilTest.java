package cn.bcd.lib.parser.base.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RpnUtilTest {
    @Test
    void whitespaceDoesNotChangeSubtractionOrItsInverse() {
        for (String expression : new String[]{"(x+1)-2", "(x+1) - 2", "(x+1)\t-\n2"}) {
            assertEquals(9, RpnUtil.calc(expression, Map.of("x", 10d)));
            assertEquals(10, RpnUtil.calc(RpnUtil.reverseExpr(expression), Map.of("x", 9d)));
        }
    }

    @Test
    void preservesUnaryMinusWithWhitespace() {
        for (String expression : new String[]{"- x", "2 * - x", "- (x + 1)"}) {
            double value = RpnUtil.calc(expression, Map.of("x", 10d));
            assertEquals(10, RpnUtil.calc(RpnUtil.reverseExpr(expression), Map.of("x", value)));
        }
    }

    @Test
    void rejectsInversionWithMultipleVariableOccurrences() {
        for (String expression : new String[]{"x+x", "x*x", "x-x", "x/x", "x+(x+1)"}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> RpnUtil.reverseExpr(expression));
            assertTrue(exception.getMessage().contains("single variable occurrence"));
        }
    }
}
