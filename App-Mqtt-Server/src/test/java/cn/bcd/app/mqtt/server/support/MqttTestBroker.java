package cn.bcd.app.mqtt.server.support;

import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.retained.InMemoryMqttRetainedMessageStore;

public final class MqttTestBroker {

    private MqttTestBroker() {
    }

    public static MqttBroker create() {
        return new MqttBroker(new InMemoryMqttRetainedMessageStore());
    }
}
