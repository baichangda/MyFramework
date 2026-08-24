package cn.bcd.app.mqtt.server.lifecycle;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MqttServerLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MqttServerLifecycle.class);

    private final MqttServerProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public MqttServerLifecycle(MqttServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("MQTT server lifecycle started, bindAddress[{}] port[{}]",
                    properties.getBindAddress(), properties.getPort());
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MQTT server lifecycle stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled();
    }
}
