package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FNumRegressionTest {
    @Test
    public void checkedExpressionVariablesSupportNormalAndInvalidValues() {
        Processor<CheckedLength> processor = Parser.getProcessor(CheckedLength.class);
        byte[] bytes = {3, 10, 20};
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        ProcessContext readContext = new ProcessContext(input);
        CheckedLength bean = processor.process(input, readContext);
        assertEquals(2, bean.length);
        assertEquals(0, bean.length__v);
        assertArrayEquals(new int[]{10, 20}, bean.values);
        assertEquals(2, readContext.getGlobalVar('A'));
        ByteBuf output = Unpooled.buffer();
        ProcessContext writeContext = new ProcessContext(output);
        processor.deProcess(output, writeContext, bean);
        assertArrayEquals(bytes, ParserTestSupport.readAll(output));
        assertEquals(2, writeContext.getGlobalVar('A'));

        for (byte sentinel : new byte[]{(byte) 0xFF, (byte) 0xFE}) {
            input = Unpooled.wrappedBuffer(new byte[]{sentinel});
            readContext = new ProcessContext(input);
            bean = processor.process(input, readContext);
            assertNotEquals(0, bean.length__v);
            assertNull(bean.values);
            assertEquals(0, readContext.getGlobalVar('A'));
            output = Unpooled.buffer();
            writeContext = new ProcessContext(output);
            processor.deProcess(output, writeContext, bean);
            assertArrayEquals(new byte[]{sentinel}, ParserTestSupport.readAll(output));
            assertEquals(0, writeContext.getGlobalVar('A'));
        }
    }

    @Test
    public void uncheckedVariablesUseTransformedFieldValues() {
        Processor<Length> processor = Parser.getProcessor(Length.class);
        byte[] bytes = {3, 10, 20};
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        ProcessContext readContext = new ProcessContext(input);
        Length bean = processor.process(input, readContext);
        assertEquals(2, bean.length);
        assertArrayEquals(new int[]{10, 20}, bean.values);
        assertEquals(0, input.readableBytes());
        assertEquals(2, readContext.getGlobalVar('A'));
        ByteBuf output = Unpooled.buffer();
        ProcessContext writeContext = new ProcessContext(output);
        processor.deProcess(output, writeContext, bean);
        assertArrayEquals(bytes, ParserTestSupport.readAll(output));
        assertEquals(2, writeContext.getGlobalVar('A'));
    }

    @Test
    public void checkedEnumsSupportExpressionsAndSentinels() {
        Processor<CheckedEnum> processor = Parser.getProcessor(CheckedEnum.class);
        CheckedEnum bean = ParserTestSupport.process(processor, (byte) 3);
        assertEquals(FNumTest.Mode.TWO, bean.mode);
        assertEquals(0, bean.mode__v);
        assertArrayEquals(new byte[]{3}, ParserTestSupport.deProcess(processor, bean));
        bean = ParserTestSupport.process(processor, (byte) 0xFF);
        assertEquals(1, bean.mode__v);
        assertNull(bean.mode);
        assertArrayEquals(new byte[]{(byte) 0xFF}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void floatingWireTypesPreserveFractionalInverseExpressions() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            Processor<Floating> processor = Parser.getProcessor(Floating.class, order,
                    DefaultNumValGetter.instance);
            Floating bean = new Floating();
            bean.f32 = 1.25f;
            bean.f64 = 1.25d;
            ByteBuf encoded = Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean));
            if (order == ByteOrder.bigEndian) {
                assertEquals(0.625f, encoded.readFloat());
                assertEquals(0.625d, encoded.readDouble());
            } else {
                assertEquals(0.625f, encoded.readFloatLE());
                assertEquals(0.625d, encoded.readDoubleLE());
            }
            Floating decoded = processor.process(encoded.readerIndex(0));
            assertEquals(bean.f32, decoded.f32);
            assertEquals(bean.f64, decoded.f64);
        }
    }

    @Test
    public void unsigned32PreservesUpperHalfFromDoubleWithAndWithoutChecking() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            Processor<UnsignedDouble> processor = Parser.getProcessor(UnsignedDouble.class, order,
                    DefaultNumValGetter.instance);
            for (long value : new long[]{0, 0x7FFFFFFFL, 0x80000000L, 3000000000L, 0xFFFFFFFDL}) {
                UnsignedDouble bean = new UnsignedDouble();
                bean.plain = value;
                bean.checked = value;
                bean.expression = value / 10d;
                ByteBuf encoded = Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean));
                for (int i = 0; i < 3; i++) {
                    assertEquals(value, order == ByteOrder.bigEndian
                            ? encoded.readUnsignedInt() : encoded.readUnsignedIntLE());
                }
                UnsignedDouble decoded = processor.process(encoded.readerIndex(0));
                assertEquals(bean.plain, decoded.plain);
                assertEquals(bean.checked, decoded.checked);
                assertEquals(bean.expression, decoded.expression);
            }
        }
    }

    @Test
    public void integerWireTypesStillRoundInverseFloatingExpressions() {
        Processor<IntegerWire> processor = Parser.getProcessor(IntegerWire.class);
        IntegerWire bean = new IntegerWire();
        bean.plain = 1.26;
        bean.checked = 1.26;
        assertArrayEquals(new byte[]{13, 13}, ParserTestSupport.deProcess(processor, bean));
    }

    public static class CheckedLength {
        @F_num(type = NumType.uint8, checkVal = true, valExpr = "x-1", var = 'a', globalVar = 'A')
        public int length;
        public byte length__v;
        @F_num_array(singleType = NumType.uint8, lenExpr = "a")
        public int[] values;
    }

    public static class Length {
        @F_num(type = NumType.uint8, valExpr = "x-1", var = 'a', globalVar = 'A')
        public int length;
        @F_num_array(singleType = NumType.uint8, lenExpr = "a")
        public int[] values;
    }

    public static class CheckedEnum {
        @F_num(type = NumType.uint8, checkVal = true, valExpr = "x-1")
        public FNumTest.Mode mode;
        public byte mode__v;
    }

    public static class Floating {
        @F_num(type = NumType.float32, valExpr = "x*2")
        public float f32;
        @F_num(type = NumType.float64, valExpr = "x*2")
        public double f64;
    }

    public static class UnsignedDouble {
        @F_num(type = NumType.uint32)
        public double plain;
        @F_num(type = NumType.uint32, checkVal = true)
        public double checked;
        public byte checked__v;
        @F_num(type = NumType.uint32, checkVal = true, valExpr = "x/10")
        public double expression;
        public byte expression__v;
    }

    public static class IntegerWire {
        @F_num(type = NumType.uint8, valExpr = "x/10")
        public double plain;
        @F_num(type = NumType.uint8, checkVal = true, valExpr = "x/10")
        public double checked;
        public byte checked__v;
    }
}
