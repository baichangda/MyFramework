package cn.bcd.lib.spring.prometheus.exporter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.spring.prometheus.exporter")
class ExporterProp {
    public String host = "127.0.0.1";
    public int port;
}
