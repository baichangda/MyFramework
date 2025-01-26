package cn.bcd.dataProcess.gateway.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProp {
    public String id;
    public String mqttHost;
    public int mqttPort;
    public String mqttTopic;
    public String mqttRespTopicPrefix;
    public int mqttConsumeThreadNum;

    public Duration heartBeatPeriod;
    public String parseTopic;
}
