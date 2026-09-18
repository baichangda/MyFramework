package cn.bcd.lib.base.util;

/**
 * 浮点数比较与舍入工具。
 * <p>
 * 比较方法使用绝对容差，舍入方法面向监控指标等非精确小数场景；
 * 金额等要求十进制精确计算的场景应使用 {@code BigDecimal}。
 */
public final class FloatUtil {
    private static final int MAX_SCALE = 10;
    private static final double[] FACTORS = new double[MAX_SCALE + 1];
    private static final double[] TOLERANCES = new double[MAX_SCALE + 1];

    static {
        for (int scale = 0; scale <= MAX_SCALE; scale++) {
            double factor = Math.pow(10, scale);
            FACTORS[scale] = factor;
            TOLERANCES[scale] = 1 / factor;
        }
    }

    private FloatUtil() {
    }

    /**
     * 判断两个 {@code double} 是否在指定小数位对应的绝对容差内相等。
     * 容差为 {@code 10^-scale}；相同方向的无穷大视为相等，任何 NaN 均不相等。
     *
     * @param d1    第一个值
     * @param d2    第二个值
     * @param scale 小数位数，范围为 0～10
     * @return 差值绝对值不大于容差时返回 {@code true}
     * @throws IllegalArgumentException 当 {@code scale} 超出支持范围时抛出
     */
    public static boolean eq(double d1, double d2, int scale) {
        double tolerance = tolerance(scale);
        if (Double.isNaN(d1) || Double.isNaN(d2)) {
            return false;
        }
        if (d1 == d2) {
            return true;
        }
        return Math.abs(d1 - d2) <= tolerance;
    }

    /**
     * 判断 {@code d1} 是否在排除指定容差后仍严格大于 {@code d2}。
     * 差值位于容差范围内时返回 {@code false}，任何 NaN 参与比较时也返回 {@code false}。
     *
     * @param d1    第一个值
     * @param d2    第二个值
     * @param scale 小数位数，范围为 0～10
     * @return {@code d1 - d2 > 10^-scale} 时返回 {@code true}
     * @throws IllegalArgumentException 当 {@code scale} 超出支持范围时抛出
     */
    public static boolean gt(double d1, double d2, int scale) {
        double tolerance = tolerance(scale);
        if (Double.isNaN(d1) || Double.isNaN(d2) || d1 == d2) {
            return false;
        }
        return d1 - d2 > tolerance;
    }

    /**
     * 判断 {@code d1} 是否在排除指定容差后仍严格小于 {@code d2}。
     * 差值位于容差范围内时返回 {@code false}，任何 NaN 参与比较时也返回 {@code false}。
     *
     * @param d1    第一个值
     * @param d2    第二个值
     * @param scale 小数位数，范围为 0～10
     * @return {@code d2 - d1 > 10^-scale} 时返回 {@code true}
     * @throws IllegalArgumentException 当 {@code scale} 超出支持范围时抛出
     */
    public static boolean lt(double d1, double d2, int scale) {
        double tolerance = tolerance(scale);
        if (Double.isNaN(d1) || Double.isNaN(d2) || d1 == d2) {
            return false;
        }
        return d2 - d1 > tolerance;
    }

    /**
     * 将 {@code double} 四舍五入为 {@code long}，正负中点均采用远离零的 HALF_UP 规则。
     * NaN 返回 0，无穷大及超出范围的值按 {@code long} 边界饱和。
     *
     * @param value 待舍入值
     * @return 舍入后的整数
     */
    public static long round(double value) {
        if (!Double.isFinite(value)) {
            return Math.round(value);
        }
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (value <= Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return value >= 0 ? Math.round(value) : -Math.round(-value);
    }

    /**
     * 将 {@code float} 四舍五入为 {@code int}，正负中点均采用远离零的 HALF_UP 规则。
     * NaN 返回 0，无穷大及超出范围的值按 {@code int} 边界饱和。
     *
     * @param value 待舍入值
     * @return 舍入后的整数
     */
    public static int round(float value) {
        if (!Float.isFinite(value)) {
            return Math.round(value);
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return value >= 0 ? Math.round(value) : -Math.round(-value);
    }

    /**
     * 按十进制小数位进行 HALF_UP 四舍五入。
     * 此方法使用无分配的浮点运算，适合监控指标等非精确小数场景；金额等精确小数应使用 BigDecimal。
     * NaN 和正负无穷大将原样返回。
     *
     * @param value 待舍入值
     * @param scale 保留的小数位数，范围为 0～10
     * @return 舍入后的 {@code double}
     * @throws IllegalArgumentException 当 {@code scale} 超出支持范围时抛出
     */
    public static double format(double value, int scale) {
        validateScale(scale);
        if (!Double.isFinite(value)) {
            return value;
        }
        double factor = FACTORS[scale];
        double scaled = Math.abs(value) * factor;
        if (!Double.isFinite(scaled) || scaled >= 0x1.0p52) {
            return value;
        }

        // nextUp 补偿十进制临界值转换为二进制后可能产生的一个 ULP 误差，例如 1.005 * 100。
        double rounded = Math.floor(Math.nextUp(scaled) + 0.5d) / factor;
        return Math.copySign(rounded, value);
    }

    private static double tolerance(int scale) {
        validateScale(scale);
        return TOLERANCES[scale];
    }

    private static void validateScale(int scale) {
        if (scale < 0 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("scale must be between 0 and " + MAX_SCALE + ": " + scale);
        }
    }
}
