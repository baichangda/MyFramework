package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FCustomizeTest {
    @Test
    public void usesCustomProcessorArgsVariablesGlobalNumVarsAndDeParse() {
        Processor<CustomBean> processor = Parser.getProcessor(CustomBean.class);
        CustomBean bean = new CustomBean();
        bean.value = 12;

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);
        CustomBean target = processor.process(Unpooled.wrappedBuffer(bytes));

        assertEquals(12, target.value);
        ProcessContext context = new ProcessContext(Unpooled.wrappedBuffer(bytes));
        processor.process(context.byteBuf, context);
        assertEquals(12, context.getGlobalNumVar(0));
    }

    public static class CustomBean {
        @F_customize(processorClass = OffsetProcessor.class, processorArgs = "2", numVar = 'a', globalNumVar = 'A')
        public int value;
    }

    public static class OffsetProcessor implements Processor<Integer> {
        private final int offset;

        public OffsetProcessor(int offset) {
            this.offset = offset;
        }

        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            return data.readUnsignedByte() + offset;
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            data.writeByte(instance - offset);
        }
    }
}
