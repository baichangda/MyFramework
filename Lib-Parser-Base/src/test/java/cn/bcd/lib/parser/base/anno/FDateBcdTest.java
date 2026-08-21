package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FDateBcdTest {
    @Test
    public void localDateTimeRoundTrip() {
        Processor<BcdBean> processor = Parser.getProcessor(BcdBean.class);
        assertTrue(Arrays.stream(processor.getClass().getDeclaredFields())
                .anyMatch(field -> field.getType() == ZoneOffset.class));
        BcdBean bean = new BcdBean();
        bean.value = LocalDateTime.of(2025, 6, 1, 12, 30, 45);

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);
        assertArrayEquals(new byte[]{0x25, 0x06, 0x01, 0x12, 0x30, 0x45}, bytes);

        BcdBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        assertEquals(bean.value, target.value);
    }

    public static class BcdBean {
        @F_date_bcd(valueZoneId = "+8")
        public LocalDateTime value;
    }
}
