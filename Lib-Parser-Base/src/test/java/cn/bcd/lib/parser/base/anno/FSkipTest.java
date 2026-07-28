package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FSkipTest {
    @Test
    public void skipsBeforeAndAfterWithFixedAndExpressionLengths() {
        Processor<SkipBean> processor = Parser.getProcessor(SkipBean.class);
        SkipBean bean = new SkipBean();
        bean.len = 2;
        bean.value = 9;

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);

        assertArrayEquals(new byte[]{2, 0, 0, 9, 0, 0}, bytes);
        SkipBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{2, 8, 8, 9, 7, 7}));
        assertEquals(2, target.len);
        assertEquals(9, target.value);
    }

    public static class SkipBean {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_skip(lenExprBefore = "a", lenAfter = 2)
        @F_num(type = NumType.uint8)
        public int value;
    }
}
