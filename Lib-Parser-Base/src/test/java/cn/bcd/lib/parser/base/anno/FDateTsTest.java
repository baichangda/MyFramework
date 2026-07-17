package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DateTsMode;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FDateTsTest {
    @Test
    public void supportsAllTimestampModesAndDateRepresentations() {
        Processor<TsBean> processor = Parser.getProcessor(TsBean.class);
        TsBean bean = new TsBean();
        bean.msLong = 1_700_000_000_123L;
        bean.secInt = 1_700_000_000;
        bean.instant = Instant.ofEpochMilli(1_700_000_000_123L);
        bean.local = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.ofHours(8));
        bean.text = "20231114221320";

        TsBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.msLong, target.msLong);
        assertEquals(bean.secInt, target.secInt);
        assertEquals(bean.instant, target.instant);
        assertEquals(bean.local, target.local);
        assertEquals(bean.text, target.text);
    }

    public static class TsBean {
        @F_date_ts(mode = DateTsMode.uint64_ms)
        public long msLong;

        @F_date_ts(mode = DateTsMode.uint32_s, order = ByteOrder.smallEndian)
        public int secInt;

        @F_date_ts(mode = DateTsMode.float64_ms)
        public Instant instant;

        @F_date_ts(mode = DateTsMode.uint64_s, valueZoneId = "+8")
        public LocalDateTime local;

        @F_date_ts(mode = DateTsMode.float64_s, stringFormat = "yyyyMMddHHmmss", valueZoneId = "+0")
        public String text;
    }
}
