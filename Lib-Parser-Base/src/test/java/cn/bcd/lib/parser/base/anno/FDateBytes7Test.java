package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    public void intArrayRoundTrip() {
        Processor<Bytes7ArrayBean> processor = Parser.getProcessor(Bytes7ArrayBean.class);
        Bytes7ArrayBean bean = new Bytes7ArrayBean();
        bean.value = new int[]{2025, 6, 1, 12, 30, 45};

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);
        assertArrayEquals(new byte[]{(byte) 0xe9, 0x07, 6, 1, 12, 30, 45}, bytes);

        Bytes7ArrayBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        assertArrayEquals(bean.value, target.value);
    }

    public static class Bytes7Bean {
        @F_date_bytes_7(yearByteOrder = ByteOrder.smallEndian, valueZoneId = "+8")
        public LocalDateTime value;

        @F_date_bytes_7(stringFormat = "yyyyMMddHHmmss", valueZoneId = "+8")
        public String text;
    }

    public static class Bytes7ArrayBean {
        @F_date_bytes_7(yearByteOrder = ByteOrder.smallEndian)
        public int[] value;
    }
}
