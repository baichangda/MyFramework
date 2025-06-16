package cn.bcd.lib.parser.base.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 值获取器
 * 用于字段类型为如下类型的值验证
 */
public abstract class NumValGetter {
    public static AtomicInteger globalIndex = new AtomicInteger();
    public final static ConcurrentHashMap<Integer, NumValGetter> index_numValGetter = new ConcurrentHashMap<>();

    public static NumValGetter get(int index) {
        return index_numValGetter.get(index);
    }

    public final int index;

    public NumValGetter() {
        index = globalIndex.getAndIncrement();
        index_numValGetter.put(index, this);
    }

    public abstract byte getType(NumType numType, int val);

    public abstract byte getType(NumType numType, long val);

    public abstract int getVal_int(NumType numType, byte type);

    public abstract long getVal_long(NumType numType, byte type);
}
