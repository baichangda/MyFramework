package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FDateBcdTest {
    @Test
    public void localDateTimeRoundTrip() {
        Processor<BcdBean> processor = Parser.getProcessor(BcdBean.class);
        BcdBean bean = new BcdBean();
        bean.value = LocalDateTime.of(2025, 6, 1, 12, 30, 45);

        BcdBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.value, target.value);
    }

    public static class BcdBean {
        @F_date_bcd(valueZoneId = "+8")
        public LocalDateTime value;
    }
}
