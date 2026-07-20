package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class FNumArraySkipTest {
    @Test
    void supportsSkipBeforeAndAfterEachElement() {
        Processor<SkipBean> processor = Parser.getProcessor(SkipBean.class);
        byte[] input = {
                9, 0x01, 0x02, 8, 7,
                6, 0x03, 0x04, 5, 4
        };

        SkipBean target = ParserTestSupport.process(processor, input);
        assertArrayEquals(new int[]{0x0102, 0x0304}, target.values);

        byte[] output = ParserTestSupport.deProcess(processor, target);
        assertArrayEquals(new byte[]{
                0, 0x01, 0x02, 0, 0,
                0, 0x03, 0x04, 0, 0
        }, output);
    }

    public static class SkipBean {
        @F_num_array(len = 2, singleType = NumType.uint16,
                singleSkipBefore = 1, singleSkipAfter = 2)
        public int[] values;
    }
}
