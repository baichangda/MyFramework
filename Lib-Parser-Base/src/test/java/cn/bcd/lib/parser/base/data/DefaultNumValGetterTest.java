package cn.bcd.lib.parser.base.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultNumValGetterTest {
    private final DefaultNumValGetter getter = DefaultNumValGetter.instance;

    @Test
    void recognizesIntSpecialValuesForEverySupportedWidth() {
        assertIntValues(8, 0xFF);
        assertIntValues(16, 0xFFFF);
        assertIntValues(24, 0xFFFFFF);
        assertIntValues(32, 0xFFFFFFFF);
    }

    @Test
    void recognizesLongSpecialValuesForEverySupportedWidth() {
        assertLongValues(40, 0xFFFFFFFFFFL);
        assertLongValues(48, 0xFFFFFFFFFFFFL);
        assertLongValues(56, 0xFFFFFFFFFFFFFFL);
        assertLongValues(64, 0xFFFFFFFFFFFFFFFFL);
    }

    @Test
    void recognizesSpecialValuesThroughFixedWidthMethods() {
        assertEquals(1, getter.getType8(0xFF));
        assertEquals(2, getter.getType8(0xFE));
        assertEquals(1, getter.getType16(0xFFFF));
        assertEquals(2, getter.getType16(0xFFFE));
        assertEquals(1, getter.getType24(0xFFFFFF));
        assertEquals(2, getter.getType24(0xFFFFFE));
        assertEquals(1, getter.getType32(0xFFFFFFFF));
        assertEquals(2, getter.getType32(0xFFFFFFFE));
        assertEquals(1, getter.getType40(0xFFFFFFFFFFL));
        assertEquals(2, getter.getType40(0xFFFFFFFFFEL));
        assertEquals(1, getter.getType48(0xFFFFFFFFFFFFL));
        assertEquals(2, getter.getType48(0xFFFFFFFFFFFEL));
        assertEquals(1, getter.getType56(0xFFFFFFFFFFFFFFL));
        assertEquals(2, getter.getType56(0xFFFFFFFFFFFFFEL));
        assertEquals(1, getter.getType64(0xFFFFFFFFFFFFFFFFL));
        assertEquals(2, getter.getType64(0xFFFFFFFFFFFFFFFEL));
    }

    @Test
    void normalizesValuesToTheDeclaredWidth() {
        assertEquals(1, getter.getType8(-1));
        assertEquals(2, getter.getType8(-2));
        assertEquals(1, getter.getType40(-1L));
        assertEquals(2, getter.getType40(-2L));
        assertEquals(1, getter.getType8(0x1FF));
        assertEquals(2, getter.getType8(0x1FE));
        assertEquals(1, getter.getType40(0x1FFFFFFFFFFL));
        assertEquals(2, getter.getType40(0x1FFFFFFFFFEL));
    }

    @Test
    void returnsZeroForUnknownValueTypes() {
        assertEquals(0, getter.getVal_int8((byte) 0));
        assertEquals(0, getter.getVal_int8((byte) 3));
        assertEquals(0, getter.getVal_long64((byte) 0));
        assertEquals(0, getter.getVal_long64((byte) 3));
    }

    private void assertIntValues(int width, int mask) {
        assertEquals(1, getFixedType(width, mask));
        assertEquals(2, getFixedType(width, mask - 1));
        assertEquals(0, getFixedType(width, mask - 2));
        assertEquals(mask, getFixedIntVal(width, (byte) 1));
        assertEquals(mask - 1, getFixedIntVal(width, (byte) 2));
    }

    private void assertLongValues(int width, long mask) {
        assertEquals(1, getFixedType(width, mask));
        assertEquals(2, getFixedType(width, mask - 1));
        assertEquals(0, getFixedType(width, mask - 2));
        assertEquals(mask, getFixedLongVal(width, (byte) 1));
        assertEquals(mask - 1, getFixedLongVal(width, (byte) 2));
    }

    private byte getFixedType(int width, int value) {
        return switch (width) {
            case 8 -> getter.getType8(value);
            case 16 -> getter.getType16(value);
            case 24 -> getter.getType24(value);
            case 32 -> getter.getType32(value);
            default -> throw new IllegalArgumentException("Unsupported width: " + width);
        };
    }

    private byte getFixedType(int width, long value) {
        return switch (width) {
            case 40 -> getter.getType40(value);
            case 48 -> getter.getType48(value);
            case 56 -> getter.getType56(value);
            case 64 -> getter.getType64(value);
            default -> throw new IllegalArgumentException("Unsupported width: " + width);
        };
    }

    private int getFixedIntVal(int width, byte type) {
        return switch (width) {
            case 8 -> getter.getVal_int8(type);
            case 16 -> getter.getVal_int16(type);
            case 24 -> getter.getVal_int24(type);
            case 32 -> getter.getVal_int32(type);
            default -> throw new IllegalArgumentException("Unsupported width: " + width);
        };
    }

    private long getFixedLongVal(int width, byte type) {
        return switch (width) {
            case 40 -> getter.getVal_long40(type);
            case 48 -> getter.getVal_long48(type);
            case 56 -> getter.getVal_long56(type);
            case 64 -> getter.getVal_long64(type);
            default -> throw new IllegalArgumentException("Unsupported width: " + width);
        };
    }
}
