package cn.bcd.lib.parser.base.processor;

import cn.bcd.lib.parser.base.util.BitBuf_reader;
import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import cn.bcd.lib.parser.base.util.BitBuf_writer;
import cn.bcd.lib.parser.base.log.BitBuf_writer_log;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;

public class ProcessContext {
    public Object instance;
    public final ByteBuf byteBuf;

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
     * 解析过程中的对象变量。
     */
    public Object[] vars;

    /**
     * 创建一个解析环境
     * 此解析环境是root环境、没有父环境
     *
     * @param byteBuf
     */
    public ProcessContext(ByteBuf byteBuf) {
        this.instance = null;
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

    public final void putGlobalVar(int varIndex, int v) {
        checkGlobalVarIndex(varIndex);
        if (globalVars == null) {
            globalVars = new int[26];
        }
        globalVars[varIndex] = v;
    }

    public final int getGlobalVar(int varIndex) {
        checkGlobalVarIndex(varIndex);
        if (globalVars == null) {
            throw new IllegalStateException("global variable has not been initialized: " + (char) ('A' + varIndex));
        }
        return globalVars[varIndex];
    }

    public final void putVar(int index, Object value) {
        checkVarIndex(index);
        if (vars == null) {
            vars = new Object[index + 1];
        } else if (index >= vars.length) {
            vars = java.util.Arrays.copyOf(vars, index + 1);
        }
        vars[index] = value;
    }

    public final Object getVar(int index) {
        checkVarIndex(index);
        if (vars == null || index >= vars.length) {
            throw new IllegalStateException("variable has not been initialized: " + index);
        }
        return vars[index];
    }

    private static void checkVarIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("variable index must not be negative: " + index);
        }
    }

    private static void checkGlobalVarIndex(int varIndex) {
        if (varIndex < 0 || varIndex >= 26) {
            throw new IllegalArgumentException("global variable index must be between 0 and 25: " + varIndex);
        }
    }
}
