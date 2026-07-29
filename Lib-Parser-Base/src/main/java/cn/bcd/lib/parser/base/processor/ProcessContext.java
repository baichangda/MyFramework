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
    public final ByteBuf byteBuf;
    private Object parent;
    private Object[] ancestorStack;
    private int depth;

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
     * 进入一个Bean作用域。当前Bean会成为其字段处理器的直接父对象。
     */
    public final void enter(Object bean) {
        Objects.requireNonNull(bean, "bean");
        int currentDepth = depth;
        if (currentDepth != 0) {
            ensureAncestorCapacity(currentDepth);
            ancestorStack[currentDepth - 1] = parent;
        }
        parent = bean;
        depth = currentDepth + 1;
    }

    /**
     * 退出当前Bean作用域并恢复上一级父对象。
     */
    public final void exit() {
        int currentDepth = depth;
        if (currentDepth == 0) {
            throw new IllegalStateException("no bean scope to exit");
        }
        if (currentDepth == 1) {
            parent = null;
            depth = 0;
            return;
        }
        int parentIndex = currentDepth - 2;
        parent = ancestorStack[parentIndex];
        ancestorStack[parentIndex] = null;
        depth = currentDepth - 1;
    }

    /**
     * 获取当前字段处理器的直接父Bean。
     */
    public final Object getParent() {
        return getParent(0);
    }

    /**
     * 获取指定层级的父Bean，level=0代表直接父Bean。
     */
    public final Object getParent(int level) {
        int currentDepth = depth;
        if (currentDepth == 0) {
            throw new IllegalStateException("no parent bean is available");
        }
        if (level < 0 || level >= currentDepth) {
            throw new IllegalArgumentException(
                    "parent level must be between 0 and " + (currentDepth - 1) + ": " + level);
        }
        return level == 0 ? parent : ancestorStack[currentDepth - level - 1];
    }

    /**
     * 从直接父Bean开始向根节点查找最近的指定类型对象。
     */
    public final <T> T findParent(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isInstance(parent)) {
            return type.cast(parent);
        }
        for (int i = depth - 2; i >= 0; i--) {
            Object ancestor = ancestorStack[i];
            if (type.isInstance(ancestor)) {
                return type.cast(ancestor);
            }
        }
        return null;
    }

    private void ensureAncestorCapacity(int requiredCapacity) {
        if (ancestorStack == null) {
            ancestorStack = new Object[Math.max(8, requiredCapacity)];
        } else if (requiredCapacity > ancestorStack.length) {
            ancestorStack = java.util.Arrays.copyOf(
                    ancestorStack, Math.max(requiredCapacity, ancestorStack.length << 1));
        }
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

    private static void checkGlobalVarIndex(int varIndex) {
        if (varIndex < 0 || varIndex >= 26) {
            throw new IllegalArgumentException("global variable index must be between 0 and 25: " + varIndex);
        }
    }
}
