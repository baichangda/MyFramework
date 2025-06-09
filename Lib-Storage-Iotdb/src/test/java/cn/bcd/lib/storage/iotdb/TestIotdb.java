package cn.bcd.lib.storage.iotdb;

import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.tsfile.enums.TSDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestIotdb {
    @Test
    public void test1() throws IoTDBConnectionException, StatementExecutionException {
        SessionPool sessionPool = new SessionPool.Builder()
                .nodeUrls(List.of("127.0.0.1:16667"))
                .user("root")
                .password("root")
                .maxSize(5)
                .build();
        long ts = 1749432478000L;
        sessionPool.insertRecord(
                "root.vehicle.rawData.TEST0000000000001",
                ts,
                List.of("hex"),
                List.of(TSDataType.STRING),
                List.of("2323"));
    }
}
