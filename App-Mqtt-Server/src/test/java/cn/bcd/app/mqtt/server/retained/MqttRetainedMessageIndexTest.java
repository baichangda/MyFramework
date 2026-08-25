package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttRetainedMessageIndexTest {

    @Test
    void shouldFindExactSingleAndMultiLevelMatches() {
        MqttRetainedMessageIndex index = new MqttRetainedMessageIndex();
        index.put(message("sensor/room1/temp", "21"));
        index.put(message("sensor/room2/temp", "22"));
        index.put(message("sensor/status", "online"));

        assertEquals(Set.of("sensor/room1/temp", "sensor/room2/temp"),
                topics(index, "sensor/+/temp"));
        assertEquals(Set.of(
                        "sensor/room1/temp", "sensor/room2/temp", "sensor/status"),
                topics(index, "sensor/#"));
        assertEquals(Set.of("sensor/status"), topics(index, "sensor/status"));
    }

    @Test
    void shouldExcludeSystemTopicsFromRootWildcards() {
        MqttRetainedMessageIndex index = new MqttRetainedMessageIndex();
        index.put(message("sensor/status", "online"));
        index.put(message("$SYS/broker/uptime", "10"));

        assertEquals(Set.of("sensor/status"), topics(index, "#"));
        assertTrue(topics(index, "+/broker/uptime").isEmpty());
        assertEquals(Set.of("$SYS/broker/uptime"), topics(index, "$SYS/#"));
    }

    @Test
    void shouldOverwriteAndDeleteMessage() {
        MqttRetainedMessageIndex index = new MqttRetainedMessageIndex();
        index.put(message("sensor/status", "offline"));
        index.put(message("sensor/status", "online"));

        assertEquals("online", new String(index.findMatching("sensor/status")
                .getFirst().payload(), StandardCharsets.UTF_8));

        index.remove("sensor/status");
        assertTrue(index.findMatching("sensor/#").isEmpty());
    }

    @Test
    void shouldLimitRetainedMessageCountAndPayloadBytes() {
        MqttRetainedMessageIndex index = new MqttRetainedMessageIndex(1, 6);
        index.put(message("sensor/one", "1234"));

        assertThrows(BaseException.class,
                () -> index.put(message("sensor/two", "1")));
        assertThrows(BaseException.class,
                () -> index.put(message("sensor/one", "1234567")));
        assertEquals(1, index.size());
        assertEquals(4, index.payloadBytes());

        index.put(message("sensor/one", "123456"));
        assertEquals(6, index.payloadBytes());
        index.remove("sensor/one");
        assertEquals(0, index.size());
        assertEquals(0, index.payloadBytes());
    }

    private static Set<String> topics(
            MqttRetainedMessageIndex index,
            String filter) {
        return index.findMatching(filter).stream()
                .map(MqttApplicationMessage::topicName)
                .collect(Collectors.toSet());
    }

    private static MqttApplicationMessage message(String topic, String payload) {
        return new MqttApplicationMessage(
                topic, payload.getBytes(StandardCharsets.UTF_8), MqttQoS.AT_LEAST_ONCE);
    }
}
