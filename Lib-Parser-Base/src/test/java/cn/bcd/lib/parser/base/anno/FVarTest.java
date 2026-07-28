package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FVarTest {
    @Test
    public void reusesTopLevelContextAndStoresAnnotatedFieldValues() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        ProcessContext context = new ProcessContext(Unpooled.wrappedBuffer(new byte[]{7, 8, 9}));

        RootBean root = processor.process(context.byteBuf, context);

        assertEquals(7, root.head);
        assertEquals(8, root.child.value);
        assertEquals(9, root.tail);
        assertEquals(7, context.getGlobalVar("head"));
        assertSame(root.child, context.getGlobalVar("child"));
        assertSame(context, ChildValueProcessor.context);
        assertSame(context, RootTailProcessor.context);
    }

    @Test
    public void storesValuesDuringDeProcess() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        RootBean root = new RootBean();
        root.head = 10;
        root.child = new ChildBean();
        root.child.value = 11;
        root.tail = 12;
        ByteBuf byteBuf = Unpooled.buffer();
        ProcessContext context = new ProcessContext(byteBuf);

        processor.deProcess(byteBuf, context, root);

        assertEquals(10, context.getGlobalVar("head"));
        assertSame(root.child, context.getGlobalVar("child"));
        assertSame(context, ChildValueProcessor.context);
        assertSame(context, RootTailProcessor.context);
    }

    public static class RootBean {
        @F_num(type = NumType.uint8)
        @F_global_var(var = "head")
        public int head;

        @F_bean
        @F_global_var(var = "child")
        public ChildBean child;

        @F_customize(processorClass = RootTailProcessor.class)
        public int tail;
    }

    public static class ChildBean {
        @F_customize(processorClass = ChildValueProcessor.class)
        public int value;
    }

    public static class ChildValueProcessor implements Processor<Integer> {
        static ProcessContext context;

        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            assertEquals(7, processContext.getGlobalVar("head"));
            context = processContext;
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            assertEquals(10, processContext.getGlobalVar("head"));
            context = processContext;
            data.writeByte(instance);
        }
    }

    public static class RootTailProcessor implements Processor<Integer> {
        static ProcessContext context;

        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            assertEquals(7, processContext.getGlobalVar("head"));
            context = processContext;
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            assertEquals(10, processContext.getGlobalVar("head"));
            context = processContext;
            data.writeByte(instance);
        }
    }
}
