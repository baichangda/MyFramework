package cn.bcd.lib.prometheus.exporter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.prometheus.exporter")
class ExporterProp {
    public String host = "127.0.0.1";
    public int port;
}
