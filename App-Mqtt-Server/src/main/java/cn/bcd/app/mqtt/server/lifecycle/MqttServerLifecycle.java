package cn.bcd.app.mqtt.server.lifecycle;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.netty.MqttServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class MqttServerLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MqttServerLifecycle.class);

    private final MqttServerProperties properties;
    private final MqttServer mqttServer;

    public MqttServerLifecycle(MqttServerProperties properties, MqttServer mqttServer) {
        this.properties = properties;
        this.mqttServer = mqttServer;
    }

    @Override
    public void start() {
        mqttServer.start();
        logger.info("MQTT server started, bindAddress[{}] port[{}]",
                properties.getBindAddress(), mqttServer.getBoundPort());
    }

    @Override
    public void stop() {
        mqttServer.stop();
        logger.info("MQTT server stopped");
    }

    @Override
    public boolean isRunning() {
        return mqttServer.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled();
    }

    public int getBoundPort() {
        return mqttServer.getBoundPort();
    }
}
