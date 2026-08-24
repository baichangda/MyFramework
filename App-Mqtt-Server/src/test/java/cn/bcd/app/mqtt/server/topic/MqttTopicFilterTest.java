package cn.bcd.app.mqtt.server.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTopicFilterTest {

    @Test
    void shouldValidateWildcardPlacement() {
        assertTrue(MqttTopicFilter.isValid("sensor/+/temperature"));
        assertTrue(MqttTopicFilter.isValid("sensor/#"));
        assertTrue(MqttTopicFilter.isValid("#"));
        assertFalse(MqttTopicFilter.isValid(""));
        assertFalse(MqttTopicFilter.isValid("sensor+"));
        assertFalse(MqttTopicFilter.isValid("sensor/#/temperature"));
        assertFalse(MqttTopicFilter.isValid("sensor/tem#"));
    }

    @Test
    void shouldMatchSingleAndMultiLevelWildcards() {
        assertTrue(MqttTopicFilter.matches("sensor/+/temperature", "sensor/room1/temperature"));
        assertFalse(MqttTopicFilter.matches("sensor/+/temperature", "sensor/room1/humidity"));
        assertTrue(MqttTopicFilter.matches("sensor/#", "sensor"));
        assertTrue(MqttTopicFilter.matches("sensor/#", "sensor/room1/temperature"));
        assertTrue(MqttTopicFilter.matches("sensor/+", "sensor/"));
        assertFalse(MqttTopicFilter.matches("sensor/+", "sensor"));
    }

    @Test
    void shouldNotMatchSystemTopicFromRootWildcard() {
        assertFalse(MqttTopicFilter.matches("#", "$SYS/broker/uptime"));
        assertFalse(MqttTopicFilter.matches("+/broker/uptime", "$SYS/broker/uptime"));
        assertTrue(MqttTopicFilter.matches("$SYS/#", "$SYS/broker/uptime"));
    }
}
