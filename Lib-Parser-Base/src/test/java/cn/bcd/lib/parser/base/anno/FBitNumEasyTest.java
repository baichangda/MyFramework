package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FBitNumEasyTest {
    @Test
    public void supportsGroupedFieldsEndValueExpressionAndVariables() {
        Processor<EasyBean> processor = Parser.getProcessor(EasyBean.class);
        EasyBean bean = new EasyBean();
        bean.flag = 1;
        bean.type = 5;
        bean.value = 341;
        bean.second = 6;

        EasyBean target = processor.process(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.flag, target.flag);
        assertEquals(bean.type, target.type);
        assertEquals(bean.value, target.value);
        assertEquals(bean.second, target.second);

        ProcessContext context = new ProcessContext(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));
        processor.process(context.byteBuf, context);
        assertEquals(1, context.getGlobalNumVar(0));
    }

    public static class EasyBean {
        @F_bit_num_easy(bitStart = 15, bitEnd = 15, globalNumVar = 'A')
        public int flag;

        @F_bit_num_easy(bitStart = 14, bitEnd = 10)
        public int type;

        @F_bit_num_easy(bitStart = 9, bitEnd = 0, end = true)
        public int value;

        @F_bit_num_easy(bitStart = 7, bitEnd = 4, valExpr = "x+1")
        public int second;
    }
}
