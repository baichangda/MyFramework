package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(7, context.getVar(2));
        assertSame(root.child, context.getVar(5));
        assertSame(context, ChildValueProcessor.context);
        assertSame(context, RootTailProcessor.context);
        assertNull(context.instance);
    }

    @Test
    public void storesValuesDuringDeProcessAndRestoresContextInstance() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        RootBean root = new RootBean();
        root.head = 10;
        root.child = new ChildBean();
        root.child.value = 11;
        root.tail = 12;
        ByteBuf byteBuf = Unpooled.buffer();
        ProcessContext context = new ProcessContext(byteBuf);
        Object originalInstance = new Object();
        context.instance = originalInstance;

        processor.deProcess(byteBuf, context, root);

        assertEquals(10, context.getVar(2));
        assertSame(root.child, context.getVar(5));
        assertSame(context, ChildValueProcessor.context);
        assertSame(context, RootTailProcessor.context);
        assertSame(originalInstance, context.instance);
    }

    public static class RootBean {
        @F_num(type = NumType.uint8)
        @F_var(index = 2)
        public int head;

        @F_bean
        @F_var(index = 5)
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
            if (!(processContext.instance instanceof ChildBean)) {
                throw new IllegalStateException("current instance is not ChildBean");
            }
            context = processContext;
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            if (!(processContext.instance instanceof ChildBean)) {
                throw new IllegalStateException("current instance is not ChildBean");
            }
            context = processContext;
            data.writeByte(instance);
        }
    }

    public static class RootTailProcessor implements Processor<Integer> {
        static ProcessContext context;

        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            if (!(processContext.instance instanceof RootBean)) {
                throw new IllegalStateException("current instance is not RootBean");
            }
            context = processContext;
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            if (!(processContext.instance instanceof RootBean)) {
                throw new IllegalStateException("current instance is not RootBean");
            }
            context = processContext;
            data.writeByte(instance);
        }
    }
}
