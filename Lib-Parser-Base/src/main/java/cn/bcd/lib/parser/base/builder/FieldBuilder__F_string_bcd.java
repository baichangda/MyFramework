package cn.bcd.lib.parser.base.builder;


import cn.bcd.lib.parser.base.anno.F_string_bcd;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.util.BcdUtil;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.reflect.Field;

public class FieldBuilder__F_string_bcd extends FieldBuilder {
    @Override
    public void buildParse(BuilderContext context) {
        final StringBuilder body = context.method_body;
        final Field field = context.field;
        final Class<?> fieldType = field.getType();
        final F_string_bcd anno = field.getAnnotation(F_string_bcd.class);
        if (anno.skip()) {
            ParseUtil.appendSkip_parse(anno.len(), anno.lenExpr(), context);
            return;
        }
        final String lenRes;
        if (anno.len() == 0) {
            lenRes = ParseUtil.replaceExprToCode(anno.lenExpr(), context);
        } else {
            lenRes = anno.len() + "";
        }

        switch (anno.appendMode()) {
            case noAppend -> {
                ParseUtil.append(body, "{}.{}={}.read_noAppend({},{});\n", varNameInstance, field.getName(), FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, lenRes);
            }
            case lowAddressAppend -> {
                ParseUtil.append(body, "{}.{}={}.read_lowAddressAppend({},{});\n", varNameInstance, field.getName(), FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, lenRes);
            }
            case highAddressAppend -> {
                ParseUtil.append(body, "{}.{}={}.read_highAddressAppend({},{});\n", varNameInstance, field.getName(), FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, lenRes);
            }
        }
    }

    @Override
    public void buildDeParse(BuilderContext context) {
        final StringBuilder body = context.method_body;
        final Field field = context.field;
        final F_string_bcd anno = field.getAnnotation(F_string_bcd.class);
        if (anno.skip()) {
            ParseUtil.appendSkip_deParse(anno.len(), anno.lenExpr(), context);
            return;
        }
        final String fieldName = field.getName();
        final String valCode = varNameInstance + "." + fieldName;
        final String varNameField = ParseUtil.getFieldVarName(context);
        final String varNameFieldVal = varNameField + "_val";
        ParseUtil.append(body, "final String {}={};\n", varNameFieldVal, valCode);
        ParseUtil.append(body, "if({}!=null){\n", varNameFieldVal);
        final String lenRes;
        if (anno.len() == 0) {
            lenRes = ParseUtil.replaceExprToCode(anno.lenExpr(), context);
        } else {
            lenRes = anno.len() + "";
        }

        switch (anno.appendMode()) {
            case noAppend -> {
                ParseUtil.append(body, "{}.write_noAppend({},{},{});\n", FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, varNameFieldVal, lenRes);
            }
            case lowAddressAppend -> {
                ParseUtil.append(body, "{}.write_lowAddressAppend({},{},{});\n", FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, varNameFieldVal, lenRes);
            }
            case highAddressAppend -> {
                ParseUtil.append(body, "{}.write_highAddressAppend({},{},{});\n", FieldBuilder__F_string_bcd.class.getName(), varNameByteBuf, varNameFieldVal, lenRes);
            }
        }
        ParseUtil.append(body, "}\n", valCode);
    }


    public static String read_noAppend(ByteBuf byteBuf, int len) {
        char[] chars = new char[len << 1];
        for (int i = 0; i < len; i++) {
            dumpByte(byteBuf.readUnsignedByte(), chars, i << 1);
        }
        return new String(chars);
    }

    public static String read_lowAddressAppend(ByteBuf byteBuf, int len) {
        char[] chars = new char[len << 1];
        int startIndex = -1;
        for (int i = 0; i < len; i++) {
            int value = byteBuf.readUnsignedByte();
            if (startIndex == -1) {
                if (value == 0) {
                    continue;
                }
                startIndex = (i << 1) + ((value >>> 4) == 0 ? 1 : 0);
            }
            dumpByte(value, chars, i << 1);
        }
        if (startIndex == -1) {
            return "";
        }
        return new String(chars, startIndex, chars.length - startIndex);
    }

    public static String read_highAddressAppend(ByteBuf byteBuf, int len) {
        char[] chars = new char[len << 1];
        int endIndex = -1;
        for (int i = 0; i < len; i++) {
            int value = byteBuf.readUnsignedByte();
            dumpByte(value, chars, i << 1);
            if (value != 0) {
                endIndex = (i << 1) + ((value & 0x0f) == 0 ? 0 : 1);
            }
        }
        return new String(chars, 0, endIndex + 1);
    }

    private static void dumpByte(int value, char[] chars, int targetIndex) {
        int sourceIndex = value << 1;
        chars[targetIndex] = BcdUtil.BCD_8421_DUMP_TABLE[sourceIndex];
        chars[targetIndex + 1] = BcdUtil.BCD_8421_DUMP_TABLE[sourceIndex + 1];
    }

    public static int write_noAppend(ByteBuf byteBuf, String s, int len) {
        int byteLen = (s.length() + 1) >> 1;
        if (byteLen != len) {
            throw BaseException.get("encoded bcd byte length[{}] must equal configured length[{}]", byteLen, len);
        }
        int charIndex = 0;
        if ((s.length() & 1) == 1) {
            byteBuf.writeByte(s.charAt(charIndex++) - '0');
        }
        writePairs(byteBuf, s, charIndex, s.length());
        return byteLen;
    }

    public static void write_lowAddressAppend(ByteBuf byteBuf, String s, int len) {
        checkMaxCharLength(s, len);
        int byteLen = (s.length() + 1) >> 1;
        byteBuf.writeZero(len - byteLen);
        int charIndex = 0;
        if ((s.length() & 1) == 1) {
            byteBuf.writeByte(Character.getNumericValue(s.charAt(charIndex++)));
        }
        writePairs(byteBuf, s, charIndex, s.length());
    }

    public static void write_highAddressAppend(ByteBuf byteBuf, String s, int len) {
        checkMaxCharLength(s, len);
        int pairEnd = s.length() & ~1;
        writePairs(byteBuf, s, 0, pairEnd);
        if (pairEnd != s.length()) {
            byteBuf.writeByte(Character.getNumericValue(s.charAt(pairEnd)) << 4);
        }
        byteBuf.writeZero(len - ((s.length() + 1) >> 1));
    }

    private static void writePairs(ByteBuf byteBuf, String s, int start, int end) {
        for (int i = start; i < end; i += 2) {
            int high = s.charAt(i) - '0';
            int low = s.charAt(i + 1) - '0';
            byteBuf.writeByte(high << 4 | low);
        }
    }

    private static void checkMaxCharLength(String s, int len) {
        if (s.length() > (len << 1)) {
            throw BaseException.get("bcd string length[{}] exceeds configured digit length[{}]", s.length(), len << 1);
        }
    }

    public static void main(String[] args) {
        String s = "2117299841738";
        ByteBuf buffer1 = Unpooled.buffer();
        write_highAddressAppend(buffer1, s, 20);
        String s1 = read_highAddressAppend(buffer1, 20);
        System.out.println(s1);

        ByteBuf buffer2 = Unpooled.buffer();
        write_lowAddressAppend(buffer2, s, 20);
        String s2 = read_lowAddressAppend(buffer2, 20);
        System.out.println(s2);

        ByteBuf buffer3 = Unpooled.buffer();
        int len3 = write_noAppend(buffer3, s, 7);
        String s3 = read_noAppend(buffer3, len3);
        System.out.println(s3);
    }
}
