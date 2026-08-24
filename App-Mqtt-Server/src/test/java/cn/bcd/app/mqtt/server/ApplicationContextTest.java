package cn.bcd.app.mqtt.server;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.lifecycle.MqttServerLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "mqtt.server.bind-address=127.0.0.1",
        "mqtt.server.port=0"
})
class ApplicationContextTest {

    @Autowired
    MqttServerProperties properties;

    @Autowired
    MqttServerLifecycle lifecycle;

    @Test
    void shouldBindConfigurationAndStartLifecycle() {
        assertEquals("127.0.0.1", properties.getBindAddress());
        assertEquals(0, properties.getPort());
        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.getBoundPort() > 0);
    }
}
