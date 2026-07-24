package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.processor.Processor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    public void intArrayRoundTrip() {
        Processor<Bytes6ArrayBean> processor = Parser.getProcessor(Bytes6ArrayBean.class);
        Bytes6ArrayBean bean = new Bytes6ArrayBean();
        bean.value = new int[]{2025, 6, 1, 12, 30, 45};

        byte[] bytes = ParserTestSupport.deProcess(processor, bean);
        assertArrayEquals(new byte[]{125, 6, 1, 12, 30, 45}, bytes);

        Bytes6ArrayBean target = processor.process(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        assertArrayEquals(bean.value, target.value);
    }

    public static class Bytes6Bean {
        @F_date_bytes_6(valueZoneId = "+8")
        public LocalDateTime value;
    }

    public static class Bytes6ArrayBean {
        @F_date_bytes_6(baseYear = 1900)
        public int[] value;
    }
}
