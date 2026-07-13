package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserOptimizationTest {
    @Test
    void rejectsInvalidArrayModelsBeforeSourceGeneration() {
        RuntimeException bitLength = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(InvalidBitLengthBean.class));
        assertTrue(hasMessage(bitLength, "singleLen"));

        RuntimeException fieldType = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(InvalidArrayFieldBean.class));
        assertTrue(hasMessage(fieldType, "requires an array field"));
    }

    @Test
    void rejectsClassLengthShorterThanItsFields() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(UndersizedFixedBean.class));
        assertTrue(hasMessage(exception, "smaller than model length"));
    }

    private static boolean hasMessage(Throwable throwable, String expected) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    public static class InvalidBitLengthBean {
        @F_bit_num_array(len = 1, singleLen = 0)
        public int[] values;
    }

    public static class InvalidArrayFieldBean {
        @F_num_array(len = 1, singleType = NumType.uint8)
        public int value;
    }

    @C_skip(len = 1)
    public static class UndersizedFixedBean {
        @F_num(type = NumType.uint8)
        public int first;

        @F_num(type = NumType.uint8)
        public int second;
    }

}
