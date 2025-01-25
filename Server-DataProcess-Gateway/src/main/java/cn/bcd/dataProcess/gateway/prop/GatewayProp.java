package cn.bcd.dataProcess.gateway.prop;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProp {
    public String id;
    public TcpProp tcp;
    public String parseTopic;
}
