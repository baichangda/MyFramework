package cn.bcd.lib.parser.base.data;

/**
 * 默认数值获取器
 * 目的是判断解析出来的原始值、是否正常
 * 正常、则进行偏移量运算、否则直接标注值的异常类型
 * 只针对数值类型
 * 目前有如下几种异常情况
 * 0xFF为异常值、对应type=1
 * 0xFE为无效值、对应type=2
 */
public final class DefaultNumValGetter extends NumValGetter {
    public final static DefaultNumValGetter instance = new DefaultNumValGetter();

    private DefaultNumValGetter() {

    }

    @Override
    public byte getType8(int val) {
        int normalizedVal = val & 0xFF;
        if (normalizedVal == 0xFF) {
            return 1;
        }
        if (normalizedVal == 0xFE) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType16(int val) {
        int normalizedVal = val & 0xFFFF;
        if (normalizedVal == 0xFFFF) {
            return 1;
        }
        if (normalizedVal == 0xFFFE) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType24(int val) {
        int normalizedVal = val & 0xFFFFFF;
        if (normalizedVal == 0xFFFFFF) {
            return 1;
        }
        if (normalizedVal == 0xFFFFFE) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType32(int val) {
        if (val == 0xFFFFFFFF) {
            return 1;
        }
        if (val == 0xFFFFFFFE) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType40(long val) {
        long normalizedVal = val & 0xFFFFFFFFFFL;
        if (normalizedVal == 0xFFFFFFFFFFL) {
            return 1;
        }
        if (normalizedVal == 0xFFFFFFFFFEL) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType48(long val) {
        long normalizedVal = val & 0xFFFFFFFFFFFFL;
        if (normalizedVal == 0xFFFFFFFFFFFFL) {
            return 1;
        }
        if (normalizedVal == 0xFFFFFFFFFFFEL) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType56(long val) {
        long normalizedVal = val & 0xFFFFFFFFFFFFFFL;
        if (normalizedVal == 0xFFFFFFFFFFFFFFL) {
            return 1;
        }
        if (normalizedVal == 0xFFFFFFFFFFFFFEL) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType64(long val) {
        if (val == 0xFFFFFFFFFFFFFFFFL) {
            return 1;
        }
        if (val == 0xFFFFFFFFFFFFFFFEL) {
            return 2;
        }
        return 0;
    }

    @Override
    public int getVal_int8(byte type) {
        if (type == 1) {
            return 0xFF;
        }
        if (type == 2) {
            return 0xFE;
        }
        return 0;
    }

    @Override
    public int getVal_int16(byte type) {
        if (type == 1) {
            return 0xFFFF;
        }
        if (type == 2) {
            return 0xFFFE;
        }
        return 0;
    }

    @Override
    public int getVal_int24(byte type) {
        if (type == 1) {
            return 0xFFFFFF;
        }
        if (type == 2) {
            return 0xFFFFFE;
        }
        return 0;
    }

    @Override
    public int getVal_int32(byte type) {
        if (type == 1) {
            return 0xFFFFFFFF;
        }
        if (type == 2) {
            return 0xFFFFFFFE;
        }
        return 0;
    }

    @Override
    public long getVal_long40(byte type) {
        if (type == 1) {
            return 0xFFFFFFFFFFL;
        }
        if (type == 2) {
            return 0xFFFFFFFFFEL;
        }
        return 0;
    }

    @Override
    public long getVal_long48(byte type) {
        if (type == 1) {
            return 0xFFFFFFFFFFFFL;
        }
        if (type == 2) {
            return 0xFFFFFFFFFFFEL;
        }
        return 0;
    }

    @Override
    public long getVal_long56(byte type) {
        if (type == 1) {
            return 0xFFFFFFFFFFFFFFL;
        }
        if (type == 2) {
            return 0xFFFFFFFFFFFFFEL;
        }
        return 0;
    }

    @Override
    public long getVal_long64(byte type) {
        if (type == 1) {
            return 0xFFFFFFFFFFFFFFFFL;
        }
        if (type == 2) {
            return 0xFFFFFFFFFFFFFFFEL;
        }
        return 0;
    }
}
