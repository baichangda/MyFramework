package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.authentication.AnonymousMqttAuthenticator;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import cn.bcd.app.mqtt.server.authorization.AllowAllMqttAuthorizer;
import cn.bcd.app.mqtt.server.authorization.MqttAuthorizer;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.broker.MqttConnectResult;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.session.MqttSession;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 单条 Netty 通道对应的 MQTT 协议处理器。
 *
 * <p>连接状态只在通道事件循环中推进；来自持久化或 Broker 的异步回调会切回事件循环，
 * 从而避免并发写通道及连接关闭竞态。</p>
 */
public final class MqttConnection extends SimpleChannelInboundHandler<MqttMessage> {

    static final String IDLE_STATE_HANDLER_NAME = "mqttIdleStateHandler";

    private final MqttBroker broker;
    private final MqttConnectFlow connectFlow;
    private final MqttSubscriptionFlow subscriptionFlow;
    private final MqttPublishFlow publishFlow;

    private volatile ChannelHandlerContext nettyContext;
    private volatile MqttConnectionContext context;
    private volatile MqttSession session;
    private volatile MqttWillMessage pendingWill;
    private volatile MqttConnectionCloseReason closeReason;
    private volatile MqttConnectionState state = MqttConnectionState.NEW;

    /**
     * 使用匿名认证和全量授权创建连接处理器。
     *
     * @param broker MQTT Broker
     */
    public MqttConnection(MqttBroker broker) {
        this(broker, new AnonymousMqttAuthenticator(), new AllowAllMqttAuthorizer());
    }

    /**
     * 使用指定认证器和全量授权创建连接处理器。
     *
     * @param broker MQTT Broker
     * @param authenticator 认证器
     */
    public MqttConnection(MqttBroker broker, MqttAuthenticator authenticator) {
        this(broker, authenticator, new AllowAllMqttAuthorizer());
    }

    /**
     * 使用指定 Broker、认证器和授权器创建连接处理器。
     *
     * @param broker MQTT Broker
     * @param authenticator 认证器
     * @param authorizer 授权器
     */
    public MqttConnection(
            MqttBroker broker,
            MqttAuthenticator authenticator,
            MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        connectFlow = new MqttConnectFlow(broker, authenticator, authorizer);
        subscriptionFlow = new MqttSubscriptionFlow(broker, authorizer);
        publishFlow = new MqttPublishFlow(broker, authorizer);
    }

    /**
     * 保存处理器对应的 Netty 上下文，供异步回调安全访问通道。
     *
     * @param context Netty 处理器上下文
     */
    @Override
    public void handlerAdded(ChannelHandlerContext context) {
        nettyContext = context;
    }

