package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.StringAppendMode;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FStringTest {
    @Test
    public void fixedLengthAndUtf8RoundTrip() {
        Processor<FixedBean> processor = Parser.getProcessor(FixedBean.class);
        FixedBean bean = new FixedBean();
        bean.value = "AB";

        FixedBean target = ParserTestSupport.roundTrip(processor, bean, (byte) 'A', (byte) 'B');

        assertEquals("AB", target.value);
    }

    @Test
    public void lengthExpressionUsesPreviousVariable() {
        Processor<ExprBean> processor = Parser.getProcessor(ExprBean.class);
        ExprBean bean = new ExprBean();
        bean.len = 3;
        bean.value = "XYZ";

        ExprBean target = ParserTestSupport.roundTrip(processor, bean, (byte) 3, (byte) 'X', (byte) 'Y', (byte) 'Z');

        assertEquals(3, target.len);
        assertEquals("XYZ", target.value);
    }

    @Test
    public void appendModesPadAndTrimZeroBytes() {
        Processor<AppendBean> processor = Parser.getProcessor(AppendBean.class);
        AppendBean bean = new AppendBean();
        bean.low = "A";
        bean.high = "B";

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);

        assertArrayEquals(new byte[]{0, 0, 'A', 'B', 0, 0}, bytes);
        AppendBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        assertEquals("A", target.low);
        assertEquals("B", target.high);
    }

    @Test
    public void noAppendRejectsLengthMismatch() {
        Processor<FixedBean> processor = Parser.getProcessor(FixedBean.class);
        FixedBean bean = new FixedBean();
        bean.value = "A";

        assertThrows(RuntimeException.class, () -> ParserTestSupport.deProcess(processor, bean));
    }

    public static class FixedBean {
        @F_string(len = 2)
        public String value;
    }

    public static class ExprBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int len;

        @F_string(lenExpr = "a")
        public String value;
    }

    public static class AppendBean {
        @F_string(len = 3, appendMode = StringAppendMode.lowAddressAppend)
        public String low;

        @F_string(len = 3, appendMode = StringAppendMode.highAddressAppend)
        public String high;
    }
}
