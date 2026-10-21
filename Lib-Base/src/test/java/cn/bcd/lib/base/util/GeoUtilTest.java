package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilTest {
    @Test
    void decodeCenterUsesLongitudeAndLatitudeBounds() {
        assertArrayEquals(new double[]{0, 0, 45, 45}, GeoUtil.decodeGeoHashBox("s"));
        assertArrayEquals(new double[]{22.5, 22.5}, GeoUtil.decodeGeoHashCenter("s"));
    }

    @Test
    void encodedPointsAndCentersBelongToTheirCells() {
        double[][] points = {{116.4, 39.9}, {-73.98, 40.75}, {151.2, -33.86}, {-58.38, -34.6}};
        for (double[] point : points) {
            for (int precision = 1; precision <= 12; precision++) {
                String hash = GeoUtil.encodeGeoHash(point[0], point[1], precision);
                assertTrue(GeoUtil.containsInGeoHash(point[0], point[1], hash), hash);
                double[] center = GeoUtil.decodeGeoHashCenter(hash);
                assertEquals(hash, GeoUtil.encodeGeoHash(center[0], center[1], precision));
            }
        }
    }

    @Test
    void containsIncludesBoundaryAndRejectsOutsidePoints() {
        assertTrue(GeoUtil.containsInGeoHash(0, 0, "s"));
        assertTrue(GeoUtil.containsInGeoHash(45, 45, "s"));
        assertFalse(GeoUtil.containsInGeoHash(-1, 20, "s"));
        assertFalse(GeoUtil.containsInGeoHash(20, 46, "s"));
    }
}
