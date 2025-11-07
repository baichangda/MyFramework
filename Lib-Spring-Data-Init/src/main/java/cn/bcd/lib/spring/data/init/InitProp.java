package cn.bcd.lib.spring.data.init;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "lib.spring.data.init")
public class InitProp {
    public String nacosHost;
    public int nacosPort;
    @NestedConfigurationProperty
    public ControlProp permission;
    @NestedConfigurationProperty
    public ControlProp transferConfig;
}
