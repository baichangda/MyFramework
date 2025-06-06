package cn.bcd.lib.storage.iotdb;

import org.apache.iotdb.session.pool.SessionPool;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestIotdb {
    @Test
    public void test1(){
        SessionPool sessionPool = new SessionPool.Builder()
                .nodeUrls(List.of("127.0.0.1:6667"))
                .user("root")
                .password("root")
                .maxSize(5)
                .build();
        sessionPool.insertRecordsOfOneDevice();
    }
}
