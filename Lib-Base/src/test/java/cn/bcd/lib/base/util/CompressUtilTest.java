package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressUtilTest {

    @Test
    void unGzipWithSplitterEmitsFinalSegmentWithoutTrailingDelimiter() {
        byte[] compressed = CompressUtil.gzip("first\nsecond".getBytes(StandardCharsets.UTF_8));
        List<String> values = new ArrayList<>();

        CompressUtil.unGzip(new ByteArrayInputStream(compressed), '\n', bytes -> {
            values.add(new String(bytes, StandardCharsets.UTF_8));
            return true;
        });

        assertEquals(List.of("first", "second"), values);
    }
}
