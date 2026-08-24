package cn.bcd.app.mqtt.server.session;

import java.util.List;
import java.util.Objects;

public record MqttSessionSnapshot(
        String clientId,
        String username,
        int nextPacketId,
        List<MqttSubscription> subscriptions,
        List<MqttPendingPublishSnapshot> pendingPublishes,
        List<MqttInboundQosTwoPublish> inboundQosTwoPublishes
) {

    public MqttSessionSnapshot {
        Objects.requireNonNull(clientId);
        subscriptions = List.copyOf(subscriptions);
        pendingPublishes = List.copyOf(pendingPublishes);
        inboundQosTwoPublishes = List.copyOf(inboundQosTwoPublishes);
    }
}
