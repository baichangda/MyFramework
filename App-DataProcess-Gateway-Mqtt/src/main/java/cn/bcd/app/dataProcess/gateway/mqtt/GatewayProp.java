package cn.bcd.app.dataProcess.gateway.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProp {
    public String id;
    public String sessionTopic;
    public String parseTopic;

    public String mqttServerHost;
    public int mqttServerPort;
    public String mqttClientId;
    public String mqttConsumeTopic;
    public int mqttConsumeThreadNum;
    public String mqttProduceTopicPrefix;

}
