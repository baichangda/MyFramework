package cn.bcd.lib.data.init;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.data.init")
public class InitializerProp {
    public String nacosHost;
    public int nacosPort;
    public ControlProp permission;
}
