package cn.bcd.app.dataProcess.gateway.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @Author：Liqi
 * @CreateTime：2025-01-22
 * @Description：TODO
 **/
@Data
@ConfigurationProperties(prefix = "mqtt")
public class MqttProp {

    private String serverHost;

    private int serverPort;

    private String clientId;

    private String topic;

    private int consumeThreadNum;

    private String responseTopicPrefix;
}
