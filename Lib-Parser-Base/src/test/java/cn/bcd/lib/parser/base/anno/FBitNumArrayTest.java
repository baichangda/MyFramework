package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.BitRemainingMode;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FBitNumArrayTest {
    @Test
    public void supportsFixedLengthExpressionSkipPrecisionAndNullDeParse() {
        Processor<BitArrayBean> processor = Parser.getProcessor(BitArrayBean.class);
        BitArrayBean bean = new BitArrayBean();
        bean.values = new int[]{2, 3};
        bean.floats = new double[]{1.2, 1.3};

        BitArrayBean target = processor.process(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertArrayEquals(bean.values, target.values);
        assertArrayEquals(bean.floats, target.floats);
        bean.values = null;
        bean.floats = null;
        assertEquals(0, ParserTestSupport.deProcess(processor, bean).length);
    }

    @Test
    public void usesNullForZeroLengthArray() {
        Processor<VariableBitArrayBean> processor = Parser.getProcessor(VariableBitArrayBean.class);

        VariableBitArrayBean bean = processor.process(Unpooled.wrappedBuffer(new byte[]{0}));

        assertEquals(0, bean.len);
        assertNull(bean.values);
        assertArrayEquals(new byte[]{0}, ParserTestSupport.deProcess(processor, bean));
    }

    public static class VariableBitArrayBean {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_bit_num_array(lenExpr = "a", singleLen = 8)
        public int[] values;
    }

    public static class BitArrayBean {
        @F_bit_num_array(len = 2, singleLen = 3, singleValExpr = "x-1", singleSkip = 1,
                bitRemainingMode = BitRemainingMode.not_ignore)
        public int[] values;

        @F_bit_num_array(len = 2, singleLen = 4, singleValExpr = "x/10", singlePrecision = 1)
        public double[] floats;
    }
}
