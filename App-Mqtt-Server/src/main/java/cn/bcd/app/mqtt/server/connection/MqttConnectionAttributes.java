package cn.bcd.app.mqtt.server.connection;

import io.netty.util.AttributeKey;

public final class MqttConnectionAttributes {

    public static final AttributeKey<MqttConnectionContext> CONNECTION_CONTEXT =
            AttributeKey.valueOf(MqttConnectionAttributes.class, "connectionContext");

    private MqttConnectionAttributes() {
    }
}
