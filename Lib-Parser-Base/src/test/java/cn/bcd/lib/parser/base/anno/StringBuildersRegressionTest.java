package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string_bcd;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.StringAppendMode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class StringBuildersRegressionTest {
    @Test
    public void nullAndEmptyPaddedFieldsPreserveWidthAndFollowingFields() {
        var processor = Parser.getProcessor(Padded.class);
        Padded bean = new Padded();
        bean.length = 2;
        bean.tail = 7;
        byte[] expected = {2, 0, 0, 0, 0, 0, 0, 0, 0, 7};
        assertArrayEquals(expected, ParserTestSupport.deProcess(processor, bean));
        Padded decoded = ParserTestSupport.process(processor, expected);
        assertEquals("", decoded.low);
        assertEquals("", decoded.high);
        assertEquals("", decoded.bcdLow);
        assertEquals("", decoded.bcdHigh);
        assertEquals(7, decoded.tail);
        bean.low = bean.high = bean.bcdLow = bean.bcdHigh = "";
        assertArrayEquals(expected, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void noAppendNullUsesTheSameLengthRulesAsEmptyString() {
        assertThrows(RuntimeException.class, () -> ParserTestSupport.deProcess(
                Parser.getProcessor(Plain.class), new Plain()));
        assertThrows(RuntimeException.class, () -> ParserTestSupport.deProcess(
                Parser.getProcessor(PlainBcd.class), new PlainBcd()));
        var processor = Parser.getProcessor(ZeroLength.class);
        ZeroLength bean = new ZeroLength();
        bean.tail = 7;
        assertArrayEquals(new byte[]{0, 7}, ParserTestSupport.deProcess(processor, bean));
        ZeroLength decoded = ParserTestSupport.process(processor, (byte) 0, (byte) 7);
        assertEquals("", decoded.text);
        assertEquals("", decoded.bcd);
        assertEquals(7, decoded.tail);
    }

    @Test
    public void paddedReadersPreserveMultibyteCharacters() {
        for (String name : new String[]{"UTF-8", "GBK", "US-ASCII"}) {
            Charset charset = Charset.forName(name);
            for (String text : new String[]{"A", "中", "Ā", "A中", "😀", ""}) {
                if (!charset.newEncoder().canEncode(text)) {
                    continue;
                }
                ByteBuf low = Unpooled.buffer().writeByte(99);
                FieldBuilder__F_string.write_lowAddressAppend(low, text, 16, charset);
                low.writeByte(7).readerIndex(1);
                assertEquals(text, FieldBuilder__F_string.read_lowAddressAppend(low, 16, charset), name);
                assertEquals(17, low.readerIndex());
                assertEquals(7, low.readByte());

                ByteBuf high = Unpooled.buffer().writeByte(99);
                FieldBuilder__F_string.write_highAddressAppend(high, text, 16, charset);
                high.writeByte(7).readerIndex(1);
                assertEquals(text, FieldBuilder__F_string.read_highAddressAppend(high, 16, charset), name);
                assertEquals(17, high.readerIndex());
                assertEquals(7, high.readByte());
            }
        }
    }

    @Test
    public void bcdReadersRejectEveryInvalidNibbleAndAcceptAllValidBytes() {
        List<BiFunction<ByteBuf, Integer, String>> readers = List.of(
                FieldBuilder__F_string_bcd::read_noAppend,
                FieldBuilder__F_string_bcd::read_lowAddressAppend,
                FieldBuilder__F_string_bcd::read_highAddressAppend);
        for (int value = 0; value < 256; value++) {
            byte[] bytes = {(byte) value};
            boolean valid = (value >>> 4) <= 9 && (value & 15) <= 9;
            for (var reader : readers) {
                if (valid) {
                    assertDoesNotThrow(() -> reader.apply(Unpooled.wrappedBuffer(bytes), 1));
                } else {
                    RuntimeException failure = assertThrows(RuntimeException.class,
                            () -> reader.apply(Unpooled.wrappedBuffer(bytes), 1));
                    assertTrue(failure.getMessage().contains("invalid BCD byte"));
                }
            }
        }
    }

    @Test
    public void oddBcdStringsKeepExistingPaddingAndNullHelpersAreConsistent() {
        for (StringAppendMode mode : StringAppendMode.values()) {
            ByteBuf output = Unpooled.buffer();
            writeBcd(mode, output, "123", 2);
            assertArrayEquals(mode == StringAppendMode.highAddressAppend
                    ? new byte[]{0x12, 0x30} : new byte[]{0x01, 0x23}, ParserTestSupport.readAll(output));
        }
        ByteBuf output = Unpooled.buffer();
        FieldBuilder__F_string.write_lowAddressAppend(output, null, 2, StandardCharsets.UTF_8);
        FieldBuilder__F_string_bcd.write_highAddressAppend(output, null, 2);
        assertArrayEquals(new byte[4], ParserTestSupport.readAll(output));
    }

    private static void writeBcd(StringAppendMode mode, ByteBuf output, String text, int len) {
        switch (mode) {
            case noAppend -> FieldBuilder__F_string_bcd.write_noAppend(output, text, len);
            case lowAddressAppend -> FieldBuilder__F_string_bcd.write_lowAddressAppend(output, text, len);
            case highAddressAppend -> FieldBuilder__F_string_bcd.write_highAddressAppend(output, text, len);
        }
    }

    public static class Padded {
        @F_num(type = NumType.uint8, var = 'a')
        public int length;
        @F_string(lenExpr = "a", appendMode = StringAppendMode.lowAddressAppend)
        public String low;
        @F_string(len = 2, appendMode = StringAppendMode.highAddressAppend)
        public String high;
        @F_string_bcd(len = 2, appendMode = StringAppendMode.lowAddressAppend)
        public String bcdLow;
        @F_string_bcd(lenExpr = "a", appendMode = StringAppendMode.highAddressAppend)
        public String bcdHigh;
        @F_num(type = NumType.uint8)
        public int tail;
    }
    public static class Plain {
        @F_string(len = 2)
        public String value;
    }
    public static class PlainBcd {
        @F_string_bcd(len = 2)
        public String value;
    }
    public static class ZeroLength {
        @F_num(type = NumType.uint8, var = 'a')
        public int length;
        @F_string(lenExpr = "a")
        public String text;
        @F_string_bcd(lenExpr = "a")
        public String bcd;
        @F_num(type = NumType.uint8)
        public int tail;
    }
}
