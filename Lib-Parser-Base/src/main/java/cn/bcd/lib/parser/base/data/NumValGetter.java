package cn.bcd.lib.parser.base.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 值获取器
 * 用于字段类型为如下类型的值验证
 */
public abstract class NumValGetter {
    private static final AtomicInteger GLOBAL_INDEX = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, NumValGetter> INDEX_NUM_VAL_GETTER = new ConcurrentHashMap<>();

    public static NumValGetter get(int index) {
        return INDEX_NUM_VAL_GETTER.get(index);
    }

    public final int index;

    public NumValGetter() {
        index = GLOBAL_INDEX.getAndIncrement();
        INDEX_NUM_VAL_GETTER.put(index, this);
    }

    public abstract byte getType8(int val);

    public abstract byte getType16(int val);

    public abstract byte getType24(int val);

    public abstract byte getType32(int val);

    public abstract byte getType40(long val);

    public abstract byte getType48(long val);

    public abstract byte getType56(long val);

    public abstract byte getType64(long val);

    public abstract int getVal_int8(byte type);

    public abstract int getVal_int16(byte type);

    public abstract int getVal_int24(byte type);

    public abstract int getVal_int32(byte type);

    public abstract long getVal_long40(byte type);

    public abstract long getVal_long48(byte type);

    public abstract long getVal_long56(byte type);

    public abstract long getVal_long64(byte type);
}
