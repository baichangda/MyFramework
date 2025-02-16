package cn.bcd.server.dataProcess.gateway.tcp;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProp {
    public String id;
    public int tcpPort;
    public Duration heartBeatPeriod;
    public String sessionTopic;
    public String parseTopic;
}