    /**
     * 按连接状态分派入站 MQTT 控制报文。
     *
     * @param context Netty 处理器上下文
     * @param message MQTT 控制报文
     */
    @Override
    protected void channelRead0(ChannelHandlerContext context, MqttMessage message) {
        if (message.decoderResult().isFailure()) {
            // 解码失败的报文内容不可信，不再尝试分派或返回应用层响应。
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttMessageType messageType = message.fixedHeader().messageType();
        // MQTT 规定客户端发送的第一个报文必须是 CONNECT，且一条连接只能发送一次。
        if (state == MqttConnectionState.NEW && messageType == MqttMessageType.CONNECT) {
            connectFlow.handle(this, (MqttConnectMessage) message);
            return;
        }
        if (state != MqttConnectionState.CONNECTED) {
            // CONNECT 完成前的其他报文，以及 CONNECT 成功后的第二个 CONNECT，均属协议错误。
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        switch (messageType) {
            case PINGREQ -> context.writeAndFlush(MqttMessage.PINGRESP);
            case SUBSCRIBE -> subscriptionFlow.subscribe(
                    this, (MqttSubscribeMessage) message);
            case UNSUBSCRIBE -> subscriptionFlow.unsubscribe(
                    this, (MqttUnsubscribeMessage) message);
            case PUBLISH -> publishFlow.publish(
                    this, (MqttPublishMessage) message);
            case PUBACK -> publishFlow.pubAck(this, (MqttPubAckMessage) message);
            case PUBREC -> publishFlow.pubRec(this, message);
            case PUBREL -> publishFlow.pubRel(this, message);
            case PUBCOMP -> publishFlow.pubComp(this, message);
            case DISCONNECT -> close(MqttConnectionCloseReason.NORMAL_DISCONNECT);
            default -> close(MqttConnectionCloseReason.PROTOCOL_ERROR);
        }
    }

    /**
     * 绑定已注册会话，配置保活并返回成功 CONNACK。
     *
     * @param connectionContext 连接上下文
     * @param connectResult Broker 连接结果
     * @param willMessage 遗嘱消息
     */
    void accept(
            MqttConnectionContext connectionContext,
            MqttConnectResult connectResult,
            MqttWillMessage willMessage) {
        context = connectionContext;
        session = connectResult.session();
        pendingWill = willMessage;
        // 必须先切换到 CONNECTED，再发送 CONNACK，避免客户端紧随其后的报文被误拒绝。
        state = MqttConnectionState.CONNECTED;
        configureKeepAlive(connectionContext.keepAliveSeconds());
        write(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(connectResult.sessionPresent())
                .build());
    }

    /**
     * 返回拒绝 CONNACK，并在写入完成后关闭连接。
     *
     * @param returnCode CONNACK 返回码
     */
    void refuse(MqttConnectReturnCode returnCode) {
        recordCloseReason(MqttConnectionCloseReason.CONNECTION_REFUSED);
        state = MqttConnectionState.CLOSING;
        requiredNettyContext().writeAndFlush(MqttMessageBuilders.connAck()
                        .returnCode(returnCode)
                        .sessionPresent(false)
                        .build())
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 写出 PUBREC、PUBREL 或 PUBCOMP 等仅携带 packetId 的控制报文。
     *
     * @param messageType 控制报文类型
     * @param packetId 报文标识符
     */
    void writeQosControlPacket(MqttMessageType messageType, int packetId) {
        // MQTT 规定 PUBREL 固定头 QoS 为 1，其余发布确认控制报文固定头 QoS 为 0。
        MqttQoS headerQos = messageType == MqttMessageType.PUBREL
                ? MqttQoS.AT_LEAST_ONCE
                : MqttQoS.AT_MOST_ONCE;
        requiredNettyContext().writeAndFlush(new MqttMessage(
                new MqttFixedHeader(messageType, false, headerQos, false, 0),
                MqttMessageIdVariableHeader.from(packetId)));
    }

    /**
     * 将 MQTT 报文写入当前通道。
     *
     * @param message MQTT 报文
     */
    void write(MqttMessage message) {
        requiredNettyContext().writeAndFlush(message);
    }

    /**
     * 将异步阶段的结果切回连接事件循环，并统一处理失败。
     *
     * @param stage 异步计算阶段
     * @param success 成功回调
     * @param <T> 异步结果类型
     */
    <T> void onCompletion(CompletionStage<T> stage, Consumer<T> success) {
        stage.whenComplete((result, failure) -> {
            Runnable completion = () -> {
                if (failure != null) {
                    // 持久化或路由失败后连接状态已不可安全继续，统一关闭连接。
                    close(MqttConnectionCloseReason.INTERNAL_ERROR);
                } else if (channel().isActive()) {
                    success.accept(result);
                }
            };
            // CompletionStage 可能在 SQLite 写线程完成，所有连接操作统一切回事件循环。
            Channel channel = channel();
            if (channel.eventLoop().inEventLoop()) {
                completion.run();
            } else {
                channel.eventLoop().execute(completion);
            }
        });
    }

    /**
     * 按 CONNECT 的 Keep Alive 值安装读空闲检测器。
     *
     * @param keepAliveSeconds 客户端声明的保活秒数
     */
    private void configureKeepAlive(int keepAliveSeconds) {
        if (keepAliveSeconds == 0) {
            return;
        }
        // MQTT 3.1.1 允许服务端在 1.5 倍 Keep Alive 周期未收到报文后断开连接。
        long readerIdleMillis = keepAliveSeconds * 1500L;
        ChannelHandlerContext context = requiredNettyContext();
        context.pipeline().addBefore(context.name(), IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(readerIdleMillis, 0, 0, TimeUnit.MILLISECONDS));
    }

    /**
     * 在读空闲超时时以保活超时原因关闭连接。
     *
     * @param context Netty 处理器上下文
     * @param event 用户事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.READER_IDLE) {
            close(MqttConnectionCloseReason.KEEP_ALIVE_TIMEOUT);
            return;
        }
        super.userEventTriggered(context, event);
    }

    /**
     * 通道失效后注销连接，并根据首个关闭原因决定是否发布遗嘱。
     *
     * @param context Netty 处理器上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        // 若此前没有显式关闭原因，则以网络关闭作为兜底原因。
        recordCloseReason(MqttConnectionCloseReason.NETWORK_CLOSED);
        state = MqttConnectionState.CLOSED;
        broker.disconnect(this);
        publishPendingWill();
        super.channelInactive(context);
    }

    /** 在非正常断开时至多发布一次待处理遗嘱。 */
    private void publishPendingWill() {
        MqttWillMessage willMessage = pendingWill;
        pendingWill = null;
        // 正常 DISCONNECT 和连接被拒绝不发布遗嘱，其余网络/协议/内部异常均需发布。
        if (willMessage != null
                && closeReason != MqttConnectionCloseReason.NORMAL_DISCONNECT
                && closeReason != MqttConnectionCloseReason.CONNECTION_REFUSED) {
            broker.publishWill(willMessage);
        }
    }

    /**
     * 将解码异常归类为协议错误，其余异常归类为内部错误。
     *
     * @param context Netty 处理器上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        close(cause instanceof DecoderException
                ? MqttConnectionCloseReason.PROTOCOL_ERROR
                : MqttConnectionCloseReason.INTERNAL_ERROR);
    }

    /** 返回连接所属 Netty 通道。 */
    public Channel channel() {
        return requiredNettyContext().channel();
    }

    public MqttConnectionContext context() {
        return context;
    }

    public MqttSession session() {
        return session;
    }

    public MqttConnectionCloseReason closeReason() {
        return closeReason;
    }

    public MqttConnectionState state() {
        return state;
    }

    /**
     * 在连接事件循环中发送应用消息。
     *
     * @param message 应用消息
     * @param packetId 报文标识符
     * @param retained 是否设置保留标志
     * @param duplicate 是否设置重复标志
     */
    public void sendPublish(
            MqttApplicationMessage message,
            int packetId,
            boolean retained,
            boolean duplicate) {
        // 路由器可从非 Netty 线程投递消息，因此显式切换到该连接的事件循环。
        Channel channel = channel();
        if (channel.eventLoop().inEventLoop()) {
            sendOnEventLoop(message, packetId, retained, duplicate);
        } else {
            channel.eventLoop().execute(
                    () -> sendOnEventLoop(message, packetId, retained, duplicate));
        }
    }

    /**
     * 构造 PUBLISH 报文，并在连接有效时执行实际写出。
     *
     * @param message 应用消息
     * @param packetId 报文标识符
     * @param retained 是否设置保留标志
     * @param duplicate 是否设置重复标志
     */
    private void sendOnEventLoop(
            MqttApplicationMessage message,
            int packetId,
            boolean retained,
            boolean duplicate) {
        if (state != MqttConnectionState.CONNECTED || !channel().isActive()) {
            return;
        }
        MqttMessageBuilders.PublishBuilder publish = MqttMessageBuilders.publish()
                .topicName(message.topicName())
                .qos(message.qos())
                .retained(retained)
                .payload(Unpooled.wrappedBuffer(message.payload()));
        if (message.qos() != MqttQoS.AT_MOST_ONCE) {
            // QoS 0 不允许携带 packetId，QoS 1/2 必须携带。
            publish.messageId(packetId);
        }
        MqttPublishMessage publishMessage = publish.build();
        if (duplicate) {
            // Netty 构造器不直接暴露 DUP 设置，复用变量头和载荷重建固定头。
            publishMessage = new MqttPublishMessage(
                    new MqttFixedHeader(
                            MqttMessageType.PUBLISH,
                            true,
                            publishMessage.fixedHeader().qosLevel(),
                            publishMessage.fixedHeader().isRetain(),
                            0),
                    publishMessage.variableHeader(),
                    publishMessage.payload());
        }
        channel().writeAndFlush(publishMessage);
    }

    /**
     * 使用指定原因在事件循环中关闭连接。
     *
     * @param reason 关闭原因
     */
    public void close(MqttConnectionCloseReason reason) {
        Channel channel = channel();
        // close 可由持久化线程调用，实际状态转换始终限制在事件循环内。
        if (channel.eventLoop().inEventLoop()) {
            closeOnEventLoop(reason);
        } else {
            channel.eventLoop().execute(() -> closeOnEventLoop(reason));
        }
    }

    /**
     * 记录关闭原因、推进状态并关闭通道。
     *
     * @param reason 关闭原因
     */
    private void closeOnEventLoop(MqttConnectionCloseReason reason) {
        recordCloseReason(reason);
        if (state != MqttConnectionState.CLOSED) {
            state = MqttConnectionState.CLOSING;
        }
        channel().close();
    }

    /**
     * 仅记录最先触发的关闭原因。
     *
     * @param reason 关闭原因
     */
    private void recordCloseReason(MqttConnectionCloseReason reason) {
        // 保留首个关闭原因，避免 channelInactive 的 NETWORK_CLOSED 覆盖真实触发原因。
        if (closeReason == null) {
            closeReason = Objects.requireNonNull(reason);
        }
    }

    /** 返回已绑定的 Netty 上下文；处理器尚未入管线时抛出异常。 */
    private ChannelHandlerContext requiredNettyContext() {
        return Objects.requireNonNull(
                nettyContext,
                "MQTT connection has not been added to a pipeline");
    }
}
