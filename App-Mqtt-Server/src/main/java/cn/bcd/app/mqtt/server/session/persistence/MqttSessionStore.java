package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public interface MqttSessionStore {

    Collection<MqttSessionSnapshot> loadAll();

    CompletionStage<Void> upsertSession(
            String clientId,
            String username,
            int nextPacketId);

    CompletionStage<Void> deleteSession(String clientId);

    CompletionStage<Void> upsertSubscription(
            String clientId,
            MqttSubscription subscription);

    CompletionStage<Void> deleteSubscriptions(
            String clientId,
            Collection<String> topicFilters);

    CompletionStage<Void> upsertPendingPublish(
            String clientId,
            int nextPacketId,
            MqttPendingPublish pending);

    CompletionStage<Void> deletePendingPublish(String clientId, int packetId);

    CompletionStage<Void> upsertInboundQosTwo(
            String clientId,
            MqttInboundQosTwoPublish publish);

    CompletionStage<Void> deleteInboundQosTwo(String clientId, int packetId);
}
