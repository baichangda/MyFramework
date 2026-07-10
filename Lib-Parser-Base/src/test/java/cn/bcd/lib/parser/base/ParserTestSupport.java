package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class ParserTestSupport {
    private ParserTestSupport() {
    }

    static byte[] readAll(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        return bytes;
    }

    static <T> byte[] deProcess(Processor<T> processor, T bean) {
        ByteBuf byteBuf = Unpooled.buffer();
        processor.deProcess(byteBuf, bean);
        return readAll(byteBuf);
    }

    static <T> T process(Processor<T> processor, byte... bytes) {
        return processor.process(Unpooled.wrappedBuffer(bytes));
    }

    static <T> T roundTrip(Processor<T> processor, T bean, byte... expectedBytes) {
        byte[] bytes = deProcess(processor, bean);
        assertArrayEquals(expectedBytes, bytes);
        return processor.process(Unpooled.wrappedBuffer(bytes));
    }
}
