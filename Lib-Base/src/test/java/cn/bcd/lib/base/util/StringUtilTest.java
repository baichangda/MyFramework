package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {
    /** 首尾及连续分隔符均被移除，首个保留字符仍为小写。 */
    @Test
    void leadingTrailingAndRepeatedDelimitersProduceLowerCamelCase() {
        assertEquals("fooBar", StringUtil.splitCharToCamelCase("_foo_bar", '_'));
        assertEquals("fooBar", StringUtil.splitCharToCamelCase("__FOO__BAR__", '_'));
        assertEquals("foo", StringUtil.splitCharToCamelCase("_FOO_", '_'));
        assertEquals("", StringUtil.splitCharToCamelCase("___", '_'));
        assertEquals("", StringUtil.splitCharToCamelCase("_", '_'));
        assertEquals("fooBar", StringUtil.splitCharToCamelCase("-foo--bar-", '-'));
    }

    /** 保留普通命名转换和空值处理行为。 */
    @Test
    void ordinaryConversionsAndEmptyInputsStillWork() {
        assertNull(StringUtil.splitCharToCamelCase(null, '_'));
        assertNull(StringUtil.camelCaseToSplitChar(null, '_'));
        assertEquals("", StringUtil.splitCharToCamelCase("", '_'));
        assertEquals("", StringUtil.camelCaseToSplitChar("", '_'));
        assertEquals("aBC", StringUtil.splitCharToCamelCase("a_b_c", '_'));
        assertEquals("fooBar", StringUtil.splitCharToCamelCase("FOO_BAR", '_'));
        assertEquals("foo", StringUtil.splitCharToCamelCase("FOO", '_'));
        assertEquals("user_name", StringUtil.camelCaseToSplitChar("userName", '_'));
    }

    /** 无论选择哪个重载，异常对象都参与占位符替换。 */
    @Test
    void throwableParametersAreConsistentAcrossOverloads() {
        Object exception = new IllegalStateException("boom");
        assertEquals(exception.toString(), StringUtil.format("{}", exception));
        assertEquals(StringUtil.format("{}", new Object[]{exception}), StringUtil.format("{}", exception));
        assertEquals("prefix " + exception, StringUtil.format("{} {}", "prefix", exception));
        assertEquals(StringUtil.format("{} {}", new Object[]{"prefix", exception}),
                StringUtil.format("{} {}", "prefix", exception));
        assertEquals("1 2 " + exception, StringUtil.format("{} {} {}", 1, 2, exception));
    }

    /** 格式化仍支持空值、普通参数及占位符转义。 */
    @Test
    void formattingPreservesNullsEscapesAndOrdinaryParameters() {
        assertEquals("null", StringUtil.format("{}", (Object) null));
        assertEquals("1-null", StringUtil.format("{}-{}", 1, null));
        assertNull(StringUtil.format(null, "value"));
        assertNull(StringUtil.format(null, "first", "second"));
        assertNull(StringUtil.format(null, new Object[]{1, 2, 3}));
        assertEquals("1-2-3", StringUtil.format("{}-{}-{}", 1, 2, 3));
        assertEquals("1-{}-2", StringUtil.format("{}-\\{}-{}", 1, 2));
        assertEquals("no placeholder", StringUtil.format("no placeholder", new IllegalStateException("boom")));
        assertEquals("first {}", StringUtil.format("{} {}", "first"));
    }
}
