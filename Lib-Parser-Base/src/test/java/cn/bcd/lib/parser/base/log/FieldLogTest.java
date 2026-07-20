package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.F_skip;
import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldLogTest {
    @Test
    void registersOneFieldLogForEveryParserAnnotation() {
        Parser.anno_fieldBuilder.keySet().forEach(annotationClass -> {
            FieldLog<?> fieldLog = FieldLogRegistry.get(annotationClass);
            assertNotNull(fieldLog, annotationClass.getName());
            assertEquals("FieldLog__" + annotationClass.getSimpleName(),
                    fieldLog.getClass().getSimpleName());
        });
        assertNotNull(FieldLogRegistry.get(F_skip.class));
    }

    @Test
    void checkedArrayWritesValueAndCompanionInOneParseLine() {
        List<String> messages = new ArrayList<>();
        Logger original = FieldLog__F_num_array.logger;
        FieldLog__F_num_array.logger = capturingLogger(messages);
        try {
            FieldLog__F_num_array.parse(CheckedArrayBean.class, "values",
                    new byte[]{1, (byte) 0xff, (byte) 0xfe},
                    new int[]{1, 0, 0}, new byte[]{0, 1, 2},
                    true, false);
        } finally {
            FieldLog__F_num_array.logger = original;
        }

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("val[1, 0, 0] values__v[0, 1, 2]"), messages.getFirst());
    }

    @Test
    void checkedArrayWritesValueAndCompanionInOneDeParseLine() {
        List<String> messages = new ArrayList<>();
        Logger original = FieldLog__F_num_array.logger;
        FieldLog__F_num_array.logger = capturingLogger(messages);
        try {
            FieldLog__F_num_array.deParse(CheckedArrayBean.class, "values",
                    new byte[]{1, (byte) 0xff, (byte) 0xfe},
                    new int[]{1, 0, 0}, new byte[]{0, 1, 2},
                    true, false);
        } finally {
            FieldLog__F_num_array.logger = original;
        }

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("val[1, 0, 0] values__v[0, 1, 2]"), messages.getFirst());
    }

    @Test
    void multipleBitOperationsWriteOneFieldLine() {
        List<String> messages = new ArrayList<>();
        Logger original = FieldLog__F_bit_num.logger;
        FieldLog__F_bit_num.logger = capturingLogger(messages);
        try {
            BitBuf_reader_log.SkipLog first = new BitBuf_reader_log.SkipLog(1, 0, 1);
            BitBuf_reader_log.SkipLog second = new BitBuf_reader_log.SkipLog(1, 1, 2);
            FieldLog__F_bit_num.parse(BitBean.class, "value", new byte[]{0},
                    0, false,
                    new BitBuf_reader_log.Log[]{first, second});
        } finally {
            FieldLog__F_bit_num.logger = original;
        }

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains(" | "), messages.getFirst());
    }

    @Test
    void fieldSkipWritesBeforeAndAfterBytesInOneLine() {
        List<String> messages = new ArrayList<>();
        Logger original = FieldLog__F_skip.logger;
        FieldLog__F_skip.logger = capturingLogger(messages);
        try {
            FieldLog__F_skip.parse(SkipBean.class, "value",
                    new byte[]{9, 1, 2, 8}, 1, 1);
        } finally {
            FieldLog__F_skip.logger = original;
        }

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("before[09] after[08]"), messages.getFirst());
    }

    @Test
    void resolvesSourceLocationAndFormatsValues() {
        LogUtil.class_fieldName_lineNo.clear();
        assertTrue(LogUtil.getFieldStackTrace(CheckedArrayBean.class, "values")
                .matches("\\(FieldLogTest\\.java:\\d+\\)"));
        assertEquals("[1]", LogUtil.formatLogValue((byte) 1));
        assertEquals("[0, 1, 2]", LogUtil.formatLogValue(new byte[]{0, 1, 2}));
        assertEquals("[null]", LogUtil.formatLogValue(null));
    }

    private static Logger capturingLogger(List<String> messages) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("info") && args != null && args.length == 2
                            && args[0] instanceof String pattern && args[1] instanceof Object[] values) {
                        messages.add(MessageFormatter.arrayFormat(pattern, values).getMessage());
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return true;
                    }
                    return null;
                });
    }

    public static class CheckedArrayBean {
        @F_num_array(len = 3, singleType = cn.bcd.lib.parser.base.data.NumType.uint8,
                singleCheckVal = true)
        public int[] values;
        public byte[] values__v;
    }

    public static class BitBean {
        @cn.bcd.lib.parser.base.anno.F_bit_num(len = 3)
        public int value;
    }

    public static class SkipBean {
        @cn.bcd.lib.parser.base.anno.F_num(type = cn.bcd.lib.parser.base.data.NumType.uint16)
        @F_skip(lenBefore = 1, lenAfter = 1)
        public int value;
    }
}
