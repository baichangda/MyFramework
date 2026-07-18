package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.BitRemainingMode;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FBitNumTest {
    @Test
    public void supportsSignedUnsignedSkipExpressionAndFinishModes() {
        Processor<BitBean> processor = Parser.getProcessor(BitBean.class);
        BitBean bean = new BitBean();
        bean.a = 5;
        bean.b = -1;
        bean.c = 3;

        BitBean target = processor.process(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.a, target.a);
        assertEquals(bean.b, target.b);
        assertEquals(bean.c, target.c);
    }

    public static class BitBean {
        @F_bit_num(len = 3, var = 'a', bitRemainingMode = BitRemainingMode.not_ignore)
        public int a;

        @F_bit_num(len = 3, unsigned = false, bitRemainingMode = BitRemainingMode.not_ignore)
        public int b;

        @F_bit_num(len = 2, valExpr = "x+1", bitRemainingMode = BitRemainingMode.Default)
        public int c;
    }
}
