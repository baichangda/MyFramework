package cn.bcd.lib.parser.base.builder;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.data.*;
import cn.bcd.lib.parser.base.util.ParseUtil;
import cn.bcd.lib.parser.base.util.RpnUtil;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Field;

public class FieldBuilder__F_num extends FieldBuilder {
    @Override
    public void buildParse(BuilderContext context) {
        if (buildParse_checkVal(context)) {
            return;
        }
        final Class<F_num> annoClass = F_num.class;
        final Field field = context.field;
        final F_num anno = field.getAnnotation(annoClass);
        ParseUtil.check_var(context, annoClass, anno.var(), anno.globalVar());
        final Class<?> fieldTypeClass = field.getType();
        final String sourceValTypeName;
        final String fieldTypeName = fieldTypeClass.getName();

        switch (fieldTypeName) {
            case "byte", "short", "int", "long", "float", "double" -> {
                sourceValTypeName = fieldTypeName;
            }
            default -> {
                if (fieldTypeClass.isEnum()) {
                    sourceValTypeName = "int";
                } else {
                    ParseUtil.notSupport_fieldType(context, annoClass);
                    sourceValTypeName = null;
                }
            }
        }


        final StringBuilder body = context.method_body;
        final String varNameInstance = FieldBuilder.varNameInstance;
        final String varNameField = ParseUtil.getFieldVarName(context);
        final boolean bigEndian = ParseUtil.bigEndian(anno.order(), context.byteOrder);
        final NumType type = anno.type();
        String funcName;
        switch (type) {
            case uint8 -> {
                if (sourceValTypeName.equals("byte")) {
                    funcName = varNameByteBuf + ".readByte()";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedByte()";
                }
            }
            case uint16 -> {
                if (sourceValTypeName.equals("short")) {
                    funcName = varNameByteBuf + ".readShort" + (bigEndian ? "" : "LE") + "()";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedShort" + (bigEndian ? "" : "LE") + "()";
                }
            }
            case uint24 -> {
                funcName = varNameByteBuf + ".readUnsignedMedium" + (bigEndian ? "" : "LE") + "()";
            }
            case uint32 -> {
                if (sourceValTypeName.equals("int")) {
                    funcName = varNameByteBuf + ".readInt" + (bigEndian ? "" : "LE") + "()";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedInt" + (bigEndian ? "" : "LE") + "()";
                }
            }
            case uint40 -> {
                funcName = ParseUtil.format("{}.read_uint40{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case uint48 -> {
                funcName = ParseUtil.format("{}.read_uint48{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case uint56 -> {
                funcName = ParseUtil.format("{}.read_uint56{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case uint64 -> {
                funcName = varNameByteBuf + ".readLong" + (bigEndian ? "" : "LE") + "()";
            }
            case int8 -> {
                funcName = varNameByteBuf + ".readByte()";
            }
            case int16 -> {
                funcName = varNameByteBuf + ".readShort" + (bigEndian ? "" : "LE") + "()";
            }
            case int24 -> {
                funcName = varNameByteBuf + ".readMedium" + (bigEndian ? "" : "LE") + "()";
            }
            case int32 -> {
                funcName = varNameByteBuf + ".readInt" + (bigEndian ? "" : "LE") + "()";
            }
            case int40 -> {
                funcName = ParseUtil.format("{}.read_int40{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case int48 -> {
                funcName = ParseUtil.format("{}.read_int48{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case int56 -> {
                funcName = ParseUtil.format("{}.read_int56{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
            }
            case int64 -> {
                funcName = varNameByteBuf + ".readLong" + (bigEndian ? "" : "LE") + "()";
            }
            case float32 -> {
                funcName = varNameByteBuf + ".readFloat" + (bigEndian ? "" : "LE") + "()";
            }
            case float64 -> {
                funcName = varNameByteBuf + ".readDouble" + (bigEndian ? "" : "LE") + "()";
            }
            default -> {
                funcName = null;
            }
        }
        //读取原始数据
        ParseUtil.append(body, "final {} {}=({}){};\n", sourceValTypeName, varNameField, sourceValTypeName, funcName);
        //表达式运算
        String valCode = ParseUtil.replaceValExprToCode(anno.valExpr(), varNameField);
        if (fieldTypeClass.isEnum()) {
            ParseUtil.append(body, "{}.{}={}.fromInteger((int)({}));\n", varNameInstance, field.getName(), fieldTypeName, valCode);
        } else {
            //格式化精度
            if ((fieldTypeClass == float.class || fieldTypeClass == double.class) && anno.precision() >= 0) {
                valCode = ParseUtil.format("{}.round((double){},{})", ParseUtil.class.getName(), valCode, anno.precision());
            }
            ParseUtil.append(body, "{}.{}=({}){};\n", varNameInstance, field.getName(), sourceValTypeName, valCode);
        }
        final char var = anno.var();
        if (var != '0') {
            context.method_varToFieldName.put(var, varNameField);
        }
        final char globalVar = anno.globalVar();
        if (globalVar != '0') {
            ParseUtil.appendPutGlobalVar(context, globalVar, varNameField);
        }
    }

    public boolean buildParse_checkVal(BuilderContext context) {
        final Class<F_num> annoClass = F_num.class;
        final Field field = context.field;
        final F_num anno = field.getAnnotation(annoClass);
        if (!anno.checkVal()) {
            return false;
        }
        //检查值类型的伴生字段
        String fieldName__v = field.getName() + "__v";
        try {
            final Field field__v = context.clazz.getField(fieldName__v);
            Class<?> field__vType = field__v.getType();
            if (field__vType != byte.class) {
                throw BaseException.get("class[{}] field[{}] valType field[{}] type[{}] must be byte", context.clazz.getName(), context.field.getName(), annoClass.getName(), fieldName__v, field__vType);
            }
        } catch (NoSuchFieldException e) {
            throw BaseException.get("class[{}] field[{}] has no valType field[{}]", context.clazz.getName(), context.field.getName(), annoClass.getName(), fieldName__v);
        }

        final Class<?> fieldTypeClass = field.getType();
        final String fieldTypeName = fieldTypeClass.getName();
        final StringBuilder body = context.method_body;
        final String varNameInstance = FieldBuilder.varNameInstance;
        final String varNameField = ParseUtil.getFieldVarName(context);
        final boolean bigEndian = ParseUtil.bigEndian(anno.order(), context.byteOrder);
        final NumType type = anno.type();
        String funcName;
        String rawValTypeName;
        switch (type) {
            case uint8 -> {
                if (fieldTypeName.equals("byte")) {
                    funcName = varNameByteBuf + ".readByte()";
                    rawValTypeName = "byte";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedByte()";
                    rawValTypeName = "short";
                }
            }
            case uint16 -> {
                if (fieldTypeName.equals("short")) {
                    funcName = varNameByteBuf + ".readShort" + (bigEndian ? "" : "LE") + "()";
                    rawValTypeName = "short";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedShort" + (bigEndian ? "" : "LE") + "()";
                    rawValTypeName = "int";
                }
            }
            case uint24 -> {
                funcName = varNameByteBuf + ".readUnsignedMedium" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "int";
            }
            case uint32 -> {
                if (fieldTypeName.equals("int")) {
                    funcName = varNameByteBuf + ".readInt" + (bigEndian ? "" : "LE") + "()";
                    rawValTypeName = "int";
                } else {
                    funcName = varNameByteBuf + ".readUnsignedInt" + (bigEndian ? "" : "LE") + "()";
                    rawValTypeName = "long";
                }
            }
            case uint40 -> {
                funcName = ParseUtil.format("{}.read_uint40{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case uint48 -> {
                funcName = ParseUtil.format("{}.read_uint48{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case uint56 -> {
                funcName = ParseUtil.format("{}.read_uint56{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case uint64 -> {
                funcName = varNameByteBuf + ".readLong" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "long";
            }
            case int8 -> {
                funcName = varNameByteBuf + ".readByte()";
                rawValTypeName = "byte";
            }
            case int16 -> {
                funcName = varNameByteBuf + ".readShort" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "short";
            }
            case int24 -> {
                funcName = varNameByteBuf + ".readMedium" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "int";
            }
            case int32 -> {
                funcName = varNameByteBuf + ".readInt" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "int";
            }
            case int40 -> {
                funcName = ParseUtil.format("{}.read_int40{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case int48 -> {
                funcName = ParseUtil.format("{}.read_int48{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case int56 -> {
                funcName = ParseUtil.format("{}.read_int56{}({})", FieldBuilder__F_num.class.getName(), bigEndian ? "" : "_le", FieldBuilder.varNameByteBuf);
                rawValTypeName = "long";
            }
            case int64 -> {
                funcName = varNameByteBuf + ".readLong" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "long";
            }
            case float32 -> {
                funcName = varNameByteBuf + ".readFloat" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "float";
            }
            case float64 -> {
                funcName = varNameByteBuf + ".readDouble" + (bigEndian ? "" : "LE") + "()";
                rawValTypeName = "double";
            }
            default -> {
                funcName = null;
                rawValTypeName = null;
            }
        }
        final char var = anno.var();
        final char globalVar = anno.globalVar();

        //读取原始数据
        String varNameRawVal = varNameField + "_rawVal";
        ParseUtil.append(body, "final {} {}={};\n", rawValTypeName, varNameRawVal, funcName);

        //获取值类型
        String varNameNumValType = varNameField + "_numValType";
        String varNameNumValGetter = context.getNumValGetterVarName();

        if (type == NumType.uint32) {
            //特殊处理、避免调用参数为long的方法
            ParseUtil.append(body, "final byte {}={}.getType({}.{},(int){});\n",
                    varNameNumValType, varNameNumValGetter,
                    NumType.class.getName(), type.name(), varNameRawVal);
        } else {
            ParseUtil.append(body, "final byte {}={}.getType({}.{},{});\n",
                    varNameNumValType, varNameNumValGetter,
                    NumType.class.getName(), type.name(), varNameRawVal);
        }


        String varNameExprVal = varNameField + "_exprVal";
        String varExprValDefineInIfCode;
        //如果定义了变量、则定义在if外面
        if (var != '0') {
            ParseUtil.append(body, "final {} {};\n", fieldTypeName, varNameExprVal);
            varExprValDefineInIfCode = varNameExprVal;
        } else {
            varExprValDefineInIfCode = ParseUtil.format("final {} {}", fieldTypeName, varNameExprVal);
        }
        //判断值类型
        ParseUtil.append(body, "if({}==0){\n", varNameNumValType);

        //计算表达式、格式化精度
        String rawValCode;
        if (rawValTypeName.equals(fieldTypeName)) {
            rawValCode = varNameRawVal;
        } else {
            rawValCode = ParseUtil.format("({}){}", fieldTypeName, varNameRawVal);
        }
        if ((fieldTypeName.equals("float") || fieldTypeName.equals("double")) && anno.precision() >= 0) {
            ParseUtil.append(body, "{}=({}){}.round((double){},{});\n",
                    varExprValDefineInIfCode,
                    fieldTypeName,
                    ParseUtil.class.getName(),
                    ParseUtil.replaceValExprToCode(anno.valExpr(), rawValCode),
                    anno.precision());
        } else {
            ParseUtil.append(body, "{}=({}){};\n",
                    varExprValDefineInIfCode,
                    fieldTypeName,
                    ParseUtil.replaceValExprToCode(anno.valExpr(), rawValCode));
        }

        //添加变量
        if (var != '0') {
            context.method_varToFieldName.put(var, varNameExprVal);
        }
        //添加全局变量
        if (globalVar != '0') {
            ParseUtil.appendPutGlobalVar(context, globalVar, varNameExprVal);
        }

        //设置值
        ParseUtil.append(body, "{}.{}={};\n", varNameInstance, field.getName(), varNameExprVal);

        ParseUtil.append(body, "}else{\n");

        if (var != '0') {
            ParseUtil.append(body, "{}=({})0;\n", varNameExprVal, fieldTypeName);
        }

        //添加全局变量
        if (globalVar != '0') {
            ParseUtil.appendPutGlobalVar(context, globalVar, "0");
        }

        //设置值类型
        ParseUtil.append(body, "{}.{}={};\n", varNameInstance, fieldName__v, varNameNumValType);

        ParseUtil.append(body, "}\n");
        return true;
    }


    @Override
    public void buildDeParse(BuilderContext context) {
        if (buildDeParse_checkVal(context)) {
            return;
        }
        final Class<F_num> annoClass = F_num.class;
        final Field field = context.field;
        final F_num anno = context.field.getAnnotation(annoClass);
        ParseUtil.check_var(context, annoClass, anno.var(), anno.globalVar());
        final boolean bigEndian = ParseUtil.bigEndian(anno.order(), context.byteOrder);
        final String varNameInstance = FieldBuilder.varNameInstance;
        final StringBuilder body = context.method_body;
        final String fieldName = field.getName();
        final String varNameField = ParseUtil.getFieldVarName(context);
        final Class<?> fieldType = field.getType();
        final boolean isFloat = fieldType == float.class || fieldType == double.class;
        final char var = anno.var();
        String valCode;
        final String fieldTypeName;
        //先判断是否是枚举类型、如果是枚举转换为int
        if (fieldType.isEnum()) {
            valCode = ParseUtil.format("{}.toInteger()", varNameInstance + "." + fieldName);
            fieldTypeName = "int";
        } else {
            valCode = varNameInstance + "." + fieldName;
            fieldTypeName = fieldType.getName();
        }

        //判断是否用到变量中、如果用到了、需要定义变量
        if (var != '0') {
            ParseUtil.append(body, "final {} {}={};\n", fieldTypeName, varNameField, valCode);
            context.method_varToFieldName.put(var, varNameField);
            valCode = varNameField;
        }

        //判断是否用到全局变量中、如果用到了、添加进去
        if (anno.globalVar() != '0') {
            ParseUtil.appendPutGlobalVar(context, anno.globalVar(), valCode);
        }

        //最后判断是否用了值表达式、如果用了、进行表达式处理
        if (!anno.valExpr().isEmpty()) {
            if (isFloat) {
                valCode = ParseUtil.replaceValExprToCode_round(RpnUtil.reverseExpr(anno.valExpr()), valCode);
            } else {
                valCode = ParseUtil.replaceValExprToCode(RpnUtil.reverseExpr(anno.valExpr()), valCode);
            }
        }
        final NumType type = anno.type();
        ParseUtil.append(body, getWriteCode(type, bigEndian, valCode));
    }


    public boolean buildDeParse_checkVal(BuilderContext context) {
        final Class<F_num> annoClass = F_num.class;
        final Field field = context.field;
        final F_num anno = field.getAnnotation(annoClass);
        if (!anno.checkVal()) {
            return false;
        }

        String fieldName__v = field.getName() + "__v";

        ParseUtil.check_var(context, annoClass, anno.var(), anno.globalVar());
        final boolean bigEndian = ParseUtil.bigEndian(anno.order(), context.byteOrder);
        final StringBuilder body = context.method_body;
        final String fieldName = field.getName();
        final String varNameField = ParseUtil.getFieldVarName(context);
        final Class<?> fieldTypeClass = field.getType();
        String fieldTypeName = fieldTypeClass.getName();

        final char var = anno.var();
        final char globalVar = anno.globalVar();

        //获取值类型
        String varNameNumValType = varNameField + "_numValType";
        ParseUtil.append(body, "final byte {}={}.{};\n", varNameNumValType, FieldBuilder.varNameInstance, fieldName__v);
        final boolean isFloat = fieldTypeName.equals("float") || fieldTypeName.equals("double");
        String varNameRawVal = varNameField + "_rawVal";
        String varExprValDefineInIfCode;
        if (var != '0') {
            ParseUtil.append(body, "final {} {};\n", fieldTypeName, varNameRawVal);
            varExprValDefineInIfCode = varNameRawVal;
        } else {
            varExprValDefineInIfCode = ParseUtil.format("final {} {}", fieldTypeName, varNameRawVal);
        }


        //判断值类型正常
        ParseUtil.append(body, "if({}==0){\n", varNameNumValType);

        //取出值
        ParseUtil.append(body, "{}={}.{};\n", varExprValDefineInIfCode, FieldBuilder.varNameInstance, fieldName);

        //设置变量
        if (var != '0') {
            context.method_varToFieldName.put(var, varNameRawVal);
        }
        //设置全局变量
        if (globalVar != '0') {
            ParseUtil.appendPutGlobalVar(context, anno.globalVar(), varNameRawVal);
        }

        //判断最后write的类型
        NumType type = anno.type();
        String funcSuffix = switch (type) {
            case uint8, int8, uint16, int16, uint24, int24, uint32, int32 -> "int";
            case uint40, int40, uint48, int48, uint56, int56, uint64, int64 -> "long";
            default -> null;
        };

        //计算表达式
        String varNameExprVal;
        if (anno.valExpr().isEmpty()) {
            varNameExprVal = varNameRawVal;
        } else {
            varNameExprVal = varNameField + "_exprVal";
            String exprValCode;
            if (isFloat) {
                exprValCode = ParseUtil.replaceValExprToCode_round(RpnUtil.reverseExpr(anno.valExpr()), varNameRawVal);
            } else {
                exprValCode = ParseUtil.replaceValExprToCode(RpnUtil.reverseExpr(anno.valExpr()), varNameRawVal);
            }
            ParseUtil.append(body, "final {} {}=({}){};\n", funcSuffix, varNameExprVal, funcSuffix, exprValCode);
        }

        //写入
        ParseUtil.append(body, getWriteCode(type, bigEndian, varNameExprVal));

        ParseUtil.append(body, "}else{\n");

        String varNameNumValGetter = context.getNumValGetterVarName();


        String valCode = ParseUtil.format("{}.getVal_{}({}.{},{})", varNameNumValGetter, funcSuffix, NumType.class.getName(), type.name(), varNameNumValType);
        ParseUtil.append(body, getWriteCode(type, bigEndian, valCode));

        if (var != '0') {
            ParseUtil.append(body, "{}=({})0;\n", varNameExprVal, fieldTypeName);
        }

        //设置全局变量
        if (globalVar != '0') {
            ParseUtil.appendPutGlobalVar(context, anno.globalVar(), "0");
        }

        ParseUtil.append(body, "}\n");
        return true;
    }

    public static String getWriteCode(NumType type, boolean bigEndian, String valCode) {
        switch (type) {
            case uint8, int8 -> {
                String funcName = "writeByte";
                return ParseUtil.format("{}.{}((int)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case uint16, int16 -> {
                String funcName = "writeShort" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((int)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case uint24, int24 -> {
                String funcName = "writeMedium" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((int)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case uint32, int32 -> {
                String funcName = "writeInt" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((int)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case uint40, int40 -> {
                String funcName = "write_int40" + (bigEndian ? "" : "_le");
                return ParseUtil.format("{}.{}({},(long)({}));\n", FieldBuilder__F_num.class.getName(), funcName, varNameByteBuf, valCode);
            }
            case uint48, int48 -> {
                String funcName = "write_int48" + (bigEndian ? "" : "_le");
                return ParseUtil.format("{}.{}({},(long)({}));\n", FieldBuilder__F_num.class.getName(), funcName, varNameByteBuf, valCode);
            }
            case uint56, int56 -> {
                String funcName = "write_int56" + (bigEndian ? "" : "_le");
                return ParseUtil.format("{}.{}({},(long)({}));\n", FieldBuilder__F_num.class.getName(), funcName, varNameByteBuf, valCode);
            }
            case uint64, int64 -> {
                String funcName = "writeLong" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((long)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case float32 -> {
                String funcName = "writeFloat" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((float)({}));\n", varNameByteBuf, funcName, valCode);
            }
            case float64 -> {
                String funcName = "writeDouble" + (bigEndian ? "" : "LE");
                return ParseUtil.format("{}.{}((double)({}));\n", varNameByteBuf, funcName, valCode);
            }
            default -> {
                return "";
            }
        }
    }

    public static long read_int40(ByteBuf byteBuf) {
        byte i1 = byteBuf.readByte();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_int40_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        byte i2 = byteBuf.readByte();
        return ((long) i2 << 32) | i1;
    }

    public static long read_uint40(ByteBuf byteBuf) {
        short i1 = byteBuf.readUnsignedByte();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_uint40_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        short i2 = byteBuf.readUnsignedByte();
        return ((long) i2 << 32) | i1;
    }

    public static void write_int40(ByteBuf byteBuf, long l) {
        byteBuf.writeByte((int) (l >> 32));
        byteBuf.writeInt((int) l);
    }

    public static void write_int40_le(ByteBuf byteBuf, long l) {
        byteBuf.writeIntLE((int) l);
        byteBuf.writeByte((int) (l >> 32));
    }

    public static long read_int48(ByteBuf byteBuf) {
        short i1 = byteBuf.readShort();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_uint48(ByteBuf byteBuf) {
        int i1 = byteBuf.readUnsignedShort();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_uint48_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        int i2 = byteBuf.readUnsignedShortLE();
        return ((long) i2 << 32) | i1;
    }

    public static long read_int48_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        short i2 = byteBuf.readShortLE();
        return ((long) i2 << 32) | i1;
    }

    public static void write_int48(ByteBuf byteBuf, long l) {
        byteBuf.writeShort((int) (l >> 32));
        byteBuf.writeInt((int) l);
    }

    public static void write_int48_le(ByteBuf byteBuf, long l) {
        byteBuf.writeIntLE((int) l);
        byteBuf.writeShortLE((int) (l >> 32));
    }

    public static long read_int56(ByteBuf byteBuf) {
        int i1 = byteBuf.readMedium();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_int56_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        int i2 = byteBuf.readMediumLE();
        return ((long) i2 << 32) | i1;
    }

    public static long read_uint56(ByteBuf byteBuf) {
        int i1 = byteBuf.readUnsignedMedium();
        long i2 = byteBuf.readUnsignedInt();
        return ((long) i1 << 32) | i2;
    }

    public static long read_uint56_le(ByteBuf byteBuf) {
        long i1 = byteBuf.readUnsignedIntLE();
        int i2 = byteBuf.readUnsignedMediumLE();
        return ((long) i2 << 32) | i1;
    }

    public static void write_int56(ByteBuf byteBuf, long l) {
        byteBuf.writeMedium((int) (l >> 32));
        byteBuf.writeInt((int) l);
    }

    public static void write_int56_le(ByteBuf byteBuf, long l) {
        byteBuf.writeIntLE((int) l);
        byteBuf.writeMediumLE((int) (l >> 32));
    }
}
