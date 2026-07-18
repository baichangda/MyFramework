package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FNumArrayTest {
    @Test
    public void supportsFixedLengthByteFastPathAndNullDeParse() {
        Processor<ByteArrayBean> processor = Parser.getProcessor(ByteArrayBean.class);
        ByteArrayBean bean = new ByteArrayBean();
        bean.values = new byte[]{1, 2, 3};

        ByteArrayBean target = ParserTestSupport.roundTrip(processor, bean, (byte) 1, (byte) 2, (byte) 3);

        assertArrayEquals(bean.values, target.values);
        bean.values = null;
        assertEquals(0, ParserTestSupport.deProcess(processor, bean).length);
    }

    @Test
    public void supportsLengthExpressionSkipValueExpressionPrecisionAndByteOrder() {
        Processor<ExpressionArrayBean> processor = Parser.getProcessor(ExpressionArrayBean.class);
        ExpressionArrayBean bean = new ExpressionArrayBean();
        bean.len = 2;
        bean.values = new int[]{2, 3};
        bean.floats = new double[]{1.2, 1.3};

        ExpressionArrayBean target = processor.process(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(2, target.len);
        assertArrayEquals(bean.values, target.values);
        assertArrayEquals(bean.floats, target.floats);
    }

    @Test
    public void usesNullForZeroLengthAndLazilyCreatesValueTypeArray() {
        Processor<CheckedArrayBean> processor = Parser.getProcessor(CheckedArrayBean.class);

        CheckedArrayBean empty = processor.process(Unpooled.wrappedBuffer(new byte[]{0}));
        assertNull(empty.values);
        assertNull(empty.values__v);
        assertArrayEquals(new byte[]{0}, ParserTestSupport.deProcess(processor, empty));

        CheckedArrayBean normal = processor.process(Unpooled.wrappedBuffer(new byte[]{3, 1, 2, 3}));
        assertArrayEquals(new int[]{1, 2, 3}, normal.values);
        assertNull(normal.values__v);
        assertArrayEquals(new byte[]{3, 1, 2, 3}, ParserTestSupport.deProcess(processor, normal));

        CheckedArrayBean special = processor.process(Unpooled.wrappedBuffer(new byte[]{3, 1, (byte) 0xFF, (byte) 0xFE}));
        assertArrayEquals(new int[]{1, 0, 0}, special.values);
        assertArrayEquals(new byte[]{0, 1, 2}, special.values__v);
        assertArrayEquals(new byte[]{3, 1, (byte) 0xFF, (byte) 0xFE}, ParserTestSupport.deProcess(processor, special));
    }

    public static class CheckedArrayBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int len;

        @F_num_array(lenExpr = "a", singleType = NumType.uint8, singleCheckVal = true)
        public int[] values;
        public byte[] values__v;
    }

    public static class ByteArrayBean {
        @F_num_array(len = 3, singleType = NumType.uint8)
        public byte[] values;
    }

    public static class ExpressionArrayBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int len;

        @F_num_array(lenExpr = "a", singleType = NumType.uint16, singleOrder = ByteOrder.smallEndian,
                singleValExpr = "x-1", singleSkip = 1)
        public int[] values;

        @F_num_array(len = 2, singleType = NumType.uint8, singleValExpr = "x/10", singlePrecision = 1)
        public double[] floats;
    }
}
