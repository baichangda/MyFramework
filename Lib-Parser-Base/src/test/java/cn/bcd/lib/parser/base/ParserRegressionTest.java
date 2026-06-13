package cn.bcd.lib.parser.base;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.anno.F_bit_num_easy;
import cn.bcd.lib.parser.base.anno.F_customize;
import cn.bcd.lib.parser.base.anno.F_date_ts;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.anno.F_string_bcd;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string_bcd;
import cn.bcd.lib.parser.base.data.DateTsMode;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.log.LogCollector_deParse;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParserRegressionTest {

    @Test
    public void bitNumEasySupportsDeParseAndReportsCorrectLength() {
        Processor<BitEasyBean> processor = Parser.getProcessor(BitEasyBean.class);
        BitEasyBean source = new BitEasyBean();
        source.flag = 1;
        source.type = 5;
        source.value = 0x155;

        ByteBuf byteBuf = Unpooled.buffer();
        processor.deProcess(byteBuf, source);

        assertEquals(2, byteBuf.readableBytes());
        BitEasyBean target = processor.process(byteBuf);
        assertEquals(source.flag, target.flag);
        assertEquals(source.type, target.type);
        assertEquals(source.value, target.value);
        assertEquals(2, ParseUtil.getClassByteLenIfPossible(BitEasyBean.class));
        assertEquals(1, ParseUtil.getClassByteLenIfPossible(SingleByteBitEasyBean.class));
    }

    @Test
    public void bitNumArrayAllowsNullDuringDeParse() {
        Processor<BitArrayBean> processor = Parser.getProcessor(BitArrayBean.class);
        ByteBuf byteBuf = Unpooled.buffer();

        processor.deProcess(byteBuf, new BitArrayBean());

        assertEquals(0, byteBuf.readableBytes());
    }

    @Test
    public void deParseBitLoggingUsesDeParseCollector() {
        LogCollector_deParse previous = Parser.logCollector_deParse;
        try {
            Parser.logCollector_deParse = NoopDeParseLogCollector.INSTANCE;
            Processor<LoggedBitBean> processor = Parser.getProcessor(LoggedBitBean.class);
            LoggedBitBean bean = new LoggedBitBean();
            bean.value = 5;

            ByteBuf byteBuf = Unpooled.buffer();
            processor.deProcess(byteBuf, bean);

            assertArrayEquals(new byte[]{(byte) 0xA0}, readAll(byteBuf));
        } finally {
            Parser.logCollector_deParse = previous;
        }
    }

    @Test
    public void customizeGlobalVarDoesNotRequireLocalVar() {
        Processor<CustomGlobalBean> processor = Parser.getProcessor(CustomGlobalBean.class);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[]{7});
        ProcessContext context = new ProcessContext(byteBuf);

        CustomGlobalBean bean = processor.process(byteBuf, context);

        assertEquals(7, bean.value);
        assertEquals(7, context.getGlobalVar(0));
    }

    @Test
    public void dynamicClassSkipUsesDeParseVariables() {
        Processor<DynamicSkipBean> processor = Parser.getProcessor(DynamicSkipBean.class);
        DynamicSkipBean bean = new DynamicSkipBean();
        bean.len = 4;
        bean.value = 9;
        ByteBuf byteBuf = Unpooled.buffer();

        processor.deProcess(byteBuf, bean);

        assertArrayEquals(new byte[]{4, 9, 0, 0}, readAll(byteBuf));
    }

    @Test
    public void fixedStringsRejectEncodedLengthMismatch() {
        Processor<FixedStringBean> processor = Parser.getProcessor(FixedStringBean.class);
        FixedStringBean bean = new FixedStringBean();

        assertThrows(RuntimeException.class, () -> processor.deProcess(Unpooled.buffer(), bean));

        bean.value = "A";

        assertThrows(RuntimeException.class, () -> processor.deProcess(Unpooled.buffer(), bean));

        bean.value = "AB";
        ByteBuf byteBuf = Unpooled.buffer();
        processor.deProcess(byteBuf, bean);
        assertArrayEquals(new byte[]{'A', 'B'}, readAll(byteBuf));
    }

    @Test
    public void fixedBcdStringsRejectEncodedLengthMismatch() {
        Processor<FixedBcdBean> processor = Parser.getProcessor(FixedBcdBean.class);
        FixedBcdBean bean = new FixedBcdBean();

        assertThrows(RuntimeException.class, () -> processor.deProcess(Unpooled.buffer(), bean));

        bean.value = "123";
        assertThrows(RuntimeException.class, () -> processor.deProcess(Unpooled.buffer(), bean));

        bean.value = "1234";
        ByteBuf byteBuf = Unpooled.buffer();
        processor.deProcess(byteBuf, bean);
        assertArrayEquals(new byte[]{0x12, 0x34}, readAll(byteBuf));
    }

    @Test
    public void paddedEmptyStringsDecodeAsEmpty() {
        assertEquals("", FieldBuilder__F_string.read_lowAddressAppend(
                Unpooled.wrappedBuffer(new byte[3]), 3, StandardCharsets.UTF_8));
        assertEquals("", FieldBuilder__F_string.read_highAddressAppend(
                Unpooled.wrappedBuffer(new byte[3]), 3, StandardCharsets.UTF_8));
        assertEquals("", FieldBuilder__F_string_bcd.read_lowAddressAppend(
                Unpooled.wrappedBuffer(new byte[3]), 3));
        assertEquals("", FieldBuilder__F_string_bcd.read_highAddressAppend(
                Unpooled.wrappedBuffer(new byte[3]), 3));
    }

    @Test
    public void localDateTimeSupportsRegionZoneId() {
        Processor<RegionDateBean> processor = Parser.getProcessor(RegionDateBean.class);
        RegionDateBean source = new RegionDateBean();
        source.value = LocalDateTime.of(2025, 6, 1, 12, 30);
        ByteBuf byteBuf = Unpooled.buffer();

        processor.deProcess(byteBuf, source);
        RegionDateBean target = processor.process(byteBuf);

        assertEquals(source.value, target.value);
    }

    private static byte[] readAll(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        return bytes;
    }

    public static class BitEasyBean {
        @F_bit_num_easy(bitStart = 15, bitEnd = 15)
        public int flag;

        @F_bit_num_easy(bitStart = 14, bitEnd = 10)
        public int type;

        @F_bit_num_easy(bitStart = 9, bitEnd = 0)
        public int value;
    }

    public static class SingleByteBitEasyBean {
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public int value;
    }

    public static class BitArrayBean {
        @F_bit_num_array(len = 2, singleLen = 4)
        public int[] values;
    }

    public static class LoggedBitBean {
        @F_bit_num(len = 3)
        public int value;
    }

    public static class CustomGlobalBean {
        @F_customize(processorClass = UnsignedByteProcessor.class, globalVar = 'A')
        public int value;
    }

    public static class UnsignedByteProcessor implements Processor<Integer> {
        @Override
        public Integer process(ByteBuf data, ProcessContext processContext) {
            return (int) data.readUnsignedByte();
        }

        @Override
        public void deProcess(ByteBuf data, ProcessContext processContext, Integer instance) {
            data.writeByte(instance);
        }
    }

    @C_skip(lenExpr = "a")
    public static class DynamicSkipBean {
        @F_num(type = NumType.uint8, var = 'a')
        public int len;

        @F_num(type = NumType.uint8)
        public int value;
    }

    public static class FixedStringBean {
        @F_string(len = 2)
        public String value;
    }

    public static class FixedBcdBean {
        @F_string_bcd(len = 2)
        public String value;
    }

    public static class RegionDateBean {
        @F_date_ts(mode = DateTsMode.uint32_s, valueZoneId = "Asia/Shanghai")
        public LocalDateTime value;
    }

    private enum NoopDeParseLogCollector implements LogCollector_deParse {
        INSTANCE;

        @Override
        public void collect_class(Class<?> clazz, int type, Object... args) {
        }

        @Override
        public void collect_field(Class<?> clazz, String fieldName, int type, Object... args) {
        }
    }
}
