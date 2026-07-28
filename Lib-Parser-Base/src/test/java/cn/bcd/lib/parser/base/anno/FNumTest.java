package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FNumTest {
    @Test
    public void parsesAndDeParsesPrimitiveNumericTypesAndByteOrder() {
        Processor<NumericBean> processor = Parser.getProcessor(NumericBean.class);
        NumericBean bean = new NumericBean();
        bean.u8 = 255;
        bean.le16 = 0x1234;
        bean.i24 = 0x010203;
        bean.u40 = 0x0102030405L;
        bean.f32 = 1.5f;
        bean.f64 = 2.25d;

        NumericBean target = processor.process(Unpooled.wrappedBuffer(ParserTestSupport.deProcess(processor, bean)));

        assertEquals(bean.u8, target.u8);
        assertEquals(bean.le16, target.le16);
        assertEquals(bean.i24, target.i24);
        assertEquals(bean.u40, target.u40);
        assertEquals(bean.f32, target.f32);
        assertEquals(bean.f64, target.f64);
    }

    @Test
    public void supportsValueExpressionPrecisionEnumAndVariables() {
        Processor<ExpressionBean> processor = Parser.getProcessor(ExpressionBean.class);
        ExpressionBean target = ParserTestSupport.process(processor, (byte) 11, (byte) 12, (byte) 2);

        assertEquals(1, target.value);
        assertEquals(1.2d, target.rounded);
        assertEquals(Mode.TWO, target.mode);

        ProcessContext context = new ProcessContext(Unpooled.wrappedBuffer(new byte[]{11, 12, 2}));
        processor.process(context.byteBuf, context);
        assertEquals(11, context.getGlobalVar("A"));
    }

    @Test
    public void usesNumericGlobalVariableInLengthExpression() {
        Processor<GlobalExpressionBean> processor = Parser.getProcessor(GlobalExpressionBean.class);
        GlobalExpressionBean target = ParserTestSupport.process(processor, (byte) 2, (byte) 7, (byte) 8);

        assertEquals(2, target.length);
        assertEquals(7, target.values[0]);
        assertEquals(8, target.values[1]);

        GlobalExpressionBean source = new GlobalExpressionBean();
        source.length = 2;
        source.values = new byte[]{7, 8};
        byte[] bytes = ParserTestSupport.deProcess(processor, source);
        assertEquals(3, bytes.length);
        assertEquals(2, bytes[0]);
        assertEquals(7, bytes[1]);
        assertEquals(8, bytes[2]);
    }

    public static class NumericBean {
        @F_num(type = NumType.uint8)
        public int u8;

        @F_num(type = NumType.uint16, order = ByteOrder.smallEndian)
        public int le16;

        @F_num(type = NumType.int24)
        public int i24;

        @F_num(type = NumType.uint40)
        public long u40;

        @F_num(type = NumType.float32)
        public float f32;

        @F_num(type = NumType.float64)
        public double f64;
    }

    public static class ExpressionBean {
        @F_num(type = NumType.uint8, valExpr = "x-10", var = 'a')
        @F_global_var(var = "A")
        public int value;

        @F_num(type = NumType.uint8, valExpr = "x/10", precision = 1)
        public double rounded;

        @F_num(type = NumType.uint8)
        public Mode mode;
    }

    public static class GlobalExpressionBean {
        @F_num(type = NumType.uint8)
        @F_global_var(var = "A")
        public int length;

        @F_num_array(singleType = NumType.uint8, lenExpr = "A")
        public byte[] values;
    }

    public enum Mode {
        ONE(1),
        TWO(2);

        private final int value;

        Mode(int value) {
            this.value = value;
        }

        public static Mode fromInteger(int i) {
            return i == 2 ? TWO : ONE;
        }

        public int toInteger() {
            return value;
        }
    }
}
