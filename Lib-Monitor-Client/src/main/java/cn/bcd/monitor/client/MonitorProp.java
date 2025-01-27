package cn.bcd.monitor.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "monitor")
public class MonitorProp {
    public String monitorRequestTopic;
    public String monitorResponseList;
}
