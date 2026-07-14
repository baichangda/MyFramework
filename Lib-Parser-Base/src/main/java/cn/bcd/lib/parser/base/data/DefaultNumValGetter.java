package cn.bcd.lib.parser.base.data;

/**
 * 默认数值获取器
 * 目的是判断解析出来的原始值、是否正常
 * 正常、则进行偏移量运算、否则直接标注值的异常类型
 * 只针对数值类型
 * 目前有如下几种异常情况
 * 0xFF为异常值
 * 0xFE为无效值
 */
public final class DefaultNumValGetter extends NumValGetter {
    public final static DefaultNumValGetter instance = new DefaultNumValGetter();

    private DefaultNumValGetter() {

    }

    @Override
    public byte getType(NumType numType, int val) {
        int mask = getIntMask(numType);
        if (mask == 0) {
            return 0;
        }

        int normalizedVal = val & mask;
        if (normalizedVal == mask) {
            return 1;
        }
        if (normalizedVal == mask - 1) {
            return 2;
        }
        return 0;
    }

    @Override
    public byte getType(NumType numType, long val) {
        long mask = getLongMask(numType);
        if (mask == 0) {
            return 0;
        }

        long normalizedVal = val & mask;
        if (normalizedVal == mask) {
            return 1;
        }
        if (normalizedVal == mask - 1) {
            return 2;
        }
        return 0;
    }

    @Override
    public int getVal_int(NumType numType, byte type) {
        int mask = getIntMask(numType);
        if (mask == 0) {
            return 0;
        }

        return switch (type) {
            case 1 -> mask;
            case 2 -> mask - 1;
            default -> 0;
        };
    }

    @Override
    public long getVal_long(NumType numType, byte type) {
        long mask = getLongMask(numType);
        if (mask == 0) {
            return 0;
        }

        return switch (type) {
            case 1 -> mask;
            case 2 -> mask - 1;
            default -> 0;
        };
    }

    private static int getIntMask(NumType numType) {
        return switch (numType) {
            case uint8, int8 -> 0xFF;
            case uint16, int16 -> 0xFFFF;
            case uint24, int24 -> 0xFFFFFF;
            case uint32, int32 -> 0xFFFFFFFF;
            default -> 0;
        };
    }

    private static long getLongMask(NumType numType) {
        return switch (numType) {
            case uint40, int40 -> 0xFFFFFFFFFFL;
            case uint48, int48 -> 0xFFFFFFFFFFFFL;
            case uint56, int56 -> 0xFFFFFFFFFFFFFFL;
            case uint64, int64 -> 0xFFFFFFFFFFFFFFFFL;
            default -> 0;
        };
    }
}
