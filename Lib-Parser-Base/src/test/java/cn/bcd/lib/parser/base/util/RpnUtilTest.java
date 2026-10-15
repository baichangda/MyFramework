package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.base.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class RpnUtilTest {

    @Test
    void rejectsInversionWithMultipleVariableOccurrences() {
        for (String expression : new String[]{"x+x", "x*x", "x-x", "x/x", "x+(x+1)"}) {
            BaseException exception = assertThrows(BaseException.class,
                    () -> RpnUtil.reverseExpr(expression));
            assertTrue(exception.getMessage().contains("single variable occurrence"));
        }
    }

    @Test
    void convertsOperatorsWithPrecedenceAndLeftAssociativity() {
        assertRpn("a+b*c-d/2", "a b c * + d 2 / -");
        assertRpn("(a+b)*(c-d)/2", "a b + c d - * 2 /");
        assertRpn("a-b-c", "a b - c -");
        assertRpn("a/b/c", "a b / c /");
        assertRpn("a/b*c", "a b / c *");
        assertRpn("((a))", "a");
    }

    @Test
    void distinguishesUnaryNegationFromSubtraction() {
        assertRpn("-x", "x !");
        assertRpn("--x", "x");
        assertRpn("---x", "x !");
        assertRpn("x--2", "x 2 ! -");
        assertRpn("x/-2", "x 2 ! /");
        assertRpn("-(x+2)*3", "x 2 + ! 3 *");
        assertRpn("-(-x)", "x");
        assertRpn("-a*-b", "a ! b ! *");
        assertRpn("2*-x-3", "2 x ! * 3 -");
    }

    @Test
    void supportsWhitespaceAndNamedVariables() {
        assertRpn(" \t(sensor_1 + .5)\n / -2 ", "sensor_1 .5 + 2 ! /");
        assertEquals("(x-2)", RpnUtil.reverseExpr("sensor_1+2"));
    }

    @Test
    void retainsNumericLiteralTextAndValue() {
        RpnUtil.Token[] tokens = RpnUtil.toRpn("x*0.000001+2.00");
        assertFalse(tokens[0].isNumber());
        assertNull(tokens[0].number());
        assertTrue(tokens[1].isNumber());
        assertEquals("0.000001", tokens[1].text());
        assertEquals(0.000001, tokens[1].number());
        assertEquals("2.00", tokens[3].text());
        assertEquals("((x-2.00)/0.000001)", RpnUtil.reverseExpr("x*0.000001+2.00"));
    }

    @Test
    void rejectsNullAndEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.toRpn(null));
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.toRpn(""));
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.reverseExpr(null));
        assertEquals("", RpnUtil.reverseExpr(""));
    }

    @Test
    void rejectsMalformedExpressionsThroughBothEntrypoints() {
        for (String expression : new String[]{" ", "x+", "*x", "()", "x)", "(x", "x(2)",
                "(x)2", "(x)(2)", "x 2", "1 2", "x+*2", "x//2", "+x", "x++2", "-",
                "(x+)", "x+()", "x!", "2x", "1.2.3", "x^2", "x+NaN", "x+Infinity"}) {
            assertThrows(BaseException.class, () -> RpnUtil.toRpn(expression), expression);
            assertThrows(BaseException.class, () -> RpnUtil.reverseExpr(expression), expression);
        }
    }

    @Test
    void syntaxErrorsIdentifyExpressionAndPosition() {
        BaseException exception = assertThrows(BaseException.class, () -> RpnUtil.toRpn("x+"));
        assertTrue(exception.getMessage().contains("index[2]"));
        assertTrue(exception.getMessage().contains("x+"));
    }

    @Test
    void invertsEveryBinaryOperatorOnEitherSide() {
        String[][] cases = {
                {"x+2", "(x-2)"}, {"2+x", "(x-2)"},
                {"x-2", "(x+2)"}, {"2-x", "(2-x)"},
                {"x*2", "(x/2)"}, {"2*x", "(x/2)"},
                {"x/2", "(x*2)"}, {"2/x", "(2/x)"},
                {"-x", "(-x)"}, {"--x", "x"},
                {"x+0", "(x-0)"}, {"0-x", "(0-x)"},
                {"x*(2*3)", "(x/(2*3))"}, {"2/(3/x)", "(3/(2/x))"},
                {"-(x+3)/2", "((-(x*2))-3)"}
        };
        for (String[] testCase : cases) {
            assertEquals(testCase[1], RpnUtil.reverseExpr(testCase[0]), testCase[0]);
        }
    }

    @Test
    void rejectsNonInvertibleAndNonFiniteConstantSubtrees() {
        for (String expression : new String[]{"3", "2+3", "x*0", "0*x", "0/x", "x/0",
                "x*(2-2)", "x/(2-2)", "(2-2)/x", "x*(-0)", "x+(1/0)",
                "x+1e309", "x+(1e308*1e308)"}) {
            assertThrows(BaseException.class, () -> RpnUtil.reverseExpr(expression), expression);
        }
    }

    @Test
    void inverseRecoversOriginalValueForNestedOperations() {
        for (String expression : new String[]{"x*10+3", "2/(3/x)", "-(x+3)/2", "2/(x*4-3)",
                "(-1+3)*(-10.4-(-x))/2-1.5*(2-1.3)", "x/-2", "2-x/3", "x*(2+3-1)/4"}) {
            String inverse = RpnUtil.reverseExpr(expression);
            for (double original : new double[]{-12.5, -2, 0.5, 3, 20}) {
                double result = evaluate(expression, original);
                assertEquals(original, evaluate(inverse, result), 1e-9, expression);
            }
        }
    }

    private static void assertRpn(String expression, String expected) {
        assertArrayEquals(expected.split(" "),
                Arrays.stream(RpnUtil.toRpn(expression)).map(RpnUtil.Token::text).toArray(String[]::new),
                expression);
    }

    /** 用 double 解释执行生成的表达式，验证求逆的数值语义。 */
    private static double evaluate(String expression, double variable) {
        Deque<Double> stack = new ArrayDeque<>();
        for (RpnUtil.Token token : RpnUtil.toRpn(expression)) {
            if (token.isNumber()) {
                stack.push(token.number());
            } else if (token.text().equals("!")) {
                stack.push(-stack.pop());
            } else if (token.text().equals("x")) {
                stack.push(variable);
            } else {
                double right = stack.pop();
                double left = stack.pop();
                stack.push(switch (token.text()) {
                    case "+" -> left + right;
                    case "-" -> left - right;
                    case "*" -> left * right;
                    case "/" -> left / right;
                    default -> throw new AssertionError(token.text());
                });
            }
        }
        assertEquals(1, stack.size());
        return stack.pop();
    }
}
