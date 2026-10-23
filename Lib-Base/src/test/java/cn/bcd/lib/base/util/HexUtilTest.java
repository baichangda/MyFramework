package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class HexUtilTest {
    /** 下标相加溢出或范围越界时，在分配输出空间前拒绝参数。 */
    @Test
    void invalidAndOverflowingRangesAreRejected() {
        byte[] input = {0, 1};
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.hexDump(input, Integer.MAX_VALUE, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.hexDump(input, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.hexDump(input, 1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.hexDump(input, 0, Integer.MAX_VALUE / 2));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.decodeHexDump("0011", Integer.MAX_VALUE, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.decodeHexDump("0011", -1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.decodeHexDump("0011", 3, 2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> HexUtil.decodeHexDump("0011", 0, Integer.MAX_VALUE - 1));
    }

    /** 空范围只允许位于输入内部或末尾，不能绕过边界校验。 */
    @Test
    void emptyRangesAreValidated() {
        byte[] input = {1, 2};
        for (int offset : new int[]{-1, 3, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.hexDump(input, offset, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> HexUtil.decodeHexDump("00", offset, 0));
        }
        for (int offset : new int[]{0, 1, 2}) {
            assertEquals("", HexUtil.hexDump(input, offset, 0));
            assertArrayEquals(new byte[0], HexUtil.decodeHexDump("00", offset, 0));
        }
        assertEquals("", HexUtil.hexDump(new byte[0]));
        assertArrayEquals(new byte[0], HexUtil.decodeHexDump(""));
    }

    /** 负长度、输出字符数溢出及奇数个十六进制字符均被拒绝。 */
    @Test
    void invalidLengthsAreRejected() {
        for (int length : new int[]{-1, -2, Integer.MIN_VALUE, 1, 3}) {
            assertThrows(IllegalArgumentException.class, () -> HexUtil.decodeHexDump("0011", 0, length));
        }
    }

    /** 覆盖全部字节值、大小写解码和指定范围的转换。 */
    @Test
    void allByteValuesAndSlicesRoundTrip() {
        byte[] input = new byte[256];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        String expected = HexFormat.of().formatHex(input);
        assertEquals(expected, HexUtil.hexDump(input));
        assertArrayEquals(input, HexUtil.decodeHexDump(expected));
        assertArrayEquals(input, HexUtil.decodeHexDump(expected.toUpperCase(Locale.ROOT)));
        assertEquals("0102", HexUtil.hexDump(new byte[]{0, 1, 2, 3}, 1, 2));
        assertArrayEquals(new byte[]{1, 2}, HexUtil.decodeHexDump("ff0102ff", 2, 4));
    }

    /** 整数按大端字节顺序输出，负数保留补码位模式。 */
    @Test
    void primitiveValuesPreserveTheirBitPatterns() {
        assertEquals("ff", HexUtil.hexDump((byte) -1));
        assertEquals("ffff", HexUtil.hexDump((short) -1));
        assertEquals("80000000", HexUtil.hexDump(Integer.MIN_VALUE));
        assertEquals("8000000000000000", HexUtil.hexDump(Long.MIN_VALUE));
        assertEquals("12345678", HexUtil.hexDump(0x12345678));
        assertEquals("0123456789abcdef", HexUtil.hexDump(0x0123456789abcdefL));
    }

    /** 仍拒绝非法字符，包括全角字符和代理项。 */
    @Test
    void invalidHexCharactersAreRejected() {
        for (String input : new String[]{"0g", " 0", "\uff10\uff11", "\ud83d\ude00"}) {
            assertThrows(IllegalArgumentException.class, () -> HexUtil.decodeHexDump(input));
        }
    }
}
