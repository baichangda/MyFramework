package cn.bcd.lib.parser.base.builder;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import cn.bcd.lib.parser.base.log.BitBuf_writer_log;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.base.util.*;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuilderContext {
    /**
     * 类
     */
    public final Class<?> clazz;
    /**
     * 当前字段
     */
    public Field field;

    /**
     * 当前字段所在所有字段中的索引
     */
    public int fieldIndex;

    /**
     * 传递进来的字节序
     */
    public final ByteOrder byteOrder;

    /**
     * 类变量定义体
     */
    public final StringBuilder class_fieldDefineBody;
    /**
     * 构造方法体
     */
    public final StringBuilder class_constructBody;

    /**
     * 类全局变量定义内容对应变量名称
     * 避免重复定义类变量
     * 解析反解析共用
     */
    public final Map<String, String> class_varDefineToVarName;

    /**
     * 字段集合
     */
    public final List<Field> class_fieldList;

    /**
     * parse方法体
     */
    public final StringBuilder method_body;

    /**
     * 解析/反解析 方法中
     * 用于给
     * {@link Processor#process(ByteBuf, ProcessContext)}
     * {@link Processor#deProcess(ByteBuf, ProcessContext, Object)}
     * 的参数对象、对象复用、避免构造多个
     * 解析和反解析不共用
     */
    public String method_processContextVarName;

    /**
     * 解析/反解析 方法中使用的变量对应字段名
     * 解析和反解析不共用
     */
    public final Map<Character, String> method_varToFieldName = new HashMap<>();

    /**
     * 构造 解析/反解析 方法所使用的缓存
     * 解析和反解析不共用
     * 解析或反解析期间所有字段共享缓存
     */
    public final Map<String, Object> method_cache = new HashMap<>();

    /**
     * 变量序号
     * 为了保证在一个 解析/反解析 方法中、定义的临时变量不重复
     * 解析和反解析不共用
     */
    public int method_varIndex = 0;


    public final NumValGetter numValGetter;

    public BuilderContext(StringBuilder class_fieldDefineBody, StringBuilder class_constructBody, StringBuilder method_body, Class<?> clazz,
                          Map<String, String> class_varDefineToVarName, ByteOrder byteOrder,
                          List<Field> class_fieldList, NumValGetter numValGetter) {
        this.class_fieldDefineBody = class_fieldDefineBody;
        this.class_constructBody = class_constructBody;
        this.method_body = method_body;
        this.clazz = clazz;
        this.class_varDefineToVarName = class_varDefineToVarName;
        this.byteOrder = byteOrder;
        this.class_fieldList = class_fieldList;
        this.numValGetter = numValGetter;
    }

    public final String getProcessContextVarName() {
        if (method_processContextVarName == null) {
            method_processContextVarName = "processContext";
            final String processContextClassName = ProcessContext.class.getName();
            ParseUtil.append(method_body, "final {} {}=new {}({},{});\n",
                    processContextClassName,
                    method_processContextVarName,
                    processContextClassName,
                    FieldBuilder.varNameInstance,
                    FieldBuilder.varNameProcessContext
            );
        }
        return method_processContextVarName;
    }

    public final String getCustomizeProcessorVarName(Class<?> processorClass, String processorArgs) {
        final String processorClassName = processorClass.getName();
        return ParseUtil.defineClassVar(this, processorClass, "new {}({})", processorClassName, processorArgs);
    }

    public final String getNumValGetterVarName() {
        if (numValGetter == null) {
            throw cn.bcd.lib.base.exception.BaseException.get(
                    "class[{}] field[{}] requires NumValGetter because value checking is enabled",
                    clazz.getName(), field.getName());
        }
        return ParseUtil.defineClassVar(this, null, NumValGetter.class,
                NumValGetter.class.getSimpleName(),
                "{}.get({})",
                NumValGetter.class.getName(),numValGetter.index);
    }

    public final String getProcessorVarName(Class<?> beanClazz) {
        return ParseUtil.defineClassVar(this, e -> {
                    Parser.getProcessor(beanClazz, byteOrder, numValGetter);
                }, Processor.class,
                Processor.class.getSimpleName() + "_" + beanClazz.getSimpleName(),
                "{}.getCachedProcessor(\"{}\")",
                Parser.class.getName(), ParseUtil.getProcessorKey(beanClazz, byteOrder, numValGetter));
    }

    public final String getGlobalVarName(char c) {
        return (String) method_cache.computeIfAbsent(ParseUtil.getGlobalVarName(c), k -> {
            ParseUtil.appendGetGlobalVar(this, c);
            return k;
        });
    }

    public final String getBitBuf_parse() {
        if (!method_cache.containsKey("hasBitBuf")) {
            final String bitBuf_reader_className = !Parser.isParseLogEnabled() ? BitBuf_reader.class.getName() : BitBuf_reader_log.class.getName();
            final String funcName = !Parser.isParseLogEnabled() ? "getBitBuf_reader" : "getBitBuf_reader_log";
            ParseUtil.append(method_body, "final {} {}={}.{}();\n", bitBuf_reader_className, FieldBuilder.varNameBitBuf, FieldBuilder.varNameProcessContext, funcName);
            method_cache.put("hasBitBuf", true);
        }
        return FieldBuilder.varNameBitBuf;
    }

    public final String getBitBuf_deParse() {
        if (!method_cache.containsKey("hasBitBuf")) {
            final String bitBuf_writer_className = !Parser.isDeParseLogEnabled() ? BitBuf_writer.class.getName() : BitBuf_writer_log.class.getName();
            final String funcName = !Parser.isDeParseLogEnabled() ? "getBitBuf_writer" : "getBitBuf_writer_log";
            ParseUtil.append(method_body, "final {} {}={}.{}();\n", bitBuf_writer_className, FieldBuilder.varNameBitBuf, FieldBuilder.varNameProcessContext, funcName);
            method_cache.put("hasBitBuf", true);
        }
        return FieldBuilder.varNameBitBuf;
    }
}
