package cn.bcd.lib.parser.base;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.F_cache;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FCacheKeyValidationTest {
    @Test
    void rejectsSpecialCharactersBeforeGeneratingSource() {
        for (Class<?> model : new Class<?>[]{QuoteKey.class, BackslashKey.class,
                WhitespaceKey.class, NewlineKey.class, PunctuationKey.class, NonAsciiKey.class}) {
            BaseException exception = assertThrows(BaseException.class, () -> Parser.getProcessor(model));
            String message = exception.getRealException().getMessage();
            assertTrue(message.contains("F_cache key must contain only ASCII letters, digits or underscores"));
            assertTrue(message.contains("class[" + model.getName() + "]"));
            assertTrue(message.contains("field[value]"));
        }
    }

    @Test
    void acceptsLettersDigitsAndUnderscoresInBothDirections() {
        Processor<ValidKey> processor = Parser.getProcessor(ValidKey.class);
        ByteBuf input = Unpooled.wrappedBuffer(new byte[]{7});
        ByteBuf output = Unpooled.buffer();
        try {
            ProcessContext parseContext = new ProcessContext(input);
            ValidKey parsed = processor.process(input, parseContext);
            assertEquals((short) 7, parseContext.getCache("09_aAzZ"));

            ProcessContext deParseContext = new ProcessContext(output);
            processor.deProcess(output, deParseContext, parsed);
            assertEquals((short) 7, deParseContext.getCache("09_aAzZ"));
            assertEquals(1, output.readableBytes());
            assertEquals(7, output.readUnsignedByte());
        } finally {
            input.release();
            output.release();
        }
    }

    public static class ValidKey {
        @F_cache(key = "09_aAzZ")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class QuoteKey {
        @F_cache(key = "a\"b")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class BackslashKey {
        @F_cache(key = "a\\nb")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class WhitespaceKey {
        @F_cache(key = "a b")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class NewlineKey {
        @F_cache(key = "a\nb")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class PunctuationKey {
        @F_cache(key = "a;b")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class NonAsciiKey {
        @F_cache(key = "缓存")
        @F_num(type = NumType.uint8)
        public short value;
    }
}
