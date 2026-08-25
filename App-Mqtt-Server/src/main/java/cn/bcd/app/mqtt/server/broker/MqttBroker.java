package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.config.MqttResourceLimits;
import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.retained.MqttRetainedMessageStore;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.persistence.MqttSessionStore;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * MQTT Broker 的协议无关门面，协调客户端会话、订阅索引、消息路由和持久化。
 */
@Component
public class MqttBroker {

    private final MqttClientRegistry clients;
    private final MqttMessageRouter router;

    /**
     * 使用默认资源上限创建 Broker，主要供测试直接构造。
     *
     * @param retainedMessageStore 保留消息存储
     * @param sessionStore 会话存储
     */
    public MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore) {
        this(retainedMessageStore, sessionStore, MqttResourceLimits.defaults());
    }

    /**
     * 使用服务配置中的资源上限创建 Broker。
     *
     * @param retainedMessageStore 保留消息存储
     * @param sessionStore 会话存储
     * @param properties 服务配置
     */
    @Autowired
    public MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore,
            MqttServerProperties properties) {
        this(retainedMessageStore, sessionStore, MqttResourceLimits.from(properties.getLimits()));
    }

    /**
     * 创建客户端注册表和消息路由器。
     *
     * @param retainedMessageStore 保留消息存储
     * @param sessionStore 会话存储
     * @param limits 资源上限
     */
    private MqttBroker(
            MqttRetainedMessageStore retainedMessageStore,
            MqttSessionStore sessionStore,
            MqttResourceLimits limits) {
        clients = new MqttClientRegistry(sessionStore, limits);
        router = new MqttMessageRouter(clients, retainedMessageStore);
    }

    /**
     * 注册连接，并在 clientId 被接管时关闭旧连接。
     *
     * @param connection 新连接
     * @param clientId 客户端标识
     * @param username 用户名
     * @param cleanSession 是否清理旧会话
     */
    public CompletionStage<MqttConnectResult> connect(
            MqttConnection connection,
            String clientId,
            String username,
            boolean cleanSession) {
        return clients.connect(connection, clientId, username, cleanSession)
                .thenApply(registration -> {
                    if (!registration.result().accepted()) {
                        return registration.result();
                    }
                    // 相同 clientId 的新连接接管会话后，旧连接必须被主动关闭。
                    MqttConnection previous = registration.previousConnection();
                    if (previous != null && previous != connection) {
                        previous.close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                    }
                    return registration.result();
                });
    }

    /**
     * 将连接从活动客户端中注销，并按会话类型决定保留或删除状态。
     *
     * @param connection 待注销连接
     */
    public void disconnect(MqttConnection connection) {
        clients.disconnect(connection);
    }

    /**
     * 登记订阅并返回首次订阅需要发送的保留消息。
     *
     * @param connection 当前连接
     * @param subscription 订阅内容
     */
    public CompletionStage<MqttSubscribeResult> subscribe(
            MqttConnection connection,
            MqttSubscription subscription) {
        return clients.subscribe(connection, subscription)
                .thenApply(result -> switch (result) {
                    case ACCEPTED -> new MqttSubscribeResult(
                            true, true, router.findRetained(subscription.topicFilter()));
                    case REJECTED -> new MqttSubscribeResult(true, false, List.of());
                    case NOT_CURRENT -> new MqttSubscribeResult(false, false, List.of());
                });
    }

    /**
     * 批量取消当前连接的主题订阅。
     *
     * @param connection 当前连接
     * @param topicFilters 主题过滤器集合
     */
    public CompletionStage<Boolean> unsubscribe(
            MqttConnection connection,
            Collection<String> topicFilters) {
        return clients.unsubscribe(connection, topicFilters);
    }

    /**
     * 校验连接所有权后路由客户端发布的消息。
     *
     * @param publisher 发布连接
     * @param message 应用消息
     * @param retained 是否更新保留消息
     */
    public CompletionStage<Boolean> publish(
            MqttConnection publisher,
            MqttApplicationMessage message,
            boolean retained) {
        if (!clients.isCurrent(publisher)) {
            return CompletableFuture.completedFuture(false);
        }
        return router.publish(message, retained).thenApply(ignored -> true);
    }

    /**
     * 路由异常断开客户端的遗嘱消息。
     *
     * @param willMessage 遗嘱消息
     */
    public void publishWill(MqttWillMessage willMessage) {
        router.publishWill(willMessage);
    }

    /**
     * 向刚完成订阅的当前连接投递一条保留消息。
     *
     * @param connection 目标连接
     * @param message 保留消息
     */
    public CompletionStage<Boolean> deliverRetained(
            MqttConnection connection,
            MqttApplicationMessage message) {
        return router.deliverRetained(connection, message);
    }

    /**
     * 处理客户端对出站 QoS 1 消息的 PUBACK。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    public void acknowledge(MqttConnection connection, int packetId) {
        clients.acknowledge(connection, packetId);
    }

    /**
     * 登记等待 PUBREL 的入站 QoS 2 消息。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     * @param message 应用消息
     * @param retained 是否设置保留标志
     * @param duplicate 是否为重复报文
     */
    public CompletionStage<MqttInboundPublishStatus> receiveQosTwo(
            MqttConnection connection,
            int packetId,
            MqttApplicationMessage message,
            boolean retained,
            boolean duplicate) {
        return clients.receiveQosTwo(
                connection, packetId, message, retained, duplicate);
    }

    /**
     * 释放并路由入站 QoS 2 消息。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    public CompletionStage<Boolean> releaseQosTwo(
            MqttConnection connection,
            int packetId) {
        if (!clients.isCurrent(connection)) {
            return CompletableFuture.completedFuture(false);
        }
        // 先从会话中释放并持久化删除，再路由载荷，防止重复 PUBREL 导致重复发布。
        return clients.releaseQosTwo(connection, packetId)
                .thenCompose(pending -> pending
                        .map(message -> router.publish(
                                message.message(), message.retained()))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)))
                .thenApply(ignored -> true);
    }

    /**
     * 处理出站 QoS 2 消息的 PUBREC 并推进状态。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    public CompletionStage<Optional<MqttPendingPublish>> receivePubRec(
            MqttConnection connection,
            int packetId) {
        return clients.receivePubRec(connection, packetId);
    }

    /**
     * 完成出站 QoS 2 消息的确认流程。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    public void receivePubComp(MqttConnection connection, int packetId) {
        clients.receivePubComp(connection, packetId);
    }

    /**
     * 返回当前连接所属会话的待确认消息快照。
     *
     * @param connection 当前连接
     */
    public Collection<MqttPendingPublish> pendingPublishes(
            MqttConnection connection) {
        return clients.pendingPublishes(connection);
    }

    /**
     * 记录待确认消息已经交给连接发送。
     *
     * @param connection 当前连接
     * @param packetId 报文标识符
     */
    public CompletionStage<Void> markPendingPublishSent(
            MqttConnection connection,
            int packetId) {
        return clients.markPendingPublishSent(connection, packetId);
    }

    /**
     * 按 clientId 查找活动连接。
     *
     * @param clientId 客户端标识
     */
    public Optional<MqttConnection> findConnection(String clientId) {
        return clients.findConnection(clientId);
    }

    /**
     * 按 clientId 查找活动或离线持久会话。
     *
     * @param clientId 客户端标识
     */
    public Optional<MqttSession> findSession(String clientId) {
        return clients.findSession(clientId);
    }

    /** 返回已登记的客户端标识数量。 */
    public int size() {
        return clients.size();
    }
}
