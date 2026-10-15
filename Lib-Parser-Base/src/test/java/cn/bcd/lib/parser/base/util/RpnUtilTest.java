package cn.bcd.lib.parser.base.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RpnUtilTest {
    @Test
    void preservesEvaluationOrderWhenReconstructingExpressions() {
        Map<String, Double> variables = Map.of("x", 8d, "y", 4d, "z", 2d);
        for (String expression : new String[]{
                "x/(y/z)", "x/(y*z)", "x*(y/z)", "x-(y-z)",
                "x+(y-z)", "-(x/(y*z))", "(x+1)*(y-2)/z",
                "10000000000000000*(1/10000000000000000)"}) {
            String reconstructed = RpnUtil.toExpr(RpnUtil.toRpn(expression));
            assertEquals(RpnUtil.calc(expression, variables), RpnUtil.calc(reconstructed, variables), expression);
        }
        assertEquals("(8/(4/2))", RpnUtil.toExpr(RpnUtil.toRpn("8/(4/2)")));
        assertEquals("(8/(4*2))", RpnUtil.toExpr(RpnUtil.toRpn("8/(4*2)")));
    }

    @Test
    void preservesOriginalNumericText() {
        for (String number : new String[]{"0.0000001", "9007199254740993", "1.2300", "2147483648"}) {
            assertEquals(number, RpnUtil.toExpr(RpnUtil.toRpn(number)));
        }
        String expression = "0.0000001*x+9007199254740993";
        String reconstructed = RpnUtil.toExpr(RpnUtil.toRpn(expression));
        assertEquals(RpnUtil.calc(expression, Map.of("x", 2d)),
                RpnUtil.calc(reconstructed, Map.of("x", 2d)));
    }

    @Test
    void rejectsMalformedRpnWhenReconstructingExpressions() {
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.toExpr(null));
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.toExpr(new RpnUtil.Ele[0]));
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.toExpr(new RpnUtil.Ele[]{null}));
        assertThrows(IllegalArgumentException.class,
                () -> RpnUtil.toExpr(new RpnUtil.Ele[]{new RpnUtil.Ele("!")}));
        for (String operator : new String[]{"+", "-", "*", "/"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RpnUtil.toExpr(new RpnUtil.Ele[]{new RpnUtil.Ele(operator)}));
            assertThrows(IllegalArgumentException.class,
                    () -> RpnUtil.toExpr(new RpnUtil.Ele[]{new RpnUtil.Ele("x"), new RpnUtil.Ele(operator)}));
        }
        assertThrows(IllegalArgumentException.class,
                () -> RpnUtil.toExpr(new RpnUtil.Ele[]{new RpnUtil.Ele("x"), new RpnUtil.Ele("y")}));
    }

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
