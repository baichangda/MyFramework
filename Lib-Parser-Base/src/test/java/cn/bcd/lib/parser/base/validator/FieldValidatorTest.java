package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.anno.F_bit_num_easy;
import cn.bcd.lib.parser.base.anno.F_date_bcd;
import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_date_bytes_7;
import cn.bcd.lib.parser.base.anno.F_date_ts;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_num_array;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.data.DateTsMode;
import cn.bcd.lib.parser.base.data.NumType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldValidatorTest {
    @Test
    void rejectsUnsupportedFieldTypesBeforeBuildingProcessorSource() {
        assertFailure(InvalidStringField.class, "does not support field type");
        assertFailure(InvalidNumericArrayField.class, "numeric primitive or enum array");
        assertFailure(InvalidDateField.class, "does not support field type");
        assertFailure(IntDateBcdField.class, "does not support field type[int]");
        assertFailure(IntDateBytes6Field.class, "does not support field type[int]");
        assertFailure(IntDateBytes7Field.class, "does not support field type[int]");
        assertFailure(IntDateTsField.class, "does not support field type[int]");
    }

    @Test
    void rejectsInvalidVariablesAndCompanionFields() {
        assertFailure(InvalidVariableField.class, "must be in [a-z]");
        assertFailure(MissingCompanionField.class, "requires companion field");
        assertFailure(InvalidCompanionField.class, "must have type[byte]");
    }

    @Test
    void rejectsUnsupportedValueExpressionVariables() {
        assertFailure(InvalidNumValueExpression.class, "only supports variable[x]");
        assertFailure(InvalidNumArrayValueExpression.class, "only supports variable[x]");
        assertFailure(InvalidBitNumValueExpression.class, "only supports variable[x]");
        assertFailure(InvalidBitNumArrayValueExpression.class, "only supports variable[x]");
        assertFailure(InvalidBitNumEasyValueExpression.class, "only supports variable[x]");
    }

    @Test
    void rejectsNonConcreteBeanListElementTypes() {
        assertFailure(RawBeanList.class, "concrete List element type");
        assertFailure(WildcardBeanList.class, "concrete List element type");
        assertFailure(InterfaceBeanList.class, "must be a concrete class");
        assertFailure(AbstractBeanList.class, "must be a concrete class");
    }

    private static void assertFailure(Class<?> modelClass, String expectedMessage) {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> Parser.getProcessor(modelClass));
        assertTrue(hasMessage(exception, expectedMessage));
    }

    private static boolean hasMessage(Throwable throwable, String expected) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    public static class InvalidStringField {
        @F_string(len = 1)
        public int value;
    }

    public static class InvalidNumericArrayField {
        @F_num_array(len = 1, singleType = NumType.uint8)
        public String[] values;
    }

    public static class InvalidDateField {
        @F_date_ts(mode = DateTsMode.uint32_s)
        public boolean value;
    }

    public static class IntDateBcdField {
        @F_date_bcd
        public int value;
    }

    public static class IntDateBytes6Field {
        @F_date_bytes_6
        public int value;
    }

    public static class IntDateBytes7Field {
        @F_date_bytes_7
        public int value;
    }

    public static class IntDateTsField {
        @F_date_ts(mode = DateTsMode.uint32_s)
        public int value;
    }

    public static class InvalidVariableField {
        @F_num(type = NumType.uint8, var = 'A')
        public int value;
    }

    public static class InvalidNumValueExpression {
        @F_num(type = NumType.uint8, valExpr = "y + 1")
        public int value;
    }

    public static class InvalidNumArrayValueExpression {
        @F_num_array(len = 1, singleType = NumType.uint8, singleValExpr = "y + 1")
        public int[] values;
    }

    public static class InvalidBitNumValueExpression {
        @F_bit_num(len = 8, valExpr = "y + 1")
        public int value;
    }

    public static class InvalidBitNumArrayValueExpression {
        @F_bit_num_array(len = 1, singleLen = 8, singleValExpr = "y + 1")
        public int[] values;
    }

    public static class InvalidBitNumEasyValueExpression {
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, valExpr = "y + 1")
        public int value;
    }

    public interface ChildContract {
    }

    public abstract static class AbstractChild {
    }

    public static class RawBeanList {
        @F_bean_list(listLen = 1)
        public List values;
    }

    public static class WildcardBeanList {
        @F_bean_list(listLen = 1)
        public List<?> values;
    }

    public static class InterfaceBeanList {
        @F_bean_list(listLen = 1)
        public List<ChildContract> values;
    }

    public static class AbstractBeanList {
        @F_bean_list(listLen = 1)
        public List<AbstractChild> values;
    }

    public static class MissingCompanionField {
        @F_num(type = NumType.uint8, checkVal = true)
        public int value;
    }

    public static class InvalidCompanionField {
        @F_num(type = NumType.uint8, checkVal = true)
        public int value;
        public int value__v;
    }
}
