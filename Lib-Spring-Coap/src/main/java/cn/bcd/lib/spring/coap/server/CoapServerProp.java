package cn.bcd.lib.spring.coap.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.spring.coap.server")
public class CoapServerProp {
    private String host;

    private int port = 5683;

    private int protocolThreadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
}
