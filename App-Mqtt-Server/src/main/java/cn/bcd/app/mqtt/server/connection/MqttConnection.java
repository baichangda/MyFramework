package cn.bcd.app.mqtt.server.connection;

import io.netty.channel.Channel;

import java.util.Objects;

public final class MqttConnection {

    private final Channel channel;

    private volatile MqttConnectionContext context;
    private volatile MqttConnectionRegistration registration;
    private volatile MqttConnectionCloseReason closeReason;

    public MqttConnection(Channel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    public Channel channel() {
        return channel;
    }

    public MqttConnectionContext context() {
        return context;
    }

    public void establish(MqttConnectionContext context) {
        assertInEventLoop();
        if (this.context != null) {
            throw new IllegalStateException("MQTT connection is already established");
        }
        this.context = Objects.requireNonNull(context);
    }

    public MqttConnectionRegistration registration() {
        return registration;
    }

    void registration(MqttConnectionRegistration registration) {
        this.registration = registration;
    }

    public MqttConnectionCloseReason closeReason() {
        return closeReason;
    }

    public void recordCloseReason(MqttConnectionCloseReason reason) {
        assertInEventLoop();
        if (closeReason == null) {
            closeReason = Objects.requireNonNull(reason);
        }
    }

    public void close(MqttConnectionCloseReason reason) {
        if (channel.eventLoop().inEventLoop()) {
            closeOnEventLoop(reason);
        } else {
            channel.eventLoop().execute(() -> closeOnEventLoop(reason));
        }
    }

    private void closeOnEventLoop(MqttConnectionCloseReason reason) {
        recordCloseReason(reason);
        channel.close();
    }

    private void assertInEventLoop() {
        if (!channel.eventLoop().inEventLoop()) {
            throw new IllegalStateException("MQTT connection state must be changed on its EventLoop");
        }
    }
}
