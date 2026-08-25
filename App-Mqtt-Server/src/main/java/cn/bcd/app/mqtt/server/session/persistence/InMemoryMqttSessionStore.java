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

    /** 返回全部内存会话的不可变快照。 */
    @Override
    public synchronized Collection<MqttSessionSnapshot> loadAll() {
        return sessions.values().stream().map(SessionState::snapshot).toList();
    }

    /**
     * 新增或更新会话元数据。
     *
     * @param clientId 客户端标识
     * @param username 用户名
     * @param nextPacketId 下一个报文标识符
     */
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

    /**
     * 删除客户端会话。
     *
     * @param clientId 客户端标识
     */
    @Override
    public synchronized CompletionStage<Void> deleteSession(String clientId) {
        sessions.remove(clientId);
        return completed();
    }

    /**
     * 新增或更新会话订阅。
     *
     * @param clientId 客户端标识
     * @param subscription 订阅内容
     */
    @Override
    public synchronized CompletionStage<Void> upsertSubscription(
            String clientId,
            MqttSubscription subscription) {
        sessions.get(clientId).subscriptions.put(subscription.topicFilter(), subscription);
        return completed();
    }

    /**
     * 批量删除会话订阅。
     *
     * @param clientId 客户端标识
     * @param topicFilters 主题过滤器集合
     */
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

    /**
     * 保存出站待确认消息和下一个 packetId。
     *
     * @param clientId 客户端标识
     * @param nextPacketId 下一个报文标识符
     * @param pending 待确认消息
     */
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

    /**
     * 删除出站待确认消息。
     *
     * @param clientId 客户端标识
     * @param packetId 报文标识符
     */
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

    /**
     * 保存入站 QoS 2 消息。
     *
     * @param clientId 客户端标识
     * @param publish 入站消息
     */
    @Override
    public synchronized CompletionStage<Void> upsertInboundQosTwo(
            String clientId,
            MqttInboundQosTwoPublish publish) {
        sessions.get(clientId).inboundPublishes.put(publish.packetId(), publish);
        return completed();
    }

    /**
     * 删除入站 QoS 2 消息。
     *
     * @param clientId 客户端标识
     * @param packetId 报文标识符
     */
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

    /** 返回已经成功完成的空异步阶段。 */
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

        /**
         * 创建内存会话状态。
         *
         * @param clientId 客户端标识
         * @param username 用户名
         * @param nextPacketId 下一个报文标识符
         */
        private SessionState(String clientId, String username, int nextPacketId) {
            this.clientId = clientId;
            this.username = username;
            this.nextPacketId = nextPacketId;
        }

        /**
         * 原位更新会话元数据并返回当前对象。
         *
         * @param username 用户名
         * @param nextPacketId 下一个报文标识符
         */
        private SessionState withMetadata(String username, int nextPacketId) {
            this.username = username;
            this.nextPacketId = nextPacketId;
            return this;
        }

        /** 将当前可变状态复制为不可变会话快照。 */
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
