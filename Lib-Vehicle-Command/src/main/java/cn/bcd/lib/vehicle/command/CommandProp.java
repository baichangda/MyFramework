package cn.bcd.lib.vehicle.command;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lib.vehicle.command")
public class CommandProp {
    public boolean sender;
    public boolean receiver;
    public String requestTopic;
    public String responseTopic;
}
