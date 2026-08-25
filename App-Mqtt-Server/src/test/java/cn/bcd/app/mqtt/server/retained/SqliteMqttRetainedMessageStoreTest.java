package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.config.MqttPersistenceProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMqttRetainedMessageStoreTest {

    @Test
    void shouldPersistRetainedMessageAcrossStoreRestartAndDeleteIt() throws IOException {
        Path databasePath = Path.of("build", "tmp", "mqtt-retained-"
                + UUID.randomUUID() + ".db");
        MqttPersistenceProperties properties = properties(databasePath);
        MqttApplicationMessage message = new MqttApplicationMessage(
                "sensor/room1/temperature", new byte[]{0x15}, MqttQoS.AT_LEAST_ONCE);

        try {
            try (SqliteMqttRetainedMessageStore first =
                         new SqliteMqttRetainedMessageStore(properties)) {
                first.save(message).toCompletableFuture().join();
            }

            try (SqliteMqttRetainedMessageStore second =
                         new SqliteMqttRetainedMessageStore(properties)) {
                MqttApplicationMessage restored = second.findMatching("sensor/+/temperature")
                        .stream()
                        .findFirst()
                        .orElseThrow();
                assertEquals(message.topicName(), restored.topicName());
                assertArrayEquals(message.payload(), restored.payload());
                assertEquals(MqttQoS.AT_LEAST_ONCE, restored.qos());
                second.delete(message.topicName()).toCompletableFuture().join();
            }

            try (SqliteMqttRetainedMessageStore third =
                         new SqliteMqttRetainedMessageStore(properties)) {
                assertTrue(third.findMatching("sensor/#").isEmpty());
            }
        } finally {
            Files.deleteIfExists(databasePath);
        }
    }

    private static MqttPersistenceProperties properties(Path databasePath) {
        MqttPersistenceProperties properties = new MqttPersistenceProperties();
        properties.getRetainedMessage().getSqlite().setDatabasePath(databasePath.toString());
        return properties;
    }
}
