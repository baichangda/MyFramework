package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.F_bean;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FBeanTest {
    @Test
    public void parsesAndDeParsesConcreteBeanField() {
        Processor<ConcreteContainer> processor = Parser.getProcessor(ConcreteContainer.class);
        ConcreteContainer bean = new ConcreteContainer();
        bean.child = new Child();
        bean.child.value = 7;

        ConcreteContainer target = ParserTestSupport.roundTrip(processor, bean, (byte) 7);

        assertEquals(7, target.child.value);
    }

    public static class ConcreteContainer {
        @F_bean
        public Child child;
    }

    public static class Child {
        @F_num(type = NumType.uint8)
        public int value;
    }
}
