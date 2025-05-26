package cn.bcd.lib.storage.cassandra;


import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.net.InetSocketAddress;
import java.time.Instant;


public class TestCassandra {
    @org.junit.jupiter.api.Test
    public void test() {
        try (CqlSession session = CqlSession.builder()
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withInt(DefaultDriverOption.NETTY_IO_SIZE, Runtime.getRuntime().availableProcessors() *2)
                                .build()
                )
                .withLocalDatacenter("datacenter1")
                .addContactPoint(new InetSocketAddress("10.0.11.50", 9042))
                .withAuthCredentials("cassandra","cassandra")
                .build()) {
            ResultSet resultSet = session.execute("select * from test1.test");
            for (Row row : resultSet) {
                String vin = row.getString("vin");
                Instant timestamp = row.getInstant("timestamp");
                int type = row.getInt("type");
                String data = row.getString("data");
                System.out.println(vin + " " + timestamp + " " + type + " " + data);
            }
        }
    }
}
