package cn.bcd.monitor.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "monitor")
public class MonitorProp {
    public String requestTopic;
    public String responseList;

    //客户端属性
    public String serverId;
    public int serverType;

    //服务端属性
    public String collectCron;
}
