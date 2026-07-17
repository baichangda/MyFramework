package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FDateBytes6Test {
    @Test
    public void localDateTimeRoundTrip() {
        Processor<Bytes6Bean> processor = Parser.getProcessor(Bytes6Bean.class);
        Bytes6Bean bean = new Bytes6Bean();
        bean.value = LocalDateTime.of(2025, 6, 1, 12, 30, 45);

        Bytes6Bean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.value, target.value);
    }

    public static class Bytes6Bean {
        @F_date_bytes_6(valueZoneId = "+8")
        public LocalDateTime value;
    }
}
