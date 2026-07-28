package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FBeanListTest {
    @Test
    public void supportsArrayListLengthExpressionAndNullDeParse() {
        Processor<Container> processor = Parser.getProcessor(Container.class);
        Container bean = new Container();
        bean.len = 2;
        bean.array = new Child[]{child(3), child(4)};
        bean.list = List.of(child(5), child(6));

        Container target = ParserTestSupport.roundTrip(processor, bean, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6);

        assertEquals(2, target.array.length);
        assertEquals(3, target.array[0].value);
        assertEquals(4, target.array[1].value);
        assertEquals(2, target.list.size());
        assertEquals(5, target.list.get(0).value);
        assertEquals(6, target.list.get(1).value);

        bean.array = null;
        bean.list = null;
        assertArrayEquals(new byte[]{2}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void usesNullArrayAndListForZeroLength() {
        Processor<VariableContainer> processor = Parser.getProcessor(VariableContainer.class);

        VariableContainer target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{0}));

        assertNull(target.array);
        assertNull(target.list);
        assertArrayEquals(new byte[]{0}, ParserTestSupport.deProcess(processor, target));
    }

    public static class VariableContainer {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_bean_list(listLenExpr = "a")
        public Child[] array;

        @F_bean_list(listLenExpr = "a")
        public List<Child> list;
    }

    private static Child child(int value) {
        Child child = new Child();
        child.value = value;
        return child;
    }

    public static class Container {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int len;

        @F_bean_list(listLenExpr = "a")
        public Child[] array;

        @F_bean_list(listLen = 2)
        public List<Child> list;
    }

    public static class Child {
        @F_num(type = NumType.uint8)
        public int value;
    }
}
