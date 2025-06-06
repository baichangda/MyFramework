package cn.bcd.lib.storage.iotdb;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "lib.storage.iotdb")
public class IotdbProp {
    public List<String> urls;
    public String user;
    public String password;
    public int maxSize;
}
