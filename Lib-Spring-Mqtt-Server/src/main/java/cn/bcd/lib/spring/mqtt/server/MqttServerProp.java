package cn.bcd.lib.spring.mqtt.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.spring.mqtt.server")
public class MqttServerProp {
    public boolean enabled = true;
    public String address = "0.0.0.0";
    public int port = 1883;
    public String dataPath = "data/hivemq";
}
