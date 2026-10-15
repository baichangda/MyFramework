package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FNumArrayRegressionTest {
    @Test
    public void checkedEnumsSupportExpressionsSentinelsAndElementPadding() {
        var processor = Parser.getProcessor(CheckedEnum.class);
        byte[] input = {3, 0, (byte) 0xFF, 0, (byte) 0xFE, 0};
        CheckedEnum bean = ParserTestSupport.process(processor, input);
        assertArrayEquals(new FNumTest.Mode[]{FNumTest.Mode.TWO, null, null}, bean.values);
        assertArrayEquals(new byte[]{0, 1, 2}, bean.values__v);
        assertArrayEquals(input, ParserTestSupport.deProcess(processor, bean));

        bean = ParserTestSupport.process(processor, (byte) 2, (byte) 0, (byte) 3, (byte) 0, (byte) 2, (byte) 0);
        assertArrayEquals(new FNumTest.Mode[]{FNumTest.Mode.ONE, FNumTest.Mode.TWO, FNumTest.Mode.ONE}, bean.values);
        assertNull(bean.values__v);
        assertArrayEquals(new byte[]{2, 0, 3, 0, 2, 0}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void checkedEnumsWithoutExpressionsSupportEmptyAndNormalArrays() {
        var processor = Parser.getProcessor(EnumLength.class);
        EnumLength bean = ParserTestSupport.process(processor, (byte) 0);
        assertNull(bean.values);
        assertNull(bean.values__v);
        assertArrayEquals(new byte[]{0}, ParserTestSupport.deProcess(processor, bean));

        bean = ParserTestSupport.process(processor, (byte) 2, (byte) 1, (byte) 2);
        assertArrayEquals(new FNumTest.Mode[]{FNumTest.Mode.ONE, FNumTest.Mode.TWO}, bean.values);
        assertNull(bean.values__v);
        assertArrayEquals(new byte[]{2, 1, 2}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void floatingWireExpressionsPreserveFractionalInverseValues() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            var processor = Parser.getProcessor(Floating.class, order, DefaultNumValGetter.instance);
            Floating bean = new Floating();
            bean.floats = new float[]{1.25f, -1.25f};
            bean.doubles = new double[]{1.25d, -1.25d};
            ByteBuf encoded = Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean));
            for (float expected : new float[]{0.625f, -0.625f}) {
                assertEquals(expected, order == ByteOrder.bigEndian ? encoded.readFloat() : encoded.readFloatLE());
            }
            for (double expected : new double[]{0.625d, -0.625d}) {
                assertEquals(expected, order == ByteOrder.bigEndian ? encoded.readDouble() : encoded.readDoubleLE());
            }
            Floating decoded = processor.process(encoded.readerIndex(0));
            assertArrayEquals(bean.floats, decoded.floats);
            assertArrayEquals(bean.doubles, decoded.doubles);
        }
    }

    @Test
    public void floatExpressionsWriteUnsigned32AndSigned64BeyondIntRange() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            var processor = Parser.getProcessor(WideFloat.class, order, DefaultNumValGetter.instance);
            WideFloat bean = new WideFloat();
            bean.unsigned = new float[]{300000000f};
            bean.checkedUnsigned = bean.unsigned;
            bean.signed = new float[]{300000000f, -300000000f};
            bean.checkedSigned = bean.signed;
            ByteBuf encoded = Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean));
            for (int i = 0; i < 2; i++) {
                assertEquals(3000000000L, order == ByteOrder.bigEndian
                        ? encoded.readUnsignedInt() : encoded.readUnsignedIntLE());
            }
            for (int i = 0; i < 2; i++) {
                for (long expected : new long[]{3000000000L, -3000000000L}) {
                    assertEquals(expected, order == ByteOrder.bigEndian ? encoded.readLong() : encoded.readLongLE());
                }
            }
            WideFloat decoded = processor.process(encoded.readerIndex(0));
            assertArrayEquals(bean.unsigned, decoded.unsigned);
            assertArrayEquals(bean.checkedUnsigned, decoded.checkedUnsigned);
            assertArrayEquals(bean.signed, decoded.signed);
            assertArrayEquals(bean.checkedSigned, decoded.checkedSigned);
            assertNull(decoded.checkedUnsigned__v);
            assertNull(decoded.checkedSigned__v);
        }
    }

    @Test
    public void integerWireExpressionsStillRoundFractionalValues() {
        var processor = Parser.getProcessor(Rounded.class);
        Rounded bean = new Rounded();
        bean.values = new float[]{1.26f};
        bean.checked = new float[]{1.26f};
        assertArrayEquals(new byte[]{13, 13}, ParserTestSupport.deProcess(processor, bean));
    }

    public static class CheckedEnum {
        @F_num_array(len = 3, singleType = NumType.uint8, singleCheckVal = true,
                singleValExpr = "x-1", singleSkipAfter = 1)
        public FNumTest.Mode[] values;
        public byte[] values__v;
    }

    public static class EnumLength {
        @F_num(type = NumType.uint8, var = 'a')
        public int length;
        @F_num_array(lenExpr = "a", singleType = NumType.uint8, singleCheckVal = true)
        public FNumTest.Mode[] values;
        public byte[] values__v;
    }

    public static class Floating {
        @F_num_array(len = 2, singleType = NumType.float32, singleValExpr = "x*2")
        public float[] floats;
        @F_num_array(len = 2, singleType = NumType.float64, singleValExpr = "x*2")
        public double[] doubles;
    }

    public static class WideFloat {
        @F_num_array(len = 1, singleType = NumType.uint32, singleValExpr = "x/10")
        public float[] unsigned;
        @F_num_array(len = 1, singleType = NumType.uint32, singleValExpr = "x/10", singleCheckVal = true)
        public float[] checkedUnsigned;
        public byte[] checkedUnsigned__v;
        @F_num_array(len = 2, singleType = NumType.int64, singleValExpr = "x/10")
        public float[] signed;
        @F_num_array(len = 2, singleType = NumType.int64, singleValExpr = "x/10", singleCheckVal = true)
        public float[] checkedSigned;
        public byte[] checkedSigned__v;
    }

    public static class Rounded {
        @F_num_array(len = 1, singleType = NumType.uint8, singleValExpr = "x/10")
        public float[] values;
        @F_num_array(len = 1, singleType = NumType.uint8, singleValExpr = "x/10", singleCheckVal = true)
        public float[] checked;
        public byte[] checked__v;
    }
}
