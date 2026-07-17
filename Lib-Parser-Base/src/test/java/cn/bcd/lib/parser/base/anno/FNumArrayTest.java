package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
