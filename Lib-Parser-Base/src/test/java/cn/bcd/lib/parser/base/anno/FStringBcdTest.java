package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.StringAppendMode;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FStringBcdTest {
    @Test
    public void fixedLengthBcdRoundTrip() {
        Processor<FixedBean> processor = Parser.getProcessor(FixedBean.class);
        FixedBean bean = new FixedBean();
        bean.value = "1234";

        FixedBean target = ParserTestSupport.roundTrip(processor, bean, (byte) 0x12, (byte) 0x34);

        assertEquals("1234", target.value);
    }

    @Test
    public void lengthExpressionAndPaddingModes() {
        Processor<ExprAppendBean> processor = Parser.getProcessor(ExprAppendBean.class);
        ExprAppendBean bean = new ExprAppendBean();
        bean.len = 2;
        bean.low = "12";
        bean.high = "34";

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);

        assertArrayEquals(new byte[]{2, 0x00, 0x12, 0x34, 0x00}, bytes);
        ExprAppendBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        assertEquals("12", target.low);
        assertEquals("34", target.high);
    }

    public static class FixedBean {
        @F_string_bcd(len = 2)
        public String value;
    }

    public static class ExprAppendBean {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_string_bcd(lenExpr = "a", appendMode = StringAppendMode.lowAddressAppend)
        public String low;

        @F_string_bcd(len = 2, appendMode = StringAppendMode.highAddressAppend)
        public String high;
    }
}
