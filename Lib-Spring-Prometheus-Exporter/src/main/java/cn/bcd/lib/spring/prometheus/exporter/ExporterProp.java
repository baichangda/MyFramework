package cn.bcd.lib.spring.prometheus.exporter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.spring.prometheus.exporter")
class ExporterProp {
    private String host;
    private int port = 9400;

    void validate() {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("lib.spring.prometheus.exporter.host must not be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("lib.spring.prometheus.exporter.port must be between 0 and 65535");
        }
    }
}
