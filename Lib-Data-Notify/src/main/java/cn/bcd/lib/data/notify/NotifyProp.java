package cn.bcd.lib.data.notify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.data.notify")
public class NotifyProp {
    public NodeProp vehicleData;
}
