package cn.bcd.app.mqtt.server.handler;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import org.springframework.stereotype.Component;

@Component
public class MqttDisconnectHandler {

    public void handle(MqttConnection connection) {
        connection.close(MqttConnectionCloseReason.NORMAL_DISCONNECT);
    }
}
