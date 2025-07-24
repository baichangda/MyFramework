package cn.bcd.lib.prometheus.exporter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.prometheus.exporter")
class ExporterProp {
    public int port;
}
