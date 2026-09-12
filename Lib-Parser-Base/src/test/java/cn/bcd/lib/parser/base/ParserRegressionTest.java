package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParserRegressionTest {
    private static <T> T roundTrip(Processor<T> processor, byte[] source) {
        ByteBuf input = Unpooled.wrappedBuffer(source);
        ByteBuf output = Unpooled.buffer();
        try {
            T bean = processor.process(input);
            assertEquals(source.length, input.readerIndex());
            processor.deProcess(output, bean);
            assertArrayEquals(source, ParserTestSupport.readAll(output));
            return bean;
        } finally {
            input.release();
            output.release();
        }
    }

    @Test
    void uint64ArraysRoundTripWithBothByteOrders() {
        byte[] source = {1, 2, 3, 4, 5, 6, 7, 8, -1, -1, -1, -1, -1, -1, -1, -1};
        U64 big = roundTrip(Parser.getProcessor(U64.class, ByteOrder.bigEndian, null), source);
        U64 little = roundTrip(Parser.getProcessor(U64.class, ByteOrder.smallEndian, null), source);
        assertArrayEquals(new long[]{0x0102030405060708L, -1L}, big.values);
        assertArrayEquals(new long[]{0x0807060504030201L, -1L}, little.values);
    }

    @Test
    void twoStringsCanUseTheSameGlobalLength() {
        TwoStrings bean = roundTrip(Parser.getProcessor(TwoStrings.class), new byte[]{2, 65, 66, 67, 68});
        assertEquals("AB", bean.first);
        assertEquals("CD", bean.second);
    }

    @Test
    void laterFieldsObserveUpdatedGlobalVariables() {
        Updated bean = roundTrip(Parser.getProcessor(Updated.class), new byte[]{1, 10, 2, 20, 21});
        assertArrayEquals(new byte[]{20, 21}, bean.second);
    }

    @Test
    void emptyBitArrayPreservesPaddingAndFinishesPreviousBits() {
        Bits bean = roundTrip(Parser.getProcessor(Bits.class), new byte[]{0, (byte) 0x80, 0, 7});
        assertNull(bean.values);
        assertEquals(1, bean.flag);
        assertEquals(7, bean.tail);
    }

    @Test
    void interfaceClassPaddingUsesActualSize() {
        Envelope bean = roundTrip(Parser.getProcessor(Envelope.class), new byte[]{9, 0, 0, 0});
        assertEquals(9, ((ItemImpl) bean.item).value);
    }

    @Test
    void regressionsAlsoWorkWithJdkCompiler() {
        cn.bcd.lib.parser.base.complier.DynamicProcessorCompiler.setCompiler(
                new cn.bcd.lib.parser.base.complier.JdkDynamicProcessorCompiler());
        Parser.clearProcessorCache();
        try {
            uint64ArraysRoundTripWithBothByteOrders();
            twoStringsCanUseTheSameGlobalLength();
            laterFieldsObserveUpdatedGlobalVariables();
            emptyBitArrayPreservesPaddingAndFinishesPreviousBits();
            interfaceClassPaddingUsesActualSize();
        } finally {
            cn.bcd.lib.parser.base.complier.DynamicProcessorCompiler.setCompiler(
                    new cn.bcd.lib.parser.base.complier.JavassistDynamicProcessorCompiler());
            Parser.clearProcessorCache();
        }
    }

    public static class U64 {
        @F_num_array(singleType = NumType.uint64, len = 2)
        public long[] values;
    }

    public static class TwoStrings {
        @F_num(type = NumType.uint8, globalVar = 'A')
        public int len;
        @F_string(lenExpr = "A")
        public String first;
        @F_string(lenExpr = "A")
        public String second;
    }

    public static class Updated {
        @F_num(type = NumType.uint8, globalVar = 'A')
        public int len;
        @F_num_array(singleType = NumType.uint8, lenExpr = "A")
        public byte[] first;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public int len2;
        @F_num_array(singleType = NumType.uint8, lenExpr = "A")
        public byte[] second;
    }

    public static class Bits {
        @F_num(type = NumType.uint8, var = 'a')
        public int count;
        @F_bit_num(len = 1)
        public int flag;
        @F_bit_num_array(singleLen = 1, lenExpr = "a", skipBefore = 3, skipAfter = 5)
        public int[] values;
        @F_num(type = NumType.uint8)
        public int tail;
    }

    public interface Item {
    }

    @C_impl(value = 1)
    public static class ItemImpl implements Item {
        @F_num(type = NumType.uint8)
        public int value;
    }

    @C_skip(len = 4)
    public static class Envelope {
        @F_bean(implClassExpr = "1")
        public Item item;
    }
}
