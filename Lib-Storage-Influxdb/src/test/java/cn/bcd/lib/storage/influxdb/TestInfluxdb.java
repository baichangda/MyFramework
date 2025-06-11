package cn.bcd.lib.storage.influxdb;

import com.influxdb.v3.client.InfluxDBClient;
import org.junit.jupiter.api.Test;

public class TestInfluxdb {
    @Test
    public void test() throws Exception {
        try(InfluxDBClient client = InfluxDBClient.getInstance("http://127.0.0.1:18181",
                "apiv3_jrn7KJmuq0sfKXFuAaPhEdnfTLAMHqOLJwIg6YP7nX8wmjbrU6PNkOfzGcjaWAfRIUNlpdB1H7QNDCty2XJ8iw".toCharArray(),
                "test")){
            client.query("select * from test").forEach(System.out::println);
        }
    }
}
