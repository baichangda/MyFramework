package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.F_bean;
import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParentContextTest {
    @Test
    void sharesGlobalVariablesWrittenByNestedBeansWithTheWholeObjectGraph() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);

        RootBean parsed = ParserTestSupport.process(processor, (byte) 2, (byte) 10, (byte) 11);

        assertEquals(2, parsed.middle.leaf.length);
        assertArrayEquals(new byte[]{10, 11}, parsed.values);

        RootBean source = new RootBean();
        source.middle = new MiddleBean();
        source.middle.leaf = new LeafBean();
        source.middle.leaf.length = 2;
        source.values = new byte[]{12, 13};
        assertArrayEquals(new byte[]{2, 12, 13}, ParserTestSupport.deProcess(processor, source));
    }

    @Test
    void maintainsParentLevelsAndExpandsTheAncestorStack() {
        ProcessContext context = new ProcessContext(Unpooled.buffer());
        Object[] beans = new Object[10];
        for (int i = 0; i < beans.length; i++) {
            beans[i] = new Object();
            context.enter(beans[i]);
        }

        assertSame(beans[9], context.getParent());
        assertSame(beans[8], context.getParent(1));
        assertSame(beans[0], context.getParent(9));

        for (int i = beans.length - 1; i >= 0; i--) {
            assertSame(beans[i], context.getParent());
            context.exit();
        }
        assertThrows(IllegalStateException.class, context::getParent);
        assertThrows(IllegalStateException.class, context::exit);
    }

    @Test
    void restoresParentStackWhenGeneratedProcessorFails() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        ProcessContext context = new ProcessContext(Unpooled.buffer());

        assertThrows(RuntimeException.class, () -> processor.process(context.byteBuf, context));
        assertThrows(IllegalStateException.class, context::getParent);

        RootBean marker = new RootBean();
        context.enter(marker);
        assertSame(marker, context.getParent());
        context.exit();
    }

    public static class RootBean {
        @F_bean
        public MiddleBean middle;

        @F_num_array(lenExpr = "A", singleType = NumType.uint8)
        public byte[] values;
    }

    public static class MiddleBean {
        @F_bean
        public LeafBean leaf;
    }

    public static class LeafBean {
        @F_customize(processorClass = ParentAwareLengthProcessor.class, globalVar = 'A')
        public int length;
    }

    public static class ParentAwareLengthProcessor implements Processor<Integer> {
        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            assertParents(processContext);
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            assertParents(processContext);
            data.writeByte(instance);
        }

        private static void assertParents(ProcessContext processContext) {
            assertInstanceOf(LeafBean.class, processContext.getParent());
            assertInstanceOf(MiddleBean.class, processContext.getParent(1));
            assertInstanceOf(RootBean.class, processContext.getParent(2));
            assertSame(processContext.getParent(2), processContext.findParent(RootBean.class));
        }
    }
}
