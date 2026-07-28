package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CImplTest {
    @Test
    public void selectsInterfaceImplementationByExpressionAndDefaultBranch() {
        Processor<InterfaceContainer> processor = Parser.getProcessor(InterfaceContainer.class);
        InterfaceContainer one = new InterfaceContainer();
        one.kind = 1;
        OneImpl oneImpl = new OneImpl();
        oneImpl.value = 8;
        one.item = oneImpl;

        InterfaceContainer oneTarget = ParserTestSupport.roundTrip(processor, one, (byte) 1, (byte) 8);
        assertInstanceOf(OneImpl.class, oneTarget.item);
        assertEquals(8, ((OneImpl) oneTarget.item).value);

        InterfaceContainer fallback = ParserTestSupport.process(processor, (byte) 9, (byte) 10);
        assertInstanceOf(DefaultImpl.class, fallback.item);
        assertEquals(10, ((DefaultImpl) fallback.item).value);
    }

    public static class InterfaceContainer {
        @F_num(type = NumType.uint8, numVar = 'a')
        public int kind;

        @F_bean(implClassExpr = "a")
        public Item item;
    }

    public interface Item {
    }

    @C_impl(1)
    public static class OneImpl implements Item {
        @F_num(type = NumType.uint8)
        public int value;
    }

    @C_impl(C_impl.Default)
    public static class DefaultImpl implements Item {
        @F_num(type = NumType.uint8)
        public int value;
    }
}
