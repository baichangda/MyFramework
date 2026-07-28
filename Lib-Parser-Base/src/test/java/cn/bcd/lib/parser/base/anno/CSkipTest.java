package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CSkipTest {
    @Test
    public void fixedClassLengthSkipsAndPadsRemainingBytes() {
        Processor<FixedSkipBean> processor = Parser.getProcessor(FixedSkipBean.class);
        FixedSkipBean bean = new FixedSkipBean();
        bean.value = 7;

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);

        assertArrayEquals(new byte[]{7, 0, 0}, bytes);
        FixedSkipBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{7, 8, 9}));
        assertEquals(7, target.value);
    }

    @Test
    public void dynamicClassLengthUsesParsedVariable() {
        Processor<DynamicSkipBean> processor = Parser.getProcessor(DynamicSkipBean.class);
        DynamicSkipBean bean = new DynamicSkipBean();
        bean.len = 4;
        bean.value = 9;

        assertArrayEquals(new byte[]{4, 9, 0, 0}, ParserTestSupport.deProcess(processor, bean));
    }

    @C_skip(len = 3)
    public static class FixedSkipBean {
        @F_num(type = NumType.uint8)
        public int value;
    }

    @C_skip(lenExpr = "a")
    public static class DynamicSkipBean {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_num(type = NumType.uint8)
        public int value;
    }
}
