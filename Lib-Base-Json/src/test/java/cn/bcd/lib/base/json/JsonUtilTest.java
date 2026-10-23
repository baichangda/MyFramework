package cn.bcd.lib.base.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilTest {

    @Test
    void nonEmptyShouldOmitNullAndEmptyByteArrays() {
        assertEquals("{}", JsonUtil.toJson(new NonEmptyBytes(null)));
        assertEquals("{}", JsonUtil.toJson(new NonEmptyBytes(new byte[0])));
    }

    @Test
    void nonEmptyShouldKeepNonEmptyByteArraysAsNumbers() {
        assertEquals("{\"data\":[-128,-1,0,1,127]}",
                JsonUtil.toJson(new NonEmptyBytes(new byte[]{-128, -1, 0, 1, 127})));
    }

    @Test
    void byteArraysShouldRoundTripThroughAllOutputMethods() {
        for (byte[] value : new byte[][]{new byte[0], allByteValues()}) {
            assertArrayEquals(value, JsonUtil.fromJson(JsonUtil.toJson(value), byte[].class));
            assertArrayEquals(value, JsonUtil.fromJson(JsonUtil.toJsonPretty(value), byte[].class));
            assertArrayEquals(value, JsonUtil.MAPPER.readValue(JsonUtil.toJsonAsBytes(value), byte[].class));
        }
    }

    private static byte[] allByteValues() {
        byte[] value = new byte[256];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) i;
        }
        return value;
    }

    public static class NonEmptyBytes {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public byte[] data;

        public NonEmptyBytes(byte[] data) {
            this.data = data;
        }
    }
}
