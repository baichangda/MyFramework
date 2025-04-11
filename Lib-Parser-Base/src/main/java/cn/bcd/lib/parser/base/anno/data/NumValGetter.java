package cn.bcd.lib.parser.base.anno.data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 值获取器
 * 用于字段类型为如下类型的值验证
 * {@link NumVal_byte}
 * {@link NumVal_short}
 * {@link NumVal_int}
 * {@link NumVal_long}
 * {@link NumVal_float}
 * {@link NumVal_double}
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

    public abstract int getType(NumType numType, int val);

    public abstract int getType(NumType numType, long val);

    public abstract int getVal_int(NumType numType, int type);

    public abstract long getVal_long(NumType numType, int type);


    /**
     * --------------------------------------------------
     * 以下方法用于简单值的解析和反解析
     * 所谓简单值指的是只需要判断原始值是否异常等等情况、而不用进行偏移量运算
     */

    public final NumVal_byte getNumVal_byte(NumType numType, byte val) {
        int type = getType(numType, val);
        if (type == 0) {
            return new NumVal_byte(0, val);
        } else {
            return new NumVal_byte(type, (byte) 0);
        }
    }

    public final NumVal_short getNumVal_short(NumType numType, short val) {
        int type = getType(numType, val);
        if (type == 0) {
            return new NumVal_short(0, val);
        } else {
            return new NumVal_short(type, (short) 0);
        }
    }

    public final NumVal_int getNumVal_int(NumType numType, int val) {
        int type = getType(numType, val);
        if (type == 0) {
            return new NumVal_int(0, val);
        } else {
            return new NumVal_int(type, 0);
        }
    }

    public final NumVal_long getNumVal_long(NumType numType, long val) {
        int type = getType(numType, val);
        if (type == 0) {
            return new NumVal_long(0, val);
        } else {
            return new NumVal_long(type, 0L);
        }
    }


    public final byte getVal(NumType numType, NumVal_byte numVal) {
        if (numVal.type() == 0) {
            return numVal.val();
        } else {
            return (byte) getVal_int(numType, numVal.type());
        }
    }

    public final short getVal(NumType numType, NumVal_short numVal) {
        if (numVal.type() == 0) {
            return numVal.val();
        } else {
            return (short) getVal_int(numType, numVal.type());
        }
    }

    public final int getVal(NumType numType, NumVal_int numVal) {
        if (numVal.type() == 0) {
            return numVal.val();
        } else {
            return getVal_int(numType, numVal.type());
        }
    }

    public final long getVal(NumType numType, NumVal_long numVal) {
        if (numVal.type() == 0) {
            return numVal.val();
        } else {
            return getVal_long(numType, numVal.type());
        }
    }
}
