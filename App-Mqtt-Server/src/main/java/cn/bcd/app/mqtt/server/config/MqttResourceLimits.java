package cn.bcd.app.mqtt.server.config;

public record MqttResourceLimits(
        int clientIds,
        int subscriptionsPerSession,
        int inflightMessagesPerSession,
        int offlineMessagesPerSession,
        long offlineQueueBytesPerSession,
        int retainedMessages,
        long retainedMessageBytes
) {
    public MqttResourceLimits {
        if (clientIds <= 0
                || subscriptionsPerSession <= 0
                || inflightMessagesPerSession <= 0
                || offlineMessagesPerSession <= 0
                || offlineQueueBytesPerSession <= 0
                || retainedMessages <= 0
                || retainedMessageBytes <= 0) {
            throw new IllegalArgumentException("MQTT resource limits must be greater than 0");
        }
        if (inflightMessagesPerSession > 65535
                || offlineMessagesPerSession > 65535) {
            throw new IllegalArgumentException(
                    "MQTT inflight and offline message limits must not exceed 65535");
        }
    }

    public static MqttResourceLimits defaults() {
        return from(new MqttServerProperties.Limits());
    }

    public static MqttResourceLimits from(MqttServerProperties.Limits limits) {
        return new MqttResourceLimits(
                limits.getClientIds(),
                limits.getSubscriptionsPerSession(),
                limits.getInflightMessagesPerSession(),
                limits.getOfflineMessagesPerSession(),
                limits.getOfflineQueueBytesPerSession(),
                limits.getRetainedMessages(),
                limits.getRetainedMessageBytes());
    }
}
