package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FVarTest {

    @Test
    void storesFieldValuesAndRestoresParentDuringParse() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        ProcessContext context = new ProcessContext(Unpooled.wrappedBuffer(new byte[]{3, 7}));

        RootBean bean = processor.process(context.byteBuf, context);

        assertSame(bean, context.root);
        assertNull(context.parent);
        assertEquals(3, context.getVar(0));
        assertEquals(7, context.getVar(4));
        assertEquals(1, context.getVar(5));
    }

    @Test
    void storesFieldValuesDuringDeProcess() {
        Processor<RootBean> processor = Parser.getProcessor(RootBean.class);
        RootBean bean = new RootBean();
        bean.type = 4;
        bean.child = new ChildBean();
        bean.child.value = 8;
        bean.parentCheck = 1;
        ProcessContext context = new ProcessContext(Unpooled.buffer());

        processor.deProcess(context.byteBuf, context, bean);

        assertSame(bean, context.root);
        assertNull(context.parent);
        assertEquals(4, context.getVar(0));
        assertEquals(8, context.getVar(4));
        assertEquals(1, context.getVar(5));
    }

    @Test
    void distinguishesStoredNullFromMissingValue() {
        ProcessContext context = new ProcessContext(Unpooled.buffer());
        context.putVar(9, null);

        assertNull(context.getVar(9));
        assertThrows(IllegalStateException.class, () -> context.getVar(8));
        assertThrows(IllegalArgumentException.class, () -> context.putVar(-1, "invalid"));
    }

    @Test
    void rejectsStandaloneAndNegativeFVar() {
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(StandaloneVarBean.class));
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(NegativeVarBean.class));
    }

    public static class RootBean {
        @F_num(type = NumType.uint8)
        @F_var(index = 0)
        public int type;

        @F_bean
        public ChildBean child;

        @F_customize(processorClass = ParentCheckProcessor.class)
        @F_var(index = 5)
        public int parentCheck;
    }

    public static class ChildBean {
        @F_num(type = NumType.uint8)
        @F_var(index = 4)
        public int value;
    }

    public static class ParentCheckProcessor implements Processor<Integer> {
        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            return processContext.root == processContext.parent ? 1 : 0;
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            if (processContext.root != processContext.parent) {
                throw new IllegalStateException("parent was not restored after child processing");
            }
        }
    }

    public static class StandaloneVarBean {
        @F_var(index = 0)
        public int value;
    }

    public static class NegativeVarBean {
        @F_num(type = NumType.uint8)
        @F_var(index = -1)
        public int value;
    }
}
