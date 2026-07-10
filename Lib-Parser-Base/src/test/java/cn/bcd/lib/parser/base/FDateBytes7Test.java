package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.F_date_bytes_7;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FDateBytes7Test {
    @Test
    public void localDateTimeAndFormattedStringRoundTrip() {
        Processor<Bytes7Bean> processor = Parser.getProcessor(Bytes7Bean.class);
        Bytes7Bean bean = new Bytes7Bean();
        bean.value = LocalDateTime.of(2025, 6, 1, 12, 30, 45);
        bean.text = "20250601123045";

        Bytes7Bean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.value, target.value);
        assertEquals(bean.text, target.text);
    }

    public static class Bytes7Bean {
        @F_date_bytes_7(order = ByteOrder.smallEndian, valueZoneId = "+8")
        public LocalDateTime value;

        @F_date_bytes_7(stringFormat = "yyyyMMddHHmmss", valueZoneId = "+8")
        public String text;
    }
}
