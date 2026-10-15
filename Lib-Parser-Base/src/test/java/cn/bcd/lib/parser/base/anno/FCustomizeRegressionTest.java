package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.validator.FieldValidator__F_customize;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class FCustomizeRegressionTest {
    @Test
    void registersVariablesForFollowingFieldsInBothDirections() {
        Processor<VariableBean> processor = Parser.getProcessor(VariableBean.class);
        byte[] bytes = {3, 'a', 'b', 'c', 'd', 'e', 'f'};
        ProcessContext read = new ProcessContext(Unpooled.wrappedBuffer(bytes));
        VariableBean bean = processor.process(read.byteBuf, read);
        assertEquals("abc", bean.local);
        assertEquals("def", bean.global);
        assertEquals(3, read.getGlobalVar('A'));
        ByteBuf output = Unpooled.buffer();
        ProcessContext write = new ProcessContext(output);
        processor.deProcess(output, write, bean);
        byte[] actual = new byte[output.readableBytes()];
        output.readBytes(actual);
        assertArrayEquals(bytes, actual);
        assertEquals(3, write.getGlobalVar('A'));
    }

    @Test
    void supportsBooleanAndChar() {
        Processor<PrimitiveBean> processor = Parser.getProcessor(PrimitiveBean.class);
        PrimitiveBean bean = processor.process(Unpooled.wrappedBuffer(new byte[]{1, 0x4e, 0x2d}));
        assertTrue(bean.flag);
        assertEquals('中', bean.character);
        assertArrayEquals(new byte[]{1, 0x4e, 0x2d}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    void supportsArrayFields() {
        Processor<ArrayBean> processor = Parser.getProcessor(ArrayBean.class);
        byte[] bytes = {2, 4};
        ArrayBean bean = processor.process(Unpooled.wrappedBuffer(bytes));
        assertArrayEquals(bytes, bean.value);
        assertArrayEquals(bytes, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    void supportsInheritedGenericNumericReturnType() {
        Processor<GenericBean> processor = Parser.getProcessor(GenericBean.class);
        GenericBean bean = processor.process(Unpooled.wrappedBuffer(new byte[]{7}));
        assertEquals(7, bean.value);
        assertArrayEquals(new byte[]{7}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    void supportsInheritedGenericReferenceReturnTypeAndNull() {
        Processor<ReferenceBean> processor = Parser.getProcessor(ReferenceBean.class);
        ReferenceBean bean = processor.process(Unpooled.wrappedBuffer(new byte[]{8}));
        assertEquals("8", bean.value);
        assertArrayEquals(new byte[]{8}, ParserTestSupport.deProcess(processor, bean));
        bean.value = null;
        assertArrayEquals(new byte[]{0}, ParserTestSupport.deProcess(processor, bean));
        assertNull(processor.process(Unpooled.wrappedBuffer(new byte[]{0})).value);
    }

    @Test
    void rejectsClassesThatDoNotImplementProcessor() throws Exception {
        assertInvalid("duck", "Processor");
        assertInvalid("abstractProcessor", "Processor");
        assertInvalid("processorInterface", "Processor");
    }

    @Test
    void rejectsNonNumericVariables() throws Exception {
        assertInvalid("local", "var/globalVar");
        assertInvalid("global", "var/globalVar");
        assertInvalid("flag", "var/globalVar");
    }

    private static void assertInvalid(String name, String message) throws Exception {
        Field field = InvalidBean.class.getField(name);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> FieldValidator__F_customize.validate(field, field.getAnnotation(F_customize.class)));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    public static class VariableBean {
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a', globalVar = 'A')
        public int length;
        @F_string(lenExpr = "a")
        public String local;
        @F_string(lenExpr = "A")
        public String global;
    }

    public static class PrimitiveBean {
        @F_customize(processorClass = BooleanProcessor.class)
        public boolean flag;
        @F_customize(processorClass = CharProcessor.class)
        public char character;
    }

    public static class ArrayBean {
        @F_customize(processorClass = ArrayProcessor.class)
        public byte[] value;
    }

    public static class GenericBean {
        @F_customize(processorClass = GenericIntProcessor.class)
        public int value;
    }

    public static class ReferenceBean {
        @F_customize(processorClass = GenericStringProcessor.class)
        public String value;
    }

    public static class InvalidBean {
        @F_customize(processorClass = DuckProcessor.class)
        public int duck;
        @F_customize(processorClass = GenericProcessor.class)
        public int abstractProcessor;
        @F_customize(processorClass = Processor.class)
        public int processorInterface;
        @F_customize(processorClass = GenericStringProcessor.class, var = 'a')
        public String local;
        @F_customize(processorClass = GenericStringProcessor.class, globalVar = 'A')
        public String global;
        @F_customize(processorClass = BooleanProcessor.class, var = 'a')
        public boolean flag;
    }

    public static class BooleanProcessor implements Processor<Boolean> {
        public Boolean process(ByteBuf data, ProcessContext context) { return data.readBoolean(); }
        public void deProcess(ByteBuf data, ProcessContext context, Boolean value) { data.writeBoolean(value); }
    }

    public static class CharProcessor implements Processor<Character> {
        public Character process(ByteBuf data, ProcessContext context) { return data.readChar(); }
        public void deProcess(ByteBuf data, ProcessContext context, Character value) { data.writeChar(value); }
    }

    public static class ArrayProcessor implements Processor<byte[]> {
        public byte[] process(ByteBuf data, ProcessContext context) {
            byte[] value = new byte[2];
            data.readBytes(value);
            return value;
        }
        public void deProcess(ByteBuf data, ProcessContext context, byte[] value) { data.writeBytes(value); }
    }

    public abstract static class GenericProcessor<T> implements Processor<T> {
        protected abstract T decode(int value);
        protected abstract int encode(T value);
        public T process(ByteBuf data, ProcessContext context) { return decode(data.readUnsignedByte()); }
        public void deProcess(ByteBuf data, ProcessContext context, T value) { data.writeByte(encode(value)); }
    }

    public static class GenericIntProcessor extends GenericProcessor<Integer> {
        protected Integer decode(int value) { return value; }
        protected int encode(Integer value) { return value; }
    }

    public static class GenericStringProcessor extends GenericProcessor<String> {
        protected String decode(int value) { return value == 0 ? null : String.valueOf(value); }
        protected int encode(String value) { return value == null ? 0 : Integer.parseInt(value); }
    }

    public static class DuckProcessor {
        public Integer process(ByteBuf data, ProcessContext context) { return (int) data.readUnsignedByte(); }
        public void deProcess(ByteBuf data, ProcessContext context, Integer value) { data.writeByte(value); }
    }
}
