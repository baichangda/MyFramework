package cn.bcd.lib.parser.base.complier;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicProcessorCompilerTest {

    @Test
    void compilesGeneratedProcessorForTopLevelClassWithJdkCompiler() {
        DynamicProcessorCompiler.setCompiler(new JdkDynamicProcessorCompiler());
        try {
            Processor<JdkTopLevelBean> processor = Parser.getProcessor(JdkTopLevelBean.class);

            JdkTopLevelBean bean = ParserTestSupport.process(processor, (byte) 123);

            assertEquals(123, bean.value);
            assertEquals(123, ParserTestSupport.deProcess(processor, bean)[0] & 0xff);
        } finally {
            DynamicProcessorCompiler.setCompiler(new JavassistDynamicProcessorCompiler());
            Parser.clearProcessorCache();
        }
    }

    @Test
    void compilesGeneratedProcessorForStaticNestedClassWithJdkCompiler() {
        DynamicProcessorCompiler.setCompiler(new JdkDynamicProcessorCompiler());
        try {
            Processor<JdkBean> processor = Parser.getProcessor(JdkBean.class);

            JdkBean bean = ParserTestSupport.process(processor, (byte) 123);

            assertEquals(123, bean.value);
            assertEquals(123, ParserTestSupport.deProcess(processor, bean)[0] & 0xff);
        } finally {
            DynamicProcessorCompiler.setCompiler(new JavassistDynamicProcessorCompiler());
            Parser.clearProcessorCache();
        }
    }

    public static class JdkBean {
        @F_num(type = NumType.uint8)
        public int value;
    }
}
