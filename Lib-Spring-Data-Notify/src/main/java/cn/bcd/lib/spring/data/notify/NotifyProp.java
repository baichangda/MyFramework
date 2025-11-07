package cn.bcd.lib.spring.data.notify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "lib.spring.data.notify")
public class NotifyProp {
    @NestedConfigurationProperty
    public NodeProp vehicleData;
    @NestedConfigurationProperty
    public NodeProp platformStatus;
    @NestedConfigurationProperty
    public NodeProp transferAccess;
}
