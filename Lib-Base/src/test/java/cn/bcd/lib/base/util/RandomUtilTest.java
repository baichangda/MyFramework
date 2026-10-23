package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class RandomUtilTest {
    private static final List<IntFunction<String>> GENERATORS = List.of(
            RandomUtil::randomString_alphabeticUppercase,
            RandomUtil::randomString_alphabeticLowercase,
            RandomUtil::randomString_alphabetic,
            RandomUtil::randomString_numeric,
            RandomUtil::randomString_alphanumeric,
            n -> RandomUtil.random("abc123", n)
    );

    /** 所有生成入口均拒绝负长度，包括最小整数边界。 */
    @Test
    void negativeLengthsAreRejected() {
        for (IntFunction<String> generator : GENERATORS) {
            assertThrows(IllegalArgumentException.class, () -> generator.apply(-1));
            assertThrows(IllegalArgumentException.class, () -> generator.apply(Integer.MIN_VALUE));
        }
    }

    /** 长度为零仍返回空字符串。 */
    @Test
    void zeroLengthReturnsEmptyString() {
        for (IntFunction<String> generator : GENERATORS) {
            assertEquals("", generator.apply(0));
        }
    }

    /** 正长度结果保持指定长度和字符范围。 */
    @Test
    void positiveLengthsPreserveCharacterPools() {
        assertTrue(RandomUtil.randomString_alphabeticUppercase(64).matches("[A-Z]{64}"));
        assertTrue(RandomUtil.randomString_alphabeticLowercase(64).matches("[a-z]{64}"));
        assertTrue(RandomUtil.randomString_alphabetic(64).matches("[A-Za-z]{64}"));
        assertTrue(RandomUtil.randomString_numeric(64).matches("[0-9]{64}"));
        assertTrue(RandomUtil.randomString_alphanumeric(64).matches("[A-Za-z0-9]{64}"));
        assertEquals("aaaa", RandomUtil.random("a", 4));
    }
}
