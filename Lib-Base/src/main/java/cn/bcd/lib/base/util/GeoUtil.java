package cn.bcd.lib.base.util;

/**
 * 纯 Java 原生地理工具类
 *
 * 能力：
 * 1. 计算两个经纬度距离，单位：米
 * 2. 判断点是否在多边形电子围栏内
 * 3. 判断点是否在圆形电子围栏内
 * 4. Geohash 编码
 * 5. Geohash 解码中心点
 * 6. Geohash 解码范围
 * 7. 判断点是否在某个 Geohash 格子内
 *
 * 坐标约定：
 * 1. 所有方法统一使用 lon, lat 顺序
 * 2. lon = 经度，范围 [-180, 180]
 * 3. lat = 纬度，范围 [-90, 90]
 * 4. polygon 二维数组格式：double[][] {{lon1, lat1}, {lon2, lat2}, ...}
 */
public final class GeoUtil {

    private GeoUtil() {
    }

    /**
     * 地球平均半径，单位：米
     */
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    /**
     * 经纬度计算误差
     */
    private static final double EPS = 1e-10;

    /**
     * 米级计算误差
     */
    private static final double METER_EPS = 1e-7;

    private static final char[] GEOHASH_BASE32 =
            "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    private static final int[] GEOHASH_BITS = {16, 8, 4, 2, 1};

    // ----------------------------------------------------------------------
    // 1. 距离计算
    // ----------------------------------------------------------------------

