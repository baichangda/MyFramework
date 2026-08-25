package cn.bcd.app.mqtt.server.config;

/**
 * Broker 运行时资源上限的不可变快照。
 *
 * <p>配置转换为该记录后再传入核心组件，避免运行期间被意外修改。</p>
 *
 * @param clientIds 可登记的客户端标识数量
 * @param subscriptionsPerSession 单个会话允许的订阅数
 * @param inflightMessagesPerSession 单个会话允许的飞行中消息数
 * @param offlineMessagesPerSession 单个离线会话允许排队的消息数
 * @param offlineQueueBytesPerSession 单个离线会话的排队消息总字节数
 * @param retainedMessages 保留消息数量
 * @param retainedMessageBytes 保留消息载荷总字节数
 */
public record MqttResourceLimits(
        int clientIds,
        int subscriptionsPerSession,
        int inflightMessagesPerSession,
        int offlineMessagesPerSession,
        long offlineQueueBytesPerSession,
        int retainedMessages,
        long retainedMessageBytes
) {
    /**
     * 校验所有资源上限以及 MQTT 报文标识符范围。
     *
     * @param clientIds 客户端标识数量上限
     * @param subscriptionsPerSession 单会话订阅数上限
     * @param inflightMessagesPerSession 单会话飞行中消息上限
     * @param offlineMessagesPerSession 单会话离线消息上限
     * @param offlineQueueBytesPerSession 单会话离线载荷字节上限
     * @param retainedMessages 保留消息数量上限
     * @param retainedMessageBytes 保留消息载荷字节上限
     */
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

    /** 创建使用服务默认配置的资源上限。 */
    public static MqttResourceLimits defaults() {
        return from(new MqttServerProperties.Limits());
    }

    /**
     * 将可变配置转换为不可变运行时上限。
     *
     * @param limits 资源限制配置
     */
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
