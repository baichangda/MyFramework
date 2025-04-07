package cn.bcd.lib.data.init;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "lib.data.init")
public class InitializerProp {
    public String nacosHost;
    public int nacosPort;
    @NestedConfigurationProperty
    public ControlProp permission;
    @NestedConfigurationProperty
    public ControlProp transferConfig;
}
