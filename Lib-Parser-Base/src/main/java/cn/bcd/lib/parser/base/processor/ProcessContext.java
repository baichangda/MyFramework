package cn.bcd.lib.parser.base.processor;

import cn.bcd.lib.parser.base.util.BitBuf_reader;
import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import cn.bcd.lib.parser.base.util.BitBuf_writer;
import cn.bcd.lib.parser.base.log.BitBuf_writer_log;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;

public class ProcessContext {
    public final ByteBuf byteBuf;
    private Object[] indexCache;
    private HashMap<String, Object> keyCache;

    /**
     * 在解析过程中如果用到如下注解
     * {@link F_bit_num}
     * {@link F_bit_num_array}
     * 则会在解析过程中赋值、参考{@link #getBitBuf_reader()}、{@link #getBitBuf_writer()}
     */
    public BitBuf_reader bitBuf_reader;
    public BitBuf_writer bitBuf_writer;

    /**
     * 全局变量定义
     */
    public int[] globalVars;

    /**
     * 创建一个解析环境
     *
     * @param byteBuf
     */
    public ProcessContext(ByteBuf byteBuf) {
        this.byteBuf = Objects.requireNonNull(byteBuf, "byteBuf");
    }

    public final BitBuf_reader getBitBuf_reader() {
        if (bitBuf_reader == null) {
            bitBuf_reader = new BitBuf_reader(byteBuf);
        }
        return bitBuf_reader;
    }

    public final BitBuf_writer getBitBuf_writer() {
        if (bitBuf_writer == null) {
            bitBuf_writer = new BitBuf_writer(byteBuf);
        }
        return bitBuf_writer;
    }

    public final BitBuf_reader_log getBitBuf_reader_log() {
        if (bitBuf_reader == null) {
            bitBuf_reader = new BitBuf_reader_log(byteBuf);
        }
        return (BitBuf_reader_log) bitBuf_reader;
    }

    public final BitBuf_writer_log getBitBuf_writer_log() {
        if (bitBuf_writer == null) {
            bitBuf_writer = new BitBuf_writer_log(byteBuf);
        }
        return (BitBuf_writer_log) bitBuf_writer;
    }

    /**
     * 使用数字索引缓存解析过程中产生的字段值。首次写入至少初始化4个元素。
     */
    public final void putCache(int index, Object value) {
        Object[] values = indexCache;
        if (values == null) {
            values = new Object[Math.max(4, index + 1)];
            indexCache = values;
        } else if (index >= values.length) {
            values = Arrays.copyOf(values, Math.max(index + 1, values.length << 1));
            indexCache = values;
        }
        values[index] = value;
    }

    public final Object getCache(int index) {
        return indexCache[index];
    }

    /**
     * 使用字符串键缓存解析过程中产生的字段值。
     */
    public final void putCache(String key, Object value) {
        if (keyCache == null) {
            keyCache = new HashMap<>();
        }
        keyCache.put(key, value);
    }

    public final Object getCache(String key) {
        HashMap<String, Object> values = keyCache;
        return values.get(key);
    }

    /**
     * 使用'A'到'Z'之间的字符保存全局数字变量。
     */
    public final void putGlobalVar(char var, int value) {
        int index = getGlobalVarIndex(var);
        if (globalVars == null) {
            globalVars = new int[26];
        }
        globalVars[index] = value;
    }

    /**
     * 获取使用'A'到'Z'字符标识的全局数字变量。
     */
    public final int getGlobalVar(char var) {
        int index = getGlobalVarIndex(var);
        return globalVars[index];
    }

    private static int getGlobalVarIndex(char var) {
        return var - 'A';
    }

}
