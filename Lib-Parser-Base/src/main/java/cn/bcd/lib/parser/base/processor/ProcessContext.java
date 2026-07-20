package cn.bcd.lib.parser.base.processor;

import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;
import cn.bcd.lib.parser.base.util.BitBuf_reader;
import cn.bcd.lib.parser.base.util.BitBuf_reader_log;
import cn.bcd.lib.parser.base.util.BitBuf_writer;
import cn.bcd.lib.parser.base.util.BitBuf_writer_log;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;

/** 一次完整解析或反解析所共享的上下文。 */
public class ProcessContext {
    private static final int INITIAL_VAR_CAPACITY = 4;
    private static final Object NULL_VALUE = new Object();

    /** 本次解析或反解析的顶层对象。 */
    public Object root;

    /** 当前正在解析字段所属的对象。 */
    public Object parent;

    public final ByteBuf byteBuf;

    private boolean rootInitialized;

    /**
     * 在解析过程中如果用到如下注解
     * {@link F_bit_num}
     * {@link F_bit_num_array}
     * 则会在解析过程中赋值、参考{@link #getBitBuf_reader()}、{@link #getBitBuf_writer()}
     */
    public BitBuf_reader bitBuf_reader;
    public BitBuf_writer bitBuf_writer;

    /** 数字表达式使用的全局变量。 */
    public int[] globalNumVars;

    /** F_var使用的通用变量，第一次写入时创建。 */
    private Object[] vars;

    /**
     * 创建一次解析或反解析的顶层上下文。子对象必须复用此对象。
     *
     * @param byteBuf 本次解析或反解析使用的ByteBuf
     */
    public ProcessContext(ByteBuf byteBuf) {
        this.byteBuf = Objects.requireNonNull(byteBuf, "byteBuf");
    }

    /** 进入一个对象的解析作用域，并返回进入前的parent，供{@link #exit(Object)}恢复。 */
    public final Object enter(Object instance) {
        Objects.requireNonNull(instance, "instance");
        Object previousParent = parent;
        if (!rootInitialized) {
            root = instance;
            rootInitialized = true;
        }
        parent = instance;
        return previousParent;
    }

    /** 恢复进入当前对象前的parent。 */
    public final void exit(Object previousParent) {
        parent = previousParent;
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

    public final void putGlobalNumVar(int varIndex, int value) {
        checkGlobalNumVarIndex(varIndex);
        if (globalNumVars == null) {
            globalNumVars = new int[26];
        }
        globalNumVars[varIndex] = value;
    }

    public final int getGlobalNumVar(int varIndex) {
        checkGlobalNumVarIndex(varIndex);
        if (globalNumVars == null) {
            throw new IllegalStateException("global numeric variable has not been initialized: " + (char) ('A' + varIndex));
        }
        return globalNumVars[varIndex];
    }

    /** 保存一次解析中共享的通用变量。索引应从0开始连续使用，相同索引后写覆盖前写。 */
    public final void putVar(int index, Object value) {
        checkVarIndex(index);
        ensureVarCapacity(index);
        vars[index] = value == null ? NULL_VALUE : value;
    }

    /** @throws IllegalStateException 对应索引尚未写入时抛出 */
    public final Object getVar(int index) {
        checkVarIndex(index);
        if (vars == null || index >= vars.length || vars[index] == null) {
            throw new IllegalStateException("variable has not been initialized: " + index);
        }
        Object value = vars[index];
        return value == NULL_VALUE ? null : value;
    }

    private void ensureVarCapacity(int index) {
        if (vars == null) {
            int capacity = INITIAL_VAR_CAPACITY;
            while (capacity <= index) {
                capacity = growCapacity(capacity, index);
            }
            vars = new Object[capacity];
            return;
        }
        if (index < vars.length) {
            return;
        }
        int capacity = vars.length;
        while (capacity <= index) {
            capacity = growCapacity(capacity, index);
        }
        vars = Arrays.copyOf(vars, capacity);
    }

    private static int growCapacity(int capacity, int index) {
        if (capacity > Integer.MAX_VALUE / 2) {
            if (index == Integer.MAX_VALUE - 1) {
                return Integer.MAX_VALUE;
            }
            throw new IllegalArgumentException("variable index is too large: " + index);
        }
        return capacity << 1;
    }

    private static void checkGlobalNumVarIndex(int varIndex) {
        if (varIndex < 0 || varIndex >= 26) {
            throw new IllegalArgumentException("global numeric variable index must be between 0 and 25: " + varIndex);
        }
    }

    private static void checkVarIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("variable index must not be negative: " + index);
        }
    }
}
