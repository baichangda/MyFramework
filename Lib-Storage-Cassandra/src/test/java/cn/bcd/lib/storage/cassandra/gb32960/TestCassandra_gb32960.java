package cn.bcd.lib.storage.cassandra.gb32960;


import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.storage.cassandra.CassandraConfig;
import cn.bcd.lib.storage.cassandra.PageResult;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;


public class TestCassandra_gb32960 {

    static Logger logger = LoggerFactory.getLogger(TestCassandra_gb32960.class);

    @org.junit.jupiter.api.Test
    public void testPage() {
        try (CqlSession session = CqlSession.builder()
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withInt(DefaultDriverOption.NETTY_IO_SIZE, Runtime.getRuntime().availableProcessors() * 2)
                                .build()
                )
                .withLocalDatacenter("datacenter1")
                .addContactPoint(new InetSocketAddress("10.0.11.50", 9042))
                .withAuthCredentials("cassandra", "cassandra")
                .build()) {
            CassandraConfig.session = session;
            PageResult<RawData> pageResult = CassandraUtil_gb32960.page_rawData("TEST0000000000000", LocalDateTime.now().plusDays(-30).toInstant(DateZoneUtil.ZONE_OFFSET), Instant.now(), null, 10, true);
            for (RawData rawData : pageResult.list) {
                logger.info(JsonUtil.toJson(rawData));
            }
        }
    }

    @org.junit.jupiter.api.Test
    public void testGet() {
        try (CqlSession session = CqlSession.builder()
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withInt(DefaultDriverOption.NETTY_IO_SIZE, Runtime.getRuntime().availableProcessors() * 2)
                                .build()
                )
                .withLocalDatacenter("datacenter1")
                .addContactPoint(new InetSocketAddress("10.0.11.50", 9042))
                .withAuthCredentials("cassandra", "cassandra")
                .build()) {
            CassandraConfig.session = session;
            RawData rawData = CassandraUtil_gb32960.get_rawData("TEST0000000000000", LocalDateTime.of(2025,5,27,11,0,30,154000000).toInstant(DateZoneUtil.ZONE_OFFSET), 1);
            logger.info(JsonUtil.toJson(rawData));
        }
    }

    @org.junit.jupiter.api.Test
    public void testInsert() {
        try (CqlSession session = CqlSession.builder()
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withInt(DefaultDriverOption.NETTY_IO_SIZE, Runtime.getRuntime().availableProcessors() * 2)
                                .build()
                )
                .withLocalDatacenter("datacenter1")
                .addContactPoint(new InetSocketAddress("10.0.11.50", 9042))
                .withAuthCredentials("cassandra", "cassandra")
                .build()) {
            CassandraConfig.session = session;
            List<RawData> list = List.of(
                    new RawData("TEST0000000000000",
                            LocalDateTime.now().plusDays(-0).toInstant(DateZoneUtil.ZONE_OFFSET), 1,
                            LocalDateTime.now().plusDays(-0).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-0).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-0).toInstant(DateZoneUtil.ZONE_OFFSET), "TEST0000000000000"),
                    new RawData("TEST0000000000001",
                            LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET), 2,
                            LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-1).toInstant(DateZoneUtil.ZONE_OFFSET), "TEST0000000000001"),
                    new RawData("TEST0000000000002",
                            LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET), 3,
                            LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-2).toInstant(DateZoneUtil.ZONE_OFFSET), "TEST0000000000002"),
                    new RawData("TEST0000000000003",
                            LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET), 4,
                            LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-3).toInstant(DateZoneUtil.ZONE_OFFSET), "TEST0000000000003"),
                    new RawData("TEST0000000000004",
                            LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET), 5,
                            LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET),
                            LocalDateTime.now().plusDays(-4).toInstant(DateZoneUtil.ZONE_OFFSET), "TEST0000000000004")
            );
            CassandraUtil_gb32960.save_rawData(list);
        }
    }
}
