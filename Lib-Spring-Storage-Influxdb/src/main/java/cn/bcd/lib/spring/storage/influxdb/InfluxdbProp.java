package cn.bcd.lib.spring.storage.influxdb;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lib.spring.storage.influxdb")
public class InfluxdbProp {
    public String url;
    public String token;
    public String database;
}