    /**
     * 计算两个经纬度之间的球面距离，单位：米。
     *
     * @param lon1 点1经度
     * @param lat1 点1纬度
     * @param lon2 点2经度
     * @param lat2 点2纬度
     * @return 两点距离，单位：米
     */
    public static double distanceMeters(
            double lon1,
            double lat1,
            double lon2,
            double lat2
    ) {
        validateLonLat(lon1, lat1);
        validateLonLat(lon2, lat2);

        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);

        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latRad1) * Math.cos(latRad2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    // ----------------------------------------------------------------------
    // 2. 圆形电子围栏
    // ----------------------------------------------------------------------

    /**
     * 判断点是否在圆形电子围栏内。
     *
     * 边界点算在内。
     *
     * @param lon          点经度
     * @param lat          点纬度
     * @param centerLon    圆心经度
     * @param centerLat    圆心纬度
     * @param radiusMeters 半径，单位：米
     * @return true = 在圆内或边界上，false = 在圆外
     */
    public static boolean containsInCircle(
            double lon,
            double lat,
            double centerLon,
            double centerLat,
            double radiusMeters
    ) {
        validateLonLat(lon, lat);
        validateLonLat(centerLon, centerLat);
        validateRadius(radiusMeters);

        double distance = distanceMeters(lon, lat, centerLon, centerLat);

        return distance <= radiusMeters + METER_EPS;
    }

    /**
     * 计算点到圆形围栏边界的距离。
     *
     * 返回值说明：
     * 1. 小于 0：点在圆内，绝对值表示距离边界还有多少米
     * 2. 等于 0：点在边界上
     * 3. 大于 0：点在圆外，表示距离边界还有多少米
     */
    public static double distanceToCircleBoundaryMeters(
            double lon,
            double lat,
            double centerLon,
            double centerLat,
            double radiusMeters
    ) {
        validateLonLat(lon, lat);
        validateLonLat(centerLon, centerLat);
        validateRadius(radiusMeters);

        double distance = distanceMeters(lon, lat, centerLon, centerLat);

        return distance - radiusMeters;
    }

    // ----------------------------------------------------------------------
    // 3. 多边形电子围栏
    // ----------------------------------------------------------------------

    /**
     * 判断点是否在普通多边形电子围栏内。
     *
     * 边界点算在内。
     *
     * @param lon     点经度
     * @param lat     点纬度
     * @param polygon 多边形坐标，格式：{{lon1, lat1}, {lon2, lat2}, ...}
     * @return true = 在多边形内或边界上，false = 在多边形外
     */
    public static boolean containsInPolygon(
            double lon,
            double lat,
            double[][] polygon
    ) {
        validateLonLat(lon, lat);
        validatePolygon(polygon);

        int n = polygon.length;

        // 1. 先判断是否在边界上，边界算在内
        for (int i = 0; i < n; i++) {
            double ax = polygon[i][0];
            double ay = polygon[i][1];

            double bx = polygon[(i + 1) % n][0];
            double by = polygon[(i + 1) % n][1];

            if (isPointOnSegment(lon, lat, ax, ay, bx, by)) {
                return true;
            }
        }

        // 2. 射线法判断是否在多边形内部
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon[i][0];
            double yi = polygon[i][1];

            double xj = polygon[j][0];
            double yj = polygon[j][1];

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi);

            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * 判断点是否在线段上。
     *
     * @param px 点经度
     * @param py 点纬度
     * @param ax 线段起点经度
     * @param ay 线段起点纬度
     * @param bx 线段终点经度
     * @param by 线段终点纬度
     */
    public static boolean isPointOnSegment(
            double px,
            double py,
            double ax,
            double ay,
            double bx,
            double by
    ) {
        // 处理退化线段：A 点和 B 点重合
        if (Math.abs(ax - bx) < EPS && Math.abs(ay - by) < EPS) {
            return Math.abs(px - ax) < EPS && Math.abs(py - ay) < EPS;
        }

        // 叉积判断是否共线
        double cross = (px - ax) * (by - ay) - (py - ay) * (bx - ax);
        if (Math.abs(cross) > EPS) {
            return false;
        }

        // 判断点是否在线段的外接矩形范围内
        return px >= Math.min(ax, bx) - EPS
                && px <= Math.max(ax, bx) + EPS
                && py >= Math.min(ay, by) - EPS
                && py <= Math.max(ay, by) + EPS;
    }

    // ----------------------------------------------------------------------
    // 4. Geohash 编码
    // ----------------------------------------------------------------------

    /**
     * Geohash 编码。
     *
     * @param lon       经度
     * @param lat       纬度
     * @param precision geohash 长度，常用 5 ~ 9，最大支持 12
     * @return geohash 字符串
     */
    public static String encodeGeoHash(
            double lon,
            double lat,
            int precision
    ) {
        validateLonLat(lon, lat);

        if (precision <= 0 || precision > 12) {
            throw new IllegalArgumentException("precision must be between 1 and 12");
        }

        double[] lonRange = {-180.0, 180.0};
        double[] latRange = {-90.0, 90.0};

        StringBuilder geohash = new StringBuilder();

        boolean evenBit = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            if (evenBit) {
                double mid = (lonRange[0] + lonRange[1]) / 2.0;
                if (lon >= mid) {
                    ch |= GEOHASH_BITS[bit];
                    lonRange[0] = mid;
                } else {
                    lonRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2.0;
                if (lat >= mid) {
                    ch |= GEOHASH_BITS[bit];
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }

            evenBit = !evenBit;

            if (bit < 4) {
                bit++;
            } else {
                geohash.append(GEOHASH_BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    // ----------------------------------------------------------------------
    // 5. Geohash 解码
    // ----------------------------------------------------------------------

    /**
     * Geohash 解码为中心点。
     *
     * 返回格式：
     * double[]{centerLon, centerLat}
     */
    public static double[] decodeGeoHashCenter(String geohash) {
        double[] box = decodeGeoHashBox(geohash);

        double centerLon = (box[0] + box[1]) / 2.0;
        double centerLat = (box[2] + box[3]) / 2.0;

        return new double[]{centerLon, centerLat};
    }

    /**
     * Geohash 解码为经纬度范围。
     *
     * 返回格式：
     * double[]{minLon, minLat, maxLon, maxLat}
     */
    public static double[] decodeGeoHashBox(String geohash) {
        validateGeoHash(geohash);

        double[] lonRange = {-180.0, 180.0};
        double[] latRange = {-90.0, 90.0};

        boolean evenBit = true;

        for (int i = 0; i < geohash.length(); i++) {
            char c = Character.toLowerCase(geohash.charAt(i));
            int cd = decodeBase32(c);

            for (int mask : GEOHASH_BITS) {
                if (evenBit) {
                    refineRange(lonRange, (cd & mask) != 0);
                } else {
                    refineRange(latRange, (cd & mask) != 0);
                }

                evenBit = !evenBit;
            }
        }

        return new double[]{
                lonRange[0],
                latRange[0],
                lonRange[1],
                latRange[1]
        };
    }

    /**
     * 判断点是否在某个 Geohash 格子范围内。
     *
     * @param lon     经度
     * @param lat     纬度
     * @param geohash geohash 字符串
     */
    public static boolean containsInGeoHash(
            double lon,
            double lat,
            String geohash
    ) {
        validateLonLat(lon, lat);

        double[] box = decodeGeoHashBox(geohash);

        double minLon = box[0];
        double maxLon = box[1];
        double minLat = box[2];
        double maxLat = box[3];

        return lon >= minLon
                && lon <= maxLon
                && lat >= minLat
                && lat <= maxLat;
    }

    // ----------------------------------------------------------------------
    // 6. 私有工具方法
    // ----------------------------------------------------------------------

    private static void refineRange(double[] range, boolean upper) {
        double mid = (range[0] + range[1]) / 2.0;

        if (upper) {
            range[0] = mid;
        } else {
            range[1] = mid;
        }
    }

    private static int decodeBase32(char c) {
        for (int i = 0; i < GEOHASH_BASE32.length; i++) {
            if (GEOHASH_BASE32[i] == c) {
                return i;
            }
        }

        throw new IllegalArgumentException("invalid geohash character: " + c);
    }

    private static void validateGeoHash(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("geohash must not be empty");
        }

        for (int i = 0; i < geohash.length(); i++) {
            decodeBase32(Character.toLowerCase(geohash.charAt(i)));
        }
    }

    private static void validatePolygon(double[][] polygon) {
        if (polygon == null || polygon.length < 3) {
            throw new IllegalArgumentException("polygon must have at least 3 points");
        }

        for (int i = 0; i < polygon.length; i++) {
            double[] point = polygon[i];

            if (point == null || point.length < 2) {
                throw new IllegalArgumentException("polygon[" + i + "] must be {lon, lat}");
            }

            validateLonLat(point[0], point[1]);
        }
    }

    private static void validateLonLat(double lon, double lat) {
        if (Double.isNaN(lon) || Double.isInfinite(lon)) {
            throw new IllegalArgumentException("invalid lon: " + lon);
        }

        if (Double.isNaN(lat) || Double.isInfinite(lat)) {
            throw new IllegalArgumentException("invalid lat: " + lat);
        }

        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("lon must be between -180 and 180: " + lon);
        }

        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("lat must be between -90 and 90: " + lat);
        }
    }

    private static void validateRadius(double radiusMeters) {
        if (Double.isNaN(radiusMeters)
                || Double.isInfinite(radiusMeters)
                || radiusMeters < 0) {
            throw new IllegalArgumentException("radiusMeters must be >= 0");
        }
    }
}