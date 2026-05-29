package cn.bcd.lib.spring.storage.influxdb.gb32960;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.storage.influxdb.InfluxdbConfig;
import cn.bcd.lib.spring.storage.influxdb.raw.InfluxdbUtil_gb32960;
import cn.bcd.lib.spring.storage.influxdb.raw.RawData;
import com.influxdb.v3.client.InfluxDBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestInfluxdb_gb32960 {

    static {
        System.setProperty("arrow.memory.allocator.type", "unsafe");
    }

    static Logger logger = LoggerFactory.getLogger(TestInfluxdb_gb32960.class);

    static String url = "http://www.baicd.fun:18181";
    static String token = "apiv3_sNQtbpTrMhlNy9Bo9AFnP62eA2-ymoFXMDrjVAiBNIB4YJ3fTEx7v_AYF80Rn8YtFhpRYcMgX7LuMvJ0GvrU2Q";
    static String database = "testdb";

    @org.junit.jupiter.api.Test
    public void testPage() {
        try (InfluxDBClient client = InfluxDBClient.getInstance(url, token.toCharArray(), database)) {
            InfluxdbConfig.client = client;
            List<RawData> list = InfluxdbUtil_gb32960.page_rawData("TEST0000000000001",
                    Date.from(LocalDateTime.now().plusDays(-30).toInstant(DateZoneUtil.ZONE_OFFSET)),
                    Date.from(Instant.now()), 0, 10, true).get();
            for (RawData rawData : list) {
                logger.info(JsonUtil.toJson(rawData));
            }
        } catch (Exception e) {
            logger.error("error",e);
        }
    }

    @org.junit.jupiter.api.Test
    public void testGet() {
        try (InfluxDBClient client = InfluxDBClient.getInstance(url, token.toCharArray(), database)) {
            InfluxdbConfig.client = client;
            RawData rawData = InfluxdbUtil_gb32960.get_rawData("TEST0000000000000",
                    Date.from(LocalDateTime.of(2025, 5, 27, 11, 0, 30, 154000000).toInstant(DateZoneUtil.ZONE_OFFSET)), 1).get();
            logger.info(JsonUtil.toJson(rawData));
        } catch (Exception e) {
            logger.error("error",e);
        }
    }

    @org.junit.jupiter.api.Test
    public void testInsert() {
        try (InfluxDBClient client = InfluxDBClient.getInstance(url, token.toCharArray(), database)) {
            InfluxdbConfig.client = client;
            List<RawData> list = List.of(
                    new RawData("TEST0000000000000",
                            Date.from(LocalDateTime.of(2025, 5, 27, 11, 0, 30, 154000000).toInstant(DateZoneUtil.ZONE_OFFSET)), 1,
                            Date.from(LocalDateTime.of(2025, 5, 27, 11, 0, 30, 154000000).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.of(2025, 5, 27, 11, 0, 30, 154000000).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.of(2025, 5, 27, 11, 0, 30, 154000000).toInstant(DateZoneUtil.ZONE_OFFSET)), "TEST0000000000000"),
                    new RawData("TEST0000000000001",
                            Date.from(LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET)), 2,
                            Date.from(LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET)), "TEST0000000000001"),
                    new RawData("TEST0000000000002",
                            Date.from(LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET)), 3,
                            Date.from(LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET)), "TEST0000000000002"),
                    new RawData("TEST0000000000003",
                            Date.from(LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET)), 4,
                            Date.from(LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET)), "TEST0000000000003"),
                    new RawData("TEST0000000000004",
                            Date.from(LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET)), 5,
                            Date.from(LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET)),
                            Date.from(LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET)), "TEST0000000000004")
            );
            InfluxdbUtil_gb32960.save_rawData(list).get();
        } catch (Exception e) {
            logger.error("error",e);
        }
    }
}
