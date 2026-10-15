package cn.bcd.lib.parser.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpnUtilTest {

    @Test
    void rejectsInversionWithMultipleVariableOccurrences() {
        for (String expression : new String[]{"x+x", "x*x", "x-x", "x/x", "x+(x+1)"}) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> RpnUtil.reverseExpr(expression));
            assertTrue(exception.getMessage().contains("single variable occurrence"));
        }
    }
}
