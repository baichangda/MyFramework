package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bcd;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DateTsMode;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class DateBuildersRegressionTest {
    @Test
    public void sixByteYearUsesUnsignedOffsetForTemporalAndArrayFields() {
        var temporal = Parser.getProcessor(SixTemporal.class);
        var array = Parser.getProcessor(SixArray.class);
        for (int offset : new int[]{0, 127, 128, 255}) {
            byte[] bytes = {(byte) offset, 1, 1, 0, 0, 0};
            SixTemporal bean = ParserTestSupport.process(temporal, bytes);
            assertEquals(2000 + offset, bean.value.getYear());
            assertArrayEquals(bytes, ParserTestSupport.deProcess(temporal, bean));
            SixArray raw = ParserTestSupport.process(array, bytes);
            assertEquals(bean.value.getYear(), raw.value[0]);
            assertArrayEquals(bytes, ParserTestSupport.deProcess(array, raw));
            long millis = FieldBuilder__F_date_bytes_6.read(Unpooled.wrappedBuffer(bytes), ZoneId.of("UTC"), 2000);
            assertEquals(bean.value.toInstant(ZoneOffset.UTC).toEpochMilli(), millis);
        }
    }

    @Test
    public void floatingSecondsPreserveMillisecondsInBothByteOrders() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            var processor = Parser.getProcessor(FloatingSeconds.class, order, DefaultNumValGetter.instance);
            for (long millis : new long[]{0, 1, -1, 1001, -1001, 1700000000001L, 1700000000123L}) {
                FloatingSeconds bean = new FloatingSeconds();
                bean.value = millis;
                byte[] bytes = ParserTestSupport.deProcess(processor, bean);
                assertEquals(millis, ParserTestSupport.process(processor, bytes).value);
            }
        }
    }

    @Test
    public void bcdRejectsInvalidDigitsInEveryDateComponent() {
        for (int index = 0; index < 6; index++) {
            for (int invalid : new int[]{0x0A, 0xA0}) {
                byte[] bytes = {0x25, 1, 1, 0, 0, 0};
                bytes[index] = (byte) invalid;
                assertThrows(IllegalArgumentException.class,
                        () -> FieldBuilder__F_date_bcd.read(Unpooled.wrappedBuffer(bytes), ZoneOffset.UTC, 2000));
                assertThrows(IllegalArgumentException.class,
                        () -> FieldBuilder__F_date_bcd.read(Unpooled.wrappedBuffer(bytes), ZoneId.of("UTC"), 2000));
            }
        }
    }

    @Test
    public void bcdAndSixByteWritersRejectOutOfRangeYearBeforeWriting() {
        for (ZoneId zone : new ZoneId[]{ZoneOffset.UTC, ZoneId.of("UTC")}) {
            for (int year : new int[]{2000, 2099}) {
                ByteBuf output = Unpooled.buffer();
                long millis = atStartOfYear(year);
                FieldBuilder__F_date_bcd.write(output, millis, zone, 2000);
                assertEquals(millis, FieldBuilder__F_date_bcd.read(output, zone, 2000));
            }
            for (int year : new int[]{1999, 2100, 2160}) {
                ByteBuf output = Unpooled.buffer();
                assertThrows(IllegalArgumentException.class,
                        () -> FieldBuilder__F_date_bcd.write(output, atStartOfYear(year), zone, 2000));
                assertEquals(0, output.writerIndex());
            }
            for (int year : new int[]{1999, 2256}) {
                ByteBuf output = Unpooled.buffer();
                assertThrows(IllegalArgumentException.class,
                        () -> FieldBuilder__F_date_bytes_6.write(output, atStartOfYear(year), zone, 2000));
                assertEquals(0, output.writerIndex());
            }
        }
        ByteBuf output = Unpooled.buffer();
        var processor = Parser.getProcessor(SixArray.class);
        for (int year : new int[]{1999, 2256}) {
            SixArray bean = new SixArray();
            bean.value = new int[]{year, 1, 1, 0, 0, 0};
            assertThrows(IllegalArgumentException.class, () -> processor.deProcess(output, bean));
            assertEquals(0, output.writerIndex());
        }
    }

    @Test
    public void sevenByteYearSupportsUnsignedShortRangeAndRejectsOverflow() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.bigEndian, ByteOrder.smallEndian}) {
            var temporal = Parser.getProcessor(SevenTemporal.class, order, DefaultNumValGetter.instance);
            var array = Parser.getProcessor(SevenArray.class, order, DefaultNumValGetter.instance);
            for (int year : new int[]{0, 32768, 65535}) {
                SevenTemporal bean = new SevenTemporal();
                bean.value = LocalDateTime.of(year, 1, 1, 0, 0);
                byte[] bytes = ParserTestSupport.deProcess(temporal, bean);
                assertEquals(year, ParserTestSupport.process(temporal, bytes).value.getYear());
                ByteBuf encoded = Unpooled.wrappedBuffer(bytes);
                assertEquals(year, order == ByteOrder.bigEndian ? encoded.readUnsignedShort() : encoded.readUnsignedShortLE());
                SevenArray raw = ParserTestSupport.process(array, bytes);
                assertEquals(year, raw.value[0]);
                assertArrayEquals(bytes, ParserTestSupport.deProcess(array, raw));
            }
            for (int year : new int[]{-1, 65536}) {
                SevenTemporal bean = new SevenTemporal();
                bean.value = LocalDateTime.of(year, 1, 1, 0, 0);
                SevenArray raw = new SevenArray();
                raw.value = new int[]{year, 1, 1, 0, 0, 0};
                ByteBuf output = Unpooled.buffer();
                assertThrows(IllegalArgumentException.class, () -> temporal.deProcess(output, bean));
                assertEquals(0, output.writerIndex());
                assertThrows(IllegalArgumentException.class, () -> array.deProcess(output, raw));
                assertEquals(0, output.writerIndex());
            }
        }
    }

    @Test
    public void allDateBuildersSupportQuotesAndBackslashesInStringFormats() {
        var processor = Parser.getProcessor(QuotedFormats.class);
        QuotedFormats bean = new QuotedFormats();
        String text = "2025\"\\0601123045";
        bean.bcd = text;
        bean.six = text;
        bean.seven = text;
        bean.timestamp = text;
        QuotedFormats decoded = ParserTestSupport.process(processor, ParserTestSupport.deProcess(processor, bean));
        assertEquals(text, decoded.bcd);
        assertEquals(text, decoded.six);
        assertEquals(text, decoded.seven);
        assertEquals(text, decoded.timestamp);
    }

    @Test
    public void allDateAnnotationsRejectUnsupportedDateSubclassesDuringValidation() {
        for (Class<?> model : new Class<?>[]{SqlBcd.class, SqlSix.class, SqlSeven.class, SqlTimestamp.class}) {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> Parser.getProcessor(model));
            boolean found = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains("does not support field type[java.sql.")) {
                    found = true;
                }
            }
            assertTrue(found, model.getName());
        }
    }

    @Test
    public void stringLiteralEscapesJavaControlCharacters() {
        assertEquals("\"\\\"\\\\\\b\\t\\n\\f\\r\"", ParseUtil.javaStringLiteral("\"\\\b\t\n\f\r"));
    }

    private static long atStartOfYear(int year) {
        return LocalDateTime.of(year, 1, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static class SixTemporal {
        @F_date_bytes_6(zoneId = "+0", valueZoneId = "+0")
        public LocalDateTime value;
    }
    public static class SixArray {
        @F_date_bytes_6
        public int[] value;
    }
    public static class SevenTemporal {
        @F_date_bytes_7(zoneId = "+0", valueZoneId = "+0")
        public LocalDateTime value;
    }
    public static class SevenArray {
        @F_date_bytes_7
        public int[] value;
    }
    public static class FloatingSeconds {
        @F_date_ts(mode = DateTsMode.float64_s)
        public long value;
    }
    public static class QuotedFormats {
        @F_date_bcd(zoneId = "+0", valueZoneId = "+0", stringFormat = "yyyy'\"\\'MMddHHmmss")
        public String bcd;
        @F_date_bytes_6(zoneId = "+0", valueZoneId = "+0", stringFormat = "yyyy'\"\\'MMddHHmmss")
        public String six;
        @F_date_bytes_7(zoneId = "+0", valueZoneId = "+0", stringFormat = "yyyy'\"\\'MMddHHmmss")
        public String seven;
        @F_date_ts(mode = DateTsMode.uint64_ms, valueZoneId = "+0", stringFormat = "yyyy'\"\\'MMddHHmmss")
        public String timestamp;
    }
    public static class SqlBcd {
        @F_date_bcd
        public java.sql.Date value;
    }
    public static class SqlSix {
        @F_date_bytes_6
        public java.sql.Date value;
    }
    public static class SqlSeven {
        @F_date_bytes_7
        public java.sql.Timestamp value;
    }
    public static class SqlTimestamp {
        @F_date_ts(mode = DateTsMode.uint64_ms)
        public java.sql.Timestamp value;
    }
}
