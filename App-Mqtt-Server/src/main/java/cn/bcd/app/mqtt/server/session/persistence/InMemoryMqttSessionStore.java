package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 进程内持久会话存储，主要用于测试或无需跨重启恢复的部署场景。
 *
 * <p>所有复合状态由同步方法保护，返回的完成阶段均已完成。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.session",
        name = "type",
        havingValue = "memory")
public final class InMemoryMqttSessionStore implements MqttSessionStore {

    private final Map<String, SessionState> sessions = new LinkedHashMap<>();

    @Override
    public synchronized Collection<MqttSessionSnapshot> loadAll() {
        return sessions.values().stream().map(SessionState::snapshot).toList();
    }

    @Override
    public synchronized CompletionStage<Void> upsertSession(
            String clientId,
            String username,
            int nextPacketId) {
        sessions.compute(clientId, (key, current) -> current == null
                ? new SessionState(clientId, username, nextPacketId)
                : current.withMetadata(username, nextPacketId));
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> deleteSession(String clientId) {
        sessions.remove(clientId);
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> upsertSubscription(
            String clientId,
            MqttSubscription subscription) {
        sessions.get(clientId).subscriptions.put(subscription.topicFilter(), subscription);
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> deleteSubscriptions(
            String clientId,
            Collection<String> topicFilters) {
        SessionState state = sessions.get(clientId);
        if (state != null) {
            topicFilters.forEach(state.subscriptions::remove);
        }
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> upsertPendingPublish(
            String clientId,
            int nextPacketId,
            MqttPendingPublish pending) {
        SessionState state = sessions.get(clientId);
        state.nextPacketId = nextPacketId;
        state.pendingPublishes.put(pending.packetId(), pending);
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> deletePendingPublish(
            String clientId,
            int packetId) {
        SessionState state = sessions.get(clientId);
        if (state != null) {
            state.pendingPublishes.remove(packetId);
        }
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> upsertInboundQosTwo(
            String clientId,
            MqttInboundQosTwoPublish publish) {
        sessions.get(clientId).inboundPublishes.put(publish.packetId(), publish);
        return completed();
    }

    @Override
    public synchronized CompletionStage<Void> deleteInboundQosTwo(
            String clientId,
            int packetId) {
        SessionState state = sessions.get(clientId);
        if (state != null) {
            state.inboundPublishes.remove(packetId);
        }
        return completed();
    }

    private static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    private static final class SessionState {
        private final String clientId;
        private String username;
        private int nextPacketId;
        private final Map<String, MqttSubscription> subscriptions = new LinkedHashMap<>();
        private final Map<Integer, MqttPendingPublish> pendingPublishes = new LinkedHashMap<>();
        private final Map<Integer, MqttInboundQosTwoPublish> inboundPublishes =
                new LinkedHashMap<>();

        private SessionState(String clientId, String username, int nextPacketId) {
            this.clientId = clientId;
            this.username = username;
            this.nextPacketId = nextPacketId;
        }

        private SessionState withMetadata(String username, int nextPacketId) {
            this.username = username;
            this.nextPacketId = nextPacketId;
            return this;
        }

        private MqttSessionSnapshot snapshot() {
            return new MqttSessionSnapshot(
                    clientId,
                    username,
                    nextPacketId,
                    List.copyOf(subscriptions.values()),
                    List.copyOf(pendingPublishes.values()),
                    List.copyOf(inboundPublishes.values()));
        }
    }
}
