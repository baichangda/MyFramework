package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.BitRemainingMode;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParserBugFixTest {
    @Test
    void roundTripsExpressionWithWhitespaceAfterParentheses() {
        var processor = Parser.getProcessor(SpacedExpression.class);
        SpacedExpression bean = ParserTestSupport.process(processor, (byte) 10);
        assertEquals(9, bean.value);
        assertArrayEquals(new byte[]{10}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    void rejectsRepeatedVariableBeforeGeneratingProcessor() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(RepeatedVariable.class));
        assertTrue(hasMessage(exception, "single variable occurrence"));
        assertTrue(hasMessage(exception, "valExpr"));
        exception = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(RepeatedArrayVariable.class));
        assertTrue(hasMessage(exception, "single variable occurrence"));
        assertTrue(hasMessage(exception, "singleValExpr"));
    }

    @Test
    void countsAlignedNotIgnoreBitFieldsAndAvoidsExtraPadding() {
        assertEquals(1, ParseUtil.getClassByteLenIfPossible(AlignedBits.class));
        var processor = Parser.getProcessor(AlignedBits.class);
        AlignedBits bean = new AlignedBits();
        bean.value = 7;
        assertArrayEquals(new byte[]{7}, ParserTestSupport.deProcess(processor, bean));
        assertEquals(7, ParserTestSupport.process(processor, (byte) 7).value);
    }

    @Test
    void countsAlignedNotIgnoreBitArraysAndAvoidsExtraPadding() {
        assertEquals(1, ParseUtil.getClassByteLenIfPossible(AlignedBitArray.class));
        var processor = Parser.getProcessor(AlignedBitArray.class);
        AlignedBitArray bean = new AlignedBitArray();
        bean.values = new int[]{3, 7};
        assertArrayEquals(new byte[]{0x37}, ParserTestSupport.deProcess(processor, bean));
        assertArrayEquals(bean.values, ParserTestSupport.process(processor, (byte) 0x37).values);
    }

    @Test
    void partialNotIgnoreBitsHaveUnknownStandaloneLength() {
        assertEquals(-1, ParseUtil.getClassByteLenIfPossible(PartialBits.class));
    }

    @Test
    void classLengthExpressionOnlyPadsPositiveDifference() {
        var processor = Parser.getProcessor(ExpressionClassLength.class);
        for (int length : new int[]{1, 2, 4}) {
            ExpressionClassLength bean = new ExpressionClassLength();
            bean.length = length;
            bean.value = 7;
            byte[] expected = length == 4 ? new byte[]{4, 7, 0, 0} : new byte[]{(byte) length, 7};
            assertArrayEquals(expected, ParserTestSupport.deProcess(processor, bean));
            ByteBuf buf = Unpooled.wrappedBuffer(expected);
            try {
                assertEquals(7, processor.process(buf).value);
                assertEquals(expected.length, buf.readerIndex());
            } finally {
                buf.release();
            }
        }
    }

    private static boolean hasMessage(Throwable exception, String message) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
        }
        return false;
    }

    public static class SpacedExpression {
        @F_num(type = NumType.uint8, valExpr = "(x+1) - 2")
        public int value;
    }

    public static class RepeatedVariable {
        @F_num(type = NumType.uint8, valExpr = "x+x")
        public int value;
    }

    public static class RepeatedArrayVariable {
        @F_num_array(singleType = NumType.uint8, len = 1, singleValExpr = "x*x")
        public int[] values;
    }

    @C_skip(len = 1)
    public static class AlignedBits {
        @F_bit_num(len = 8, bitRemainingMode = BitRemainingMode.not_ignore)
        public int value;
    }

    @C_skip(len = 1)
    public static class AlignedBitArray {
        @F_bit_num_array(len = 2, singleLen = 4, bitRemainingMode = BitRemainingMode.not_ignore)
        public int[] values;
    }

    public static class PartialBits {
        @F_bit_num(len = 3, bitRemainingMode = BitRemainingMode.not_ignore)
        public int value;
    }

    @C_skip(lenExpr = "a")
    public static class ExpressionClassLength {
        @F_num(type = NumType.uint8, var = 'a')
        public int length;
        @F_num(type = NumType.uint8)
        public int value;
    }
}
