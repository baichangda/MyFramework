package cn.bcd.lib.spring.storage.influxdb;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.config.ClientConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(value = "lib.spring.storage.influxdb.url")
@Configuration
@EnableConfigurationProperties(InfluxdbProp.class)
public class InfluxdbConfig {

    public static InfluxDBClient client;
    public static String database;

    @Bean
    public InfluxDBClient influxDBClient(InfluxdbProp influxdbProp) {
        database = influxdbProp.database;
        ClientConfig config = new ClientConfig.Builder()
                .host(influxdbProp.url)
                .token(influxdbProp.token.toCharArray())
                .database(influxdbProp.database)
                //非同步写入
                .writeNoSync(true)
                .build();
        InfluxDBClient influxDBClient = InfluxDBClient.getInstance(config);
        client = influxDBClient;
        return influxDBClient;
    }
}
