package cn.bcd.lib.storage.iotdb;

import cn.bcd.lib.storage.iotdb.gb32960.IotdbUtil_gb32960;
import cn.bcd.lib.storage.iotdb.gb32960.RawData;
import org.apache.iotdb.session.pool.SessionPool;

import java.util.Date;
import java.util.List;

public class TestIotdb_gb32960 {
    @org.junit.jupiter.api.Test
    public void testInsert() {
        SessionPool sessionPool = new SessionPool.Builder()
                .nodeUrls(List.of("127.0.0.1:16667"))
                .user("root")
                .password("root")
                .maxSize(5)
                .build();
        IotdbConfig.session = sessionPool;
        IotdbUtil_gb32960.save_rawData(List.of(
                new RawData("TEST0000000000001", new Date(1749432478000L), 1, new Date(1749432478000L), new Date(1749432478000L), new Date(1749432478000L), "2323"),
                new RawData("TEST0000000000002", new Date(1749432478000L), 1, new Date(1749432478000L), new Date(1749432478000L), new Date(1749432478000L), "2323"),
                new RawData("TEST0000000000003", new Date(1749432478000L), 1, new Date(1749432478000L), new Date(1749432478000L), new Date(1749432478000L), "2323"),
                new RawData("TEST0000000000001", new Date(1749432479000L), 1, new Date(1749432478000L), new Date(1749432478000L), new Date(1749432478000L), "2323"),
                new RawData("TEST0000000000001", new Date(1749432481000L), 1, new Date(1749432478000L), new Date(1749432478000L), new Date(1749432478000L), "2323")
        ));
    }
}
