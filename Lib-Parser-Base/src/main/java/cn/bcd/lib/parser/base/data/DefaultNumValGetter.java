package cn.bcd.lib.parser.base.data;

/**
 * 默认数值获取器
 * 目的是判断解析出来的原始值、是否正常
 * 正常、则进行偏移量运算、否则直接标注值的异常类型
 * 只针对数值类型
 * 目前有如下几种异常情况
 * 0xFF为无效、对应type=1
 * 0xFE为异常、对应type=2
 */
public final class DefaultNumValGetter extends NumValGetter {
    public static final byte TYPE_NORMAL = 0;
    public static final byte TYPE_INVALID = 1;
    public static final byte TYPE_ABNORMAL = 2;

    public final static DefaultNumValGetter instance = new DefaultNumValGetter();

    private DefaultNumValGetter() {

    }

    @Override
    public byte getType8(int val) {
        val = val & 0xFF;
        if (val == 0xFF) {
            return TYPE_INVALID;
        }
        if (val == 0xFE) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType16(int val) {
        val = val & 0xFFFF;
        if (val == 0xFFFF) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFE) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType24(int val) {
        val = val & 0xFFFFFF;
        if (val == 0xFFFFFF) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFE) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType32(int val) {
        if (val == 0xFFFFFFFF) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFFFE) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType40(long val) {
        val = val & 0xFFFFFFFFFFL;
        if (val == 0xFFFFFFFFFFL) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFFFFFEL) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType48(long val) {
        val = val & 0xFFFFFFFFFFFFL;
        if (val == 0xFFFFFFFFFFFFL) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFFFFFFFEL) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType56(long val) {
        val = val & 0xFFFFFFFFFFFFFFL;
        if (val == 0xFFFFFFFFFFFFFFL) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFFFFFFFFFEL) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public byte getType64(long val) {
        if (val == 0xFFFFFFFFFFFFFFFFL) {
            return TYPE_INVALID;
        }
        if (val == 0xFFFFFFFFFFFFFFFEL) {
            return TYPE_ABNORMAL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public int getVal_int8(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFF;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFE;
        }
        return TYPE_NORMAL;
    }

    @Override
    public int getVal_int16(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFF;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFE;
        }
        return TYPE_NORMAL;
    }

    @Override
    public int getVal_int24(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFF;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFE;
        }
        return TYPE_NORMAL;
    }

    @Override
    public int getVal_int32(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFFFF;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFFFE;
        }
        return TYPE_NORMAL;
    }

    @Override
    public long getVal_long40(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFFFFFFL;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFFFFFEL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public long getVal_long48(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFFFFFFFFL;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFFFFFFFEL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public long getVal_long56(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFFFFFFFFFFL;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFFFFFFFFFEL;
        }
        return TYPE_NORMAL;
    }

    @Override
    public long getVal_long64(byte type) {
        if (type == TYPE_INVALID) {
            return 0xFFFFFFFFFFFFFFFFL;
        }
        if (type == TYPE_ABNORMAL) {
            return 0xFFFFFFFFFFFFFFFEL;
        }
        return TYPE_NORMAL;
    }
}
