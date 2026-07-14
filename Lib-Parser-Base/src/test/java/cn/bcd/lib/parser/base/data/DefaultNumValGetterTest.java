package cn.bcd.lib.parser.base.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultNumValGetterTest {
    private final DefaultNumValGetter getter = DefaultNumValGetter.instance;

    @Test
    void recognizesIntSpecialValuesForEverySupportedWidth() {
        assertIntValues(NumType.uint8, 0xFF);
        assertIntValues(NumType.int8, 0xFF);
        assertIntValues(NumType.uint16, 0xFFFF);
        assertIntValues(NumType.int16, 0xFFFF);
        assertIntValues(NumType.uint24, 0xFFFFFF);
        assertIntValues(NumType.int24, 0xFFFFFF);
        assertIntValues(NumType.uint32, 0xFFFFFFFF);
        assertIntValues(NumType.int32, 0xFFFFFFFF);
    }

    @Test
    void recognizesLongSpecialValuesForEverySupportedWidth() {
        assertLongValues(NumType.uint40, 0xFFFFFFFFFFL);
        assertLongValues(NumType.int40, 0xFFFFFFFFFFL);
        assertLongValues(NumType.uint48, 0xFFFFFFFFFFFFL);
        assertLongValues(NumType.int48, 0xFFFFFFFFFFFFL);
        assertLongValues(NumType.uint56, 0xFFFFFFFFFFFFFFL);
        assertLongValues(NumType.int56, 0xFFFFFFFFFFFFFFL);
        assertLongValues(NumType.uint64, 0xFFFFFFFFFFFFFFFFL);
        assertLongValues(NumType.int64, 0xFFFFFFFFFFFFFFFFL);
    }

    @Test
    void onlyChecksBitsWithinTheDeclaredWidth() {
        assertEquals(1, getter.getType(NumType.uint8, 0x1FF));
        assertEquals(2, getter.getType(NumType.uint8, 0x1FE));
        assertEquals(1, getter.getType(NumType.uint40, 0x1FFFFFFFFFFL));
        assertEquals(2, getter.getType(NumType.uint40, 0x1FFFFFFFFFEL));
    }

    @Test
    void returnsZeroForUnsupportedTypesAndValueKinds() {
        assertEquals(0, getter.getType(NumType.uint40, 0xFFFFFFFF));
        assertEquals(0, getter.getType(NumType.uint32, 0xFFFFFFFFL));
        assertEquals(0, getter.getType(NumType.float32, 0xFFFFFFFF));
        assertEquals(0, getter.getType(NumType.float64, 0xFFFFFFFFFFFFFFFFL));

        assertEquals(0, getter.getVal_int(NumType.uint40, (byte) 1));
        assertEquals(0, getter.getVal_long(NumType.uint32, (byte) 1));
        assertEquals(0, getter.getVal_int(NumType.uint8, (byte) 0));
        assertEquals(0, getter.getVal_int(NumType.uint8, (byte) 3));
        assertEquals(0, getter.getVal_long(NumType.uint64, (byte) 0));
        assertEquals(0, getter.getVal_long(NumType.uint64, (byte) 3));
    }

    private void assertIntValues(NumType numType, int mask) {
        assertEquals(1, getter.getType(numType, mask));
        assertEquals(2, getter.getType(numType, mask - 1));
        assertEquals(0, getter.getType(numType, mask - 2));
        assertEquals(mask, getter.getVal_int(numType, (byte) 1));
        assertEquals(mask - 1, getter.getVal_int(numType, (byte) 2));
    }

    private void assertLongValues(NumType numType, long mask) {
        assertEquals(1, getter.getType(numType, mask));
        assertEquals(2, getter.getType(numType, mask - 1));
        assertEquals(0, getter.getType(numType, mask - 2));
        assertEquals(mask, getter.getVal_long(numType, (byte) 1));
        assertEquals(mask - 1, getter.getVal_long(numType, (byte) 2));
    }
}
