package cn.bcd.lib.data.notify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "lib.data.notify")
public class NotifyProp {
    @NestedConfigurationProperty
    public NodeProp vehicleData;
    @NestedConfigurationProperty
    public NodeProp platformStatus;
}
