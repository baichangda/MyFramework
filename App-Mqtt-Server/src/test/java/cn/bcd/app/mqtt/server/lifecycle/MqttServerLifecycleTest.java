package cn.bcd.app.mqtt.server.lifecycle;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttServerLifecycleTest {

    @Test
    void shouldStartAndStopIdempotently() {
        MqttServerLifecycle lifecycle = new MqttServerLifecycle(new MqttServerProperties());

        lifecycle.start();
        lifecycle.start();
        assertTrue(lifecycle.isRunning());

        lifecycle.stop();
        lifecycle.stop();
        assertFalse(lifecycle.isRunning());
    }
}
