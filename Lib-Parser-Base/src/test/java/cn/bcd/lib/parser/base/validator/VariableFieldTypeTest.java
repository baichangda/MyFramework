package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.*;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class VariableFieldTypeTest {
    static Stream<Field> fields() {
        return Stream.of(NumFields.class, BitFields.class, EasyFields.class, CustomFields.class)
                .flatMap(type -> Arrays.stream(type.getFields()));
    }

    @TestFactory
    Stream<DynamicTest> variableFieldTypes() {
        return fields().map(field -> DynamicTest.dynamicTest(field.toString(),
                () -> allowsOnlyIntegerPrimitivesAsVariables(field)));
    }

    private static void allowsOnlyIntegerPrimitivesAsVariables(Field field) {
        boolean allowed = field.getName().endsWith("Plain")
                || field.getType() == byte.class || field.getType() == short.class
                || field.getType() == int.class || field.getType() == long.class;
        if (allowed) {
            assertDoesNotThrow(() -> validate(field));
        } else {
            RuntimeException exception = assertThrows(RuntimeException.class, () -> validate(field));
            assertTrue(exception.getMessage().contains("var/globalVar requires byte, short, int or long"),
                    exception.getMessage());
        }
    }

    private static void validate(Field field) {
        if (field.isAnnotationPresent(F_num.class)) {
            FieldValidator__F_num.validate(field, field.getAnnotation(F_num.class), DefaultNumValGetter.instance);
        }
        if (field.isAnnotationPresent(F_bit_num.class)) {
            FieldValidator__F_bit_num.validate(field, field.getAnnotation(F_bit_num.class));
        }
        if (field.isAnnotationPresent(F_bit_num_easy.class)) {
            FieldValidator__F_bit_num_easy.validate(field, field.getAnnotation(F_bit_num_easy.class));
        }
        if (field.isAnnotationPresent(F_customize.class)) {
            FieldValidator__F_customize.validate(field, field.getAnnotation(F_customize.class));
        }
    }

    public static class NumFields {
        @F_num(type = NumType.uint8, var = 'a')
        public byte byteLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public byte byteGlobal;
        @F_num(type = NumType.uint8)
        public byte bytePlain;
        @F_num(type = NumType.uint8, var = 'a')
        public short shortLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public short shortGlobal;
        @F_num(type = NumType.uint8)
        public short shortPlain;
        @F_num(type = NumType.uint8, var = 'a')
        public int intLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public int intGlobal;
        @F_num(type = NumType.uint8)
        public int intPlain;
        @F_num(type = NumType.uint8, var = 'a')
        public long longLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public long longGlobal;
        @F_num(type = NumType.uint8)
        public long longPlain;
        @F_num(type = NumType.uint8, var = 'a')
        public float floatLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public float floatGlobal;
        @F_num(type = NumType.uint8)
        public float floatPlain;
        @F_num(type = NumType.uint8, var = 'a')
        public double doubleLocal;
        @F_num(type = NumType.uint8, globalVar = 'A')
        public double doubleGlobal;
        @F_num(type = NumType.uint8)
        public double doublePlain;
    }

    public static class BitFields {
        @F_bit_num(len = 8, var = 'a')
        public byte byteLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public byte byteGlobal;
        @F_bit_num(len = 8)
        public byte bytePlain;
        @F_bit_num(len = 8, var = 'a')
        public short shortLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public short shortGlobal;
        @F_bit_num(len = 8)
        public short shortPlain;
        @F_bit_num(len = 8, var = 'a')
        public int intLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public int intGlobal;
        @F_bit_num(len = 8)
        public int intPlain;
        @F_bit_num(len = 8, var = 'a')
        public long longLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public long longGlobal;
        @F_bit_num(len = 8)
        public long longPlain;
        @F_bit_num(len = 8, var = 'a')
        public float floatLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public float floatGlobal;
        @F_bit_num(len = 8)
        public float floatPlain;
        @F_bit_num(len = 8, var = 'a')
        public double doubleLocal;
        @F_bit_num(len = 8, globalVar = 'A')
        public double doubleGlobal;
        @F_bit_num(len = 8)
        public double doublePlain;
    }

    public static class EasyFields {
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public byte byteLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public byte byteGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public byte bytePlain;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public short shortLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public short shortGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public short shortPlain;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public int intLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public int intGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public int intPlain;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public long longLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public long longGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public long longPlain;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public float floatLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public float floatGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public float floatPlain;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, var = 'a')
        public double doubleLocal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0, globalVar = 'A')
        public double doubleGlobal;
        @F_bit_num_easy(bitStart = 7, bitEnd = 0)
        public double doublePlain;
    }

    public static class CustomFields {
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public byte byteLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public byte byteGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public byte bytePlain;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public short shortLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public short shortGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public short shortPlain;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public int intLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public int intGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public int intPlain;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public long longLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public long longGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public long longPlain;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public float floatLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public float floatGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public float floatPlain;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", var = 'a')
        public double doubleLocal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0", globalVar = 'A')
        public double doubleGlobal;
        @F_customize(processorClass = FCustomizeTest.OffsetProcessor.class, processorArgs = "0")
        public double doublePlain;
    }
}
