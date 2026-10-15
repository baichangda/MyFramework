package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ReverseExprTest {
    @Test
    void solvesScaleAndOffsetByMovingEachOperation() {
        assertEquals("((x-3)/10)", RpnUtil.reverseExpr("x*10+3"));
        assertEquals("x", RpnUtil.reverseExpr("x"));
        assertEquals("", RpnUtil.reverseExpr(""));
    }


    @Test
    void preservesDecimalLiteralText() {
        assertEquals("(x/0.000001)", RpnUtil.reverseExpr("x*0.000001"));
    }

    @Test
    void rejectsExpressionsWithoutAnInverse() {
        for (String expression : new String[]{"3", "2+3", "x*0", "0*x", "0/x",
                "x/0", "x*(2-2)", "x/(2-2)", "x+(1/0)", "x+x", "x*x"}) {
            assertThrows(RuntimeException.class, () -> RpnUtil.reverseExpr(expression), expression);
        }
    }

    @Test
    void rejectsMissingOperandsAndUnbalancedParentheses() {
        for (String expression : new String[]{" ", "x+", "*x", "()", "x)", "(x", "x(2)"}) {
            assertThrows(RuntimeException.class, () -> RpnUtil.reverseExpr(expression), expression);
        }
        assertThrows(IllegalArgumentException.class, () -> RpnUtil.reverseExpr(null));
    }

    @Test
    void generatedProcessorsUseGroupedInverseExpressions() {
        var productProcessor = Parser.getProcessor(ProductBean.class);
        ProductBean product = ParserTestSupport.process(productProcessor, (byte) 10);
        assertEquals(60, product.value);
        assertArrayEquals(new byte[]{10}, ParserTestSupport.deProcess(productProcessor, product));

        var reciprocalProcessor = Parser.getProcessor(ReciprocalBean.class);
        ReciprocalBean reciprocal = ParserTestSupport.process(reciprocalProcessor, (byte) 12);
        assertEquals(8, reciprocal.value);
        assertArrayEquals(new byte[]{12}, ParserTestSupport.deProcess(reciprocalProcessor, reciprocal));
    }

    public static class ProductBean {
        @F_num(type = NumType.uint8, valExpr = "x*(2*3)")
        public double value;
    }

    public static class ReciprocalBean {
        @F_num(type = NumType.uint8, valExpr = "2/(3/x)")
        public double value;
    }
}
