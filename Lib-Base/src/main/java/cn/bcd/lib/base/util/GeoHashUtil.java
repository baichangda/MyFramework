package cn.bcd.lib.base.util;

import java.util.BitSet;

public class GeoHashUtil {

    // GeoHash 编码使用的32个字符
    private static final char[] BASE32 = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm', 'n',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private static final double[] latRange = {-90.0, 90.0};
    private static final double[] lngRange = {-180.0, 180.0};

    /**
     * 根据经纬度和指定的小数位数，计算并返回 GeoHash 编码。
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param precision 坐标的小数位数（例如：6 表示精确到小数点后6位）
     * @return GeoHash 编码字符串
     */
    public static String encode(double latitude, double longitude, int precision) {
        // 检查经纬度范围
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must in [-90, 90]");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must in [-180, 180]");
        }
        if (precision < 0) {
            throw new IllegalArgumentException("precision must > 0");
        }
        // 根据小数位数计算所需的二进制位数
        int latBits = getBitsForPrecision(precision, latRange[1] - latRange[0]);
        int lngBits = getBitsForPrecision(precision, lngRange[1] - lngRange[0]);
        int totalBits = latBits + lngBits;

        // 对经纬度进行二进制编码
        BitSet latBitSet = encodeToBits(latitude, latRange, latBits);
        BitSet lngBitSet = encodeToBits(longitude, lngRange, lngBits);

        // 合并经纬度二进制（经度在前，纬度在后，交替排列）
        BitSet mergedBitSet = new BitSet(totalBits);
        int mergeIndex = 0;
        for (int i = 0; i < Math.max(latBits, lngBits); i++) {
            if (i < lngBits) {
                mergedBitSet.set(mergeIndex++, lngBitSet.get(i));
            }
            if (i < latBits) {
                mergedBitSet.set(mergeIndex++, latBitSet.get(i));
            }
        }

        // 5. 将合并后的二进制转换为 Base32 编码
        return bitsToBase32(mergedBitSet, totalBits);
    }

    /**
     * 将 GeoHash 编码解码为一个包含经纬度范围的矩形区域。
     *
     * @param geohash GeoHash 编码字符串
     * @return 一个 double 数组，格式为: [minLat, maxLat, minLng, maxLng]
     */
    public static double[] decode(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("geohash must not be empty");
        }

        // 将 Base32 字符串转换为二进制位集
        BitSet mergedBitSet = base32ToBits(geohash);
        int totalBits = geohash.length() * 5;

        // 分离出纬度和经度的二进制位
        BitSet latBitSet = new BitSet();
        BitSet lngBitSet = new BitSet();

        int latIndex = 0;
        int lngIndex = 0;

        for (int i = 0; i < totalBits; i++) {
            if (i % 2 == 0) {
                // 偶数位（0, 2, 4...）是经度
                lngBitSet.set(lngIndex++, mergedBitSet.get(i));
            } else {
                // 奇数位（1, 3, 5...）是纬度
                latBitSet.set(latIndex++, mergedBitSet.get(i));
            }
        }

        int latBits = latIndex;
        int lngBits = lngIndex;

        // 将二进制位解码为经纬度范围
        double[] latRange = decodeBitsToRange(latBitSet, latBits, new double[]{-90.0, 90.0});
        double[] lngRange = decodeBitsToRange(lngBitSet, lngBits, new double[]{-180.0, 180.0});

        return new double[]{latRange[0], latRange[1], lngRange[0], lngRange[1]};
    }

    /**
     * 计算表示指定精度所需的二进制位数。
     *
     * @param precision 小数位数
     * @param range     该坐标（纬度或经度）的总范围
     * @return 所需的二进制位数
     */
    private static int getBitsForPrecision(int precision, double range) {
        if (precision == 0) {
            return 1;
        }
        // 计算达到该精度所需的最小分辨率
        double resolution = range / Math.pow(10, precision);
        // 计算所需的二进制位数 (ceil(log2(1/resolution)))
        return (int) Math.ceil(Math.log(range / resolution) / Math.log(2));
    }

    /**
     * 将一个数值编码为指定位数的二进制位集。
     */
    private static BitSet encodeToBits(double value, double[] range, int bits) {
        BitSet bitSet = new BitSet(bits);
        double min = range[0];
        double max = range[1];

        for (int i = 0; i < bits; i++) {
            double mid = (min + max) / 2;
            if (value > mid) {
                bitSet.set(i);
                min = mid;
            } else {
                max = mid;
            }
        }
        return bitSet;
    }

    /**
     * 将二进制位集解码为数值范围。
     */
    private static double[] decodeBitsToRange(BitSet bitSet, int bits, double[] range) {
        double min = range[0];
        double max = range[1];

        for (int i = 0; i < bits; i++) {
            double mid = (min + max) / 2;
            if (bitSet.get(i)) {
                min = mid;
            } else {
                max = mid;
            }
        }
        return new double[]{min, max};
    }

    /**
     * 将 BitSet 转换为 Base32 字符串。
     */
    private static String bitsToBase32(BitSet bitSet, int totalBits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalBits; i += 5) {
            int value = 0;
            for (int j = 0; j < 5; j++) {
                if (i + j < totalBits && bitSet.get(i + j)) {
                    value |= (1 << (4 - j));
                }
            }
            sb.append(BASE32[value]);
        }
        return sb.toString();
    }

    /**
     * 将 Base32 字符串转换为 BitSet。
     */
    private static BitSet base32ToBits(String geohash) {
        BitSet bitSet = new BitSet(geohash.length() * 5);
        int bitIndex = 0;

        for (char c : geohash.toCharArray()) {
            int value = 0;
            // 找到字符在 BASE32 数组中的索引
            for (int i = 0; i < BASE32.length; i++) {
                if (BASE32[i] == c) {
                    value = i;
                    break;
                }
            }

            // 将5位数值转换为二进制，并设置到 BitSet 中
            for (int j = 4; j >= 0; j--) {
                bitSet.set(bitIndex++, (value & (1 << j)) != 0);
            }
        }
        return bitSet;
    }

    // --- 辅助方法 ---

    /**
     * 计算 GeoHash 编码的中心点纬度。
     */
    public static double getCenterLatitude(double[] bbox) {
        return (bbox[0] + bbox[1]) / 2.0;
    }

    /**
     * 计算 GeoHash 编码的中心点经度。
     */
    public static double getCenterLongitude(double[] bbox) {
        return (bbox[2] + bbox[3]) / 2.0;
    }

    /**
     * 打印 GeoHash 及其对应的边界框和中心点，方便调试。
     */
    public static void printGeoHashInfo(String geohash) {
        double[] bbox = decode(geohash);
        System.out.printf("GeoHash: %s\n", geohash);
        System.out.printf("边界框: [最小纬度: %.6f, 最大纬度: %.6f, 最小经度: %.6f, 最大经度: %.6f]\n",
                bbox[0], bbox[1], bbox[2], bbox[3]);
        System.out.printf("中心点: [纬度: %.6f, 经度: %.6f]\n",
                getCenterLatitude(bbox), getCenterLongitude(bbox));
        System.out.println("----------------------------------------");
    }

    public static void main(String[] args) {
        // 示例：编码
        double lat = 39.908823; // 北京天安门纬度
        double lng = 116.397470; // 北京天安门经度

        System.out.println("--- 编码示例 ---");
        for (int precision : new int[]{3, 4, 5, 6}) {
            String geohash = encode(lat, lng, precision);
            System.out.printf("经纬度 (%.6f, %.6f) ，小数位数 %d 位 -> GeoHash: %s\n", lat, lng, precision, geohash);
        }
        System.out.println();

        // 示例：解码和信息打印
        System.out.println("--- 解码示例 ---");
        String exampleHash = "wx4g0ec19x";
        printGeoHashInfo(exampleHash);

        // 你可以尝试不同的精度
        exampleHash = encode(lat, lng, 6);
        printGeoHashInfo(exampleHash);
    }
}