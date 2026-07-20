package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.DateTsMode;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FFieldSkipTest {
    @Test
    public void skipsByteFieldsWithFixedAndExpressionLengths() {
        Processor<ByteSkipBean> processor = Parser.getProcessor(ByteSkipBean.class);
        byte[] input = new byte[35];
        input[0] = 2;
        Arrays.fill(input, 1, input.length - 1, (byte) 0x7f);
        input[input.length - 1] = 99;

        ByteSkipBean target = processor.process(Unpooled.wrappedBuffer(input));

        assertEquals(2, target.length);
        assertEquals(0, target.number);
        assertNull(target.numbers);
        assertNull(target.string);
        assertNull(target.bcdString);
        assertEquals(0, target.dateBcd);
        assertEquals(0, target.dateBytes6);
        assertEquals(0, target.dateBytes7);
        assertEquals(0, target.dateTs);
        assertEquals(99, target.tail);

        ByteSkipBean source = new ByteSkipBean();
        source.length = 2;
        source.number = 123;
        source.numbers = new int[]{1, 2};
        source.string = "ab";
        source.bcdString = "12";
        source.dateBcd = 1;
        source.dateBytes6 = 1;
        source.dateBytes7 = 1;
        source.dateTs = 1;
        source.tail = 99;
        byte[] output = ParserTestSupport.deProcess(processor, source);

        byte[] expected = new byte[35];
        expected[0] = 2;
        expected[expected.length - 1] = 99;
        assertArrayEquals(expected, output);
    }

    @Test
    public void skipsRegularArrayAndEasyBitFields() {
        Processor<BitSkipBean> processor = Parser.getProcessor(BitSkipBean.class);
        byte[] input = {(byte) 0xe5, (byte) 0xff, (byte) 0xa5, 0x66};

        BitSkipBean target = processor.process(Unpooled.wrappedBuffer(input));

        assertEquals(0, target.skippedBits);
        assertEquals(5, target.bits);
        assertNull(target.skippedArray);
        assertEquals(0, target.skippedEasyBits);
        assertEquals(5, target.easyBits);
        assertEquals(0x66, target.tail);

        BitSkipBean source = new BitSkipBean();
        source.skippedBits = 7;
        source.bits = 5;
        source.skippedArray = new int[]{7, 7};
        source.skippedEasyBits = 10;
        source.easyBits = 5;
        source.tail = 0x66;
        assertArrayEquals(new byte[]{0x05, 0x00, 0x05, 0x66}, ParserTestSupport.deProcess(processor, source));
    }

    @Test
    public void rejectsVariablesOnSkippedFields() {
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(InvalidSkippedVariableBean.class));
    }

    public static class ByteSkipBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int length;

        @F_num(type = NumType.uint16, skip = true)
        public int number;

        @F_num_array(lenExpr = "a", singleType = NumType.uint8, singleSkip = 1, skip = true)
        public int[] numbers;

        @F_string(lenExpr = "a", skip = true)
        public String string;

        @F_string_bcd(len = 2, skip = true)
        public String bcdString;

        @F_date_bcd(skip = true)
        public long dateBcd;

        @F_date_bytes_6(skip = true)
        public long dateBytes6;

        @F_date_bytes_7(skip = true)
        public long dateBytes7;

        @F_date_ts(mode = DateTsMode.uint32_s, skip = true)
        public long dateTs;

        @F_num(type = NumType.uint8)
        public int tail;
    }

    public static class BitSkipBean {
        @F_bit_num(len = 3, skip = true)
        public int skippedBits;

        @F_bit_num(len = 5)
        public int bits;

        @F_bit_num_array(len = 2, singleLen = 3, singleSkip = 1, skip = true)
        public int[] skippedArray;

        @F_bit_num_easy(bitStart = 7, bitEnd = 4, skip = true)
        public int skippedEasyBits;

        @F_bit_num_easy(bitStart = 3, bitEnd = 0, end = true)
        public int easyBits;

        @F_num(type = NumType.uint8)
        public int tail;
    }

    public static class InvalidSkippedVariableBean {
        @F_num(type = NumType.uint8, skip = true, var = 'a')
        public int value;
    }
}
