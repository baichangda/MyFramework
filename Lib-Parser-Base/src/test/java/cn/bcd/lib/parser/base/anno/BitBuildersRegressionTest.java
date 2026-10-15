package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BitBuildersRegressionTest {
    @Test
    public void easyUsesCorrectByteWidthsAtEveryBoundary() {
        var processor = Parser.getProcessor(EasyBoundaries.class);
        byte[] bytes = {1, (byte) 0xAB, 1, 0x23, (byte) 0xAB, (byte) 0xCD,
                1, 0x23, 0x45, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 1, 0x23, 0x45, 0x67};
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        EasyBoundaries bean = processor.process(input);
        assertEquals(1, bean.one);
        assertEquals(0xAB, bean.eight);
        assertEquals(0x123, bean.nine);
        assertEquals(0xABCD, bean.sixteen);
        assertEquals(0x12345, bean.seventeen);
        assertEquals(0xABCDEF, bean.twentyFour);
        assertEquals(0x1234567, bean.twentyFive);
        assertEquals(0, input.readableBytes());
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void easyFullWidthPreservesSignedBitPatternsAndUnsignedLongs() {
        var processor = Parser.getProcessor(EasyFull.class);
        for (int bits : new int[]{0, 0x12345678, Integer.MIN_VALUE, -1}) {
            ByteBuf input = Unpooled.buffer().writeInt(bits).writeInt(bits);
            byte[] expected = new byte[8];
            input.getBytes(0, expected);
            EasyFull bean = processor.process(input);
            assertEquals(bits, bean.signed);
            assertEquals(Integer.toUnsignedLong(bits), bean.unsigned);
            assertArrayEquals(expected, ParserTestSupport.deProcess(processor, bean));
        }
    }

    @Test
    public void easyStartsNewGroupsAfterEndAndAfterOtherAnnotations() {
        var processor = Parser.getProcessor(EasyGroups.class);
        byte[] bytes = {0x12, 0x34, 0x56, 0x78};
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        EasyGroups bean = processor.process(input);
        assertEquals(0x12, bean.first);
        assertEquals(0x34, bean.second);
        assertEquals(0x56, bean.middle);
        assertEquals(0x78, bean.last);
        assertEquals(0, input.readableBytes());
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void easySkippedFieldsStillDefineGroupWidthAndFinishGroups() {
        var processor = Parser.getProcessor(EasySkipped.class);
        EasySkipped bean = ParserTestSupport.process(processor, (byte) 1, (byte) 0xA0, (byte) 0xFF);
        assertEquals(10, bean.value);
        assertEquals(0, bean.high);
        assertEquals(0, bean.low);
        assertArrayEquals(new byte[]{0, (byte) 0xA0, 0}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void easyFloatingAndEnumFieldsSupportInverseExpressions() {
        var processor = Parser.getProcessor(EasyExpressions.class);
        byte[] bytes = {13, 26, 3, 9};
        EasyExpressions bean = ParserTestSupport.process(processor, bytes);
        assertEquals(1.3f, bean.floating);
        assertEquals(2.6d, bean.decimal);
        assertEquals(Mode.TWO, bean.mode);
        assertEquals(9f, bean.unscaled);
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void bitEnumArraysSupportExpressionsAndCrossBytePadding() {
        var processor = Parser.getProcessor(EnumArray.class);
        byte[] bytes = {(byte) 0x98};
        EnumArray bean = ParserTestSupport.process(processor, bytes);
        assertArrayEquals(new Mode[]{Mode.ONE, Mode.TWO}, bean.values);
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
        bean.values = null;
        assertArrayEquals(new byte[0], ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void floatInverseExpressionsPreserveValuesBeyondIntRange() {
        var processor = Parser.getProcessor(WideFloats.class);
        WideFloats bean = new WideFloats();
        bean.scalar = 300000000f;
        bean.values = new float[]{300000000f, -300000000f};
        bean.easy = 300000000f;
        ByteBuf expected = Unpooled.buffer().writeInt((int) 3000000000L)
                .writeLong(3000000000L).writeLong(-3000000000L).writeInt((int) 3000000000L);
        byte[] bytes = ParserTestSupport.readAll(expected);
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
        WideFloats decoded = ParserTestSupport.process(processor, bytes);
        assertEquals(bean.scalar, decoded.scalar);
        assertArrayEquals(bean.values, decoded.values);
        assertEquals(bean.easy, decoded.easy);
    }

    @Test
    public void scalarAndEasyVariablesUseTransformedValues() {
        var processor = Parser.getProcessor(Variables.class);
        byte[] bytes = {3, 10, 20, 3, 30, 40, 16};
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        ProcessContext readContext = new ProcessContext(input);
        Variables bean = processor.process(input, readContext);
        assertEquals(2, bean.length);
        assertEquals(2, bean.easyLength);
        assertEquals(2d, bean.rounded);
        assertArrayEquals(new int[]{10, 20}, bean.values);
        assertArrayEquals(new int[]{30, 40}, bean.easyValues);
        assertEquals(2, readContext.getGlobalVar('A'));
        assertEquals(2, readContext.getGlobalVar('B'));
        ByteBuf output = Unpooled.buffer();
        ProcessContext writeContext = new ProcessContext(output);
        processor.deProcess(output, writeContext, bean);
        assertArrayEquals(new byte[]{3, 10, 20, 3, 30, 40, 20}, ParserTestSupport.readAll(output));
        assertEquals(2, writeContext.getGlobalVar('A'));
        assertEquals(2, writeContext.getGlobalVar('B'));
    }

    public enum Mode {
        ONE, TWO;
        public static Mode fromInteger(int value) { return value == 2 ? TWO : ONE; }
        public int toInteger() { return ordinal() + 1; }
    }

    public static class EasyBoundaries {
        @F_bit_num_easy(bitStart = 0, bitEnd = 0, end = true)
        public int one;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, end = true)
        public int eight;
        @F_bit_num_easy(bitStart = 8, bitEnd = 0, end = true)
        public int nine;
        @F_bit_num_easy(bitStart = 15, bitEnd = 0, end = true)
        public int sixteen;
        @F_bit_num_easy(bitStart = 16, bitEnd = 0, end = true)
        public int seventeen;
        @F_bit_num_easy(bitStart = 23, bitEnd = 0, end = true)
        public int twentyFour;
        @F_bit_num_easy(bitStart = 24, bitEnd = 0)
        public int twentyFive;
    }

    public static class EasyFull {
        @F_bit_num_easy(bitStart = 31, bitEnd = 0, end = true)
        public int signed;
        @F_bit_num_easy(bitStart = 31, bitEnd = 0)
        public long unsigned;
    }

    public static class EasyGroups {
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, end = true)
        public int first;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public int second;
        @F_num(type = NumType.uint8)
        public int middle;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public int last;
    }

    public static class EasySkipped {
        @F_bit_num_easy(bitStart = 8, bitEnd = 8, skip = true)
        public int high;
        @F_bit_num_easy(bitStart = 7, bitEnd = 4)
        public int value;
        @F_bit_num_easy(bitStart = 3, bitEnd = 0, end = true, skip = true)
        public int low;
        @F_bit_num_easy(bitStart = 0, bitEnd = 0, skip = true)
        public int last;
    }

    public static class EasyExpressions {
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, end = true, valExpr = "x/10")
        public float floating;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, end = true, valExpr = "x/10")
        public double decimal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, end = true, valExpr = "x-1")
        public Mode mode;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public float unscaled;
    }

    public static class EnumArray {
        @F_bit_num_array(len = 2, singleLen = 2, singleSkip = 1, singleValExpr = "x-1")
        public Mode[] values;
    }

    public static class WideFloats {
        @F_bit_num(len = 32, valExpr = "x/10")
        public float scalar;
        @F_bit_num_array(len = 2, singleLen = 64, singleUnsigned = false, singleValExpr = "x/10")
        public float[] values;
        @F_bit_num_easy(bitStart = 31, bitEnd = 0, valExpr = "x/10")
        public float easy;
    }

    public static class Variables {
        @F_bit_num(len = 8, valExpr = "x-1", var = 'a', globalVar = 'A')
        public int length;
        @F_num_array(lenExpr = "a", singleType = NumType.uint8)
        public int[] values;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, valExpr = "x-1", var = 'b', globalVar = 'B')
        public int easyLength;
        @F_num_array(lenExpr = "b", singleType = NumType.uint8)
        public int[] easyValues;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, valExpr = "x/10", precision = 0)
        public double rounded;
    }
}
