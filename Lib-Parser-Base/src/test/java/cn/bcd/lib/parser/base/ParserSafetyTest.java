package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class ParserSafetyTest {
    @Test
    void acceptsNullNumValGetterWhenValueCheckingIsUnused() {
        Processor<SimpleBean> processor = Parser.getProcessor(SimpleBean.class, ByteOrder.Default, null);
        SimpleBean bean = ParserTestSupport.process(processor, (byte) 7);
        assertEquals(7, bean.value);
    }

    @Test
    void rejectsNullNumValGetterWhenValueCheckingIsEnabled() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(CheckedBean.class, ByteOrder.Default, null));
        assertTrue(hasMessage(exception, "requires NumValGetter"));
    }

    @Test
    void rejectsValueCheckingForFloatingPointTypes() {
        assertUnsupportedValueChecking(Float32CheckedBean.class, "float32");
        assertUnsupportedValueChecking(Float64CheckedBean.class, "float64");
        assertUnsupportedValueChecking(Float32ArrayCheckedBean.class, "float32");
        assertUnsupportedValueChecking(Float64ArrayCheckedBean.class, "float64");
    }

    private void assertUnsupportedValueChecking(Class<?> modelClass, String type) {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> Parser.getProcessor(modelClass));
        assertTrue(hasMessage(exception, "does not support value checking"));
        assertTrue(hasMessage(exception, type));
    }

    @Test
    void compilesFixedWidthGetterCallsForEveryWidth() {
        assertDoesNotThrow(() -> Parser.getProcessor(AllWidthsCheckedBean.class));
    }

    @Test
    void returnsOneProcessorDuringConcurrentLookup() throws Exception {
        Parser.clearProcessorCache();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Processor<ConcurrentBean>>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                tasks.add(() -> Parser.getProcessor(ConcurrentBean.class));
            }
            List<Processor<ConcurrentBean>> processors = executor.invokeAll(tasks).stream()
                    .map(future -> assertDoesNotThrow((ThrowingSupplier<Processor<ConcurrentBean>>) future::get))
                    .toList();
            Processor<ConcurrentBean> first = processors.getFirst();
            assertTrue(processors.stream().allMatch(processor -> processor == first));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidModelsBeforeCompilation() {
        RuntimeException fieldException = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(PrivateFieldBean.class));
        assertTrue(hasMessage(fieldException, "must be public"));

        RuntimeException annotationException = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(MultipleAnnotationsBean.class));
        assertTrue(hasMessage(annotationException, "exactly one"));

        RuntimeException skipException = assertThrows(RuntimeException.class,
                () -> Parser.getProcessor(InvalidSkipBean.class));
        assertTrue(hasMessage(skipException, "mutually exclusive"));
    }

    @Test
    void reportsMissingAndInvalidGlobalVariables() {
        ProcessContext context = new ProcessContext(Unpooled.buffer());
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> context.getGlobalVar(0));
        assertTrue(missing.getMessage().contains("A"));
        assertThrows(IllegalArgumentException.class, () -> context.putGlobalVar(26, 1));
    }

    @Test
    void freezesConfigurationAfterFirstProcessorLookup() {
        Parser.getProcessor(SimpleBean.class);
        assertTrue(Parser.isConfigurationFrozen());
        assertThrows(IllegalStateException.class, Parser::enablePrintBuildLog);
        Parser.clearProcessorCache();
        assertTrue(Parser.isConfigurationFrozen());
    }

    private static boolean hasMessage(Throwable throwable, String expected) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    public static class SimpleBean {
        @F_num(type = NumType.uint8)
        public int value;
    }

    public static class CheckedBean {
        @F_num(type = NumType.uint8, checkVal = true)
        public int value;
        public byte value__v;
    }

    public static class Float32CheckedBean {
        @F_num(type = NumType.float32, checkVal = true)
        public float value;
        public byte value__v;
    }

    public static class Float64CheckedBean {
        @F_num(type = NumType.float64, checkVal = true)
        public double value;
        public byte value__v;
    }

    public static class Float32ArrayCheckedBean {
        @F_num_array(singleType = NumType.float32, len = 1, singleCheckVal = true)
        public float[] values;
        public byte[] values__v;
    }

    public static class Float64ArrayCheckedBean {
        @F_num_array(singleType = NumType.float64, len = 1, singleCheckVal = true)
        public double[] values;
        public byte[] values__v;
    }

    public static class AllWidthsCheckedBean {
        @F_num(type = NumType.uint8, checkVal = true)
        public int value8;
        public byte value8__v;

        @F_num(type = NumType.uint16, checkVal = true)
        public int value16;
        public byte value16__v;

        @F_num(type = NumType.uint24, checkVal = true)
        public int value24;
        public byte value24__v;

        @F_num(type = NumType.uint32, checkVal = true)
        public long value32;
        public byte value32__v;

        @F_num(type = NumType.uint40, checkVal = true)
        public long value40;
        public byte value40__v;

        @F_num(type = NumType.uint48, checkVal = true)
        public long value48;
        public byte value48__v;

        @F_num(type = NumType.uint56, checkVal = true)
        public long value56;
        public byte value56__v;

        @F_num(type = NumType.uint64, checkVal = true)
        public long value64;
        public byte value64__v;

        @F_num_array(singleType = NumType.uint16, len = 1, singleCheckVal = true)
        public int[] values;
        public byte[] values__v;
    }

    public static class ConcurrentBean {
        @F_num(type = NumType.uint8)
        public int value;
    }

    public static class PrivateFieldBean {
        @F_num(type = NumType.uint8)
        private int value;
    }

    public static class MultipleAnnotationsBean {
        @F_num(type = NumType.uint8)
        @cn.bcd.lib.parser.base.anno.F_string(len = 1)
        public int value;
    }

    @C_skip(len = 2, lenExpr = "a")
    public static class InvalidSkipBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int value;
    }
}
