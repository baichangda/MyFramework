package cn.bcd.dataProcess.gateway.prop;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class TcpProp {
    public int port;
    public Duration maxBeforeOffline;
    public String sessionTopic;
}
