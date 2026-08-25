package cn.bcd.app.mqtt.server.session;

import java.util.List;
import java.util.Objects;

/**
 * 持久化 MQTT 会话所需状态的不可变快照。
 *
 * <p>集合在构造时复制，保证异步持久化期间不会随活动会话继续变化。</p>
 */
public record MqttSessionSnapshot(
        String clientId,
        String username,
        int nextPacketId,
        List<MqttSubscription> subscriptions,
        List<MqttPendingPublish> pendingPublishes,
        List<MqttInboundQosTwoPublish> inboundQosTwoPublishes
) {

    public MqttSessionSnapshot {
        Objects.requireNonNull(clientId);
        subscriptions = List.copyOf(subscriptions);
        pendingPublishes = List.copyOf(pendingPublishes);
        inboundQosTwoPublishes = List.copyOf(inboundQosTwoPublishes);
    }
}
