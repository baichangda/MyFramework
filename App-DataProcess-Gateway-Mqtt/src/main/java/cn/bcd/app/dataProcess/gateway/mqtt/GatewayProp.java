package cn.bcd.app.dataProcess.gateway.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProp {
    public String id;
    public String serverHost;
    public int serverPort;
    public String clientId;
    public String topic;
    public int consumeThreadNum;
    public String responseTopicPrefix;
    public String sessionTopic;
    public String parseTopic;
}
