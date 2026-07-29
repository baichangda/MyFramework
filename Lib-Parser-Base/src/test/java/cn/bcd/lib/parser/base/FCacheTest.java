package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.F_cache;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FCacheTest {

    @Test
    void indexCacheSupportsFastPathAndExpansion() {
        ProcessContext context = new ProcessContext(Unpooled.buffer());
        Object first = new Object();
        Object expanded = new Object();

        context.putCache(0, first);
        context.putCache(8, expanded);

        assertSame(first, context.getCache(0));
        assertSame(expanded, context.getCache(8));
        assertNull(context.getCache(7));
    }

    @Test
    void keyCacheSupportsNullValues() {
        ProcessContext context = new ProcessContext(Unpooled.buffer());
        context.putCache("value", null);

        assertNull(context.getCache("value"));
        assertNull(context.getCache("missing"));
    }

    @Test
    void annotationCachesParsedAndDeParsedFieldValues() {
        Processor<CacheBean> processor = Parser.getProcessor(CacheBean.class);
        ProcessContext parseContext = new ProcessContext(Unpooled.wrappedBuffer(new byte[]{7, 'a', 'b', 'c'}));

        CacheBean parsed = processor.process(parseContext.byteBuf, parseContext);

        assertEquals(7, parsed.number);
        assertEquals("abc", parsed.text);
        assertEquals((short) 7, parseContext.getCache(0));
        assertEquals("abc", parseContext.getCache("text"));

        ProcessContext deParseContext = new ProcessContext(Unpooled.buffer());
        processor.deProcess(deParseContext.byteBuf, deParseContext, parsed);

        assertEquals((short) 7, deParseContext.getCache(0));
        assertEquals("abc", deParseContext.getCache("text"));
    }

    @Test
    void annotationRequiresExactlyOneSelectorAndAParserAnnotation() {
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(InvalidBothBean.class));
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(InvalidNeitherBean.class));
        assertThrows(RuntimeException.class, () -> Parser.getProcessor(CacheOnlyBean.class));
    }

    public static class CacheBean {
        @F_cache(index = 0)
        @F_num(type = NumType.uint8)
        public short number;

        @F_cache(key = "text")
        @F_string(len = 3)
        public String text;
    }

    public static class InvalidBothBean {
        @F_cache(index = 0, key = "value")
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class InvalidNeitherBean {
        @F_cache
        @F_num(type = NumType.uint8)
        public short value;
    }

    public static class CacheOnlyBean {
        @F_cache(index = 0)
        public short value;
    }
}
