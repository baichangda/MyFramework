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

    public MqttConnection(MqttBroker broker) {
        this(broker, new AnonymousMqttAuthenticator(), new AllowAllMqttAuthorizer());
    }

    public MqttConnection(MqttBroker broker, MqttAuthenticator authenticator) {
        this(broker, authenticator, new AllowAllMqttAuthorizer());
    }

    public MqttConnection(
            MqttBroker broker,
            MqttAuthenticator authenticator,
            MqttAuthorizer authorizer) {
        this.broker = Objects.requireNonNull(broker);
        connectFlow = new MqttConnectFlow(broker, authenticator, authorizer);
        subscriptionFlow = new MqttSubscriptionFlow(broker, authorizer);
        publishFlow = new MqttPublishFlow(broker, authorizer);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext context) {
        nettyContext = context;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, MqttMessage message) {
        if (message.decoderResult().isFailure()) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttMessageType messageType = message.fixedHeader().messageType();
        if (state == MqttConnectionState.NEW && messageType == MqttMessageType.CONNECT) {
            connectFlow.handle(this, (MqttConnectMessage) message);
            return;
        }
        if (state != MqttConnectionState.CONNECTED) {
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

    void accept(
            MqttConnectionContext connectionContext,
            MqttConnectResult connectResult,
            MqttWillMessage willMessage) {
        context = connectionContext;
        session = connectResult.session();
        pendingWill = willMessage;
        state = MqttConnectionState.CONNECTED;
        configureKeepAlive(connectionContext.keepAliveSeconds());
        write(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(connectResult.sessionPresent())
                .build());
    }

    void refuse(MqttConnectReturnCode returnCode) {
        recordCloseReason(MqttConnectionCloseReason.CONNECTION_REFUSED);
        state = MqttConnectionState.CLOSING;
        requiredNettyContext().writeAndFlush(MqttMessageBuilders.connAck()
                        .returnCode(returnCode)
                        .sessionPresent(false)
                        .build())
                .addListener(ChannelFutureListener.CLOSE);
    }

    void writeQosControlPacket(MqttMessageType messageType, int packetId) {
        MqttQoS headerQos = messageType == MqttMessageType.PUBREL
                ? MqttQoS.AT_LEAST_ONCE
                : MqttQoS.AT_MOST_ONCE;
        requiredNettyContext().writeAndFlush(new MqttMessage(
                new MqttFixedHeader(messageType, false, headerQos, false, 0),
                MqttMessageIdVariableHeader.from(packetId)));
    }

    void write(MqttMessage message) {
        requiredNettyContext().writeAndFlush(message);
    }

    <T> void onCompletion(CompletionStage<T> stage, Consumer<T> success) {
        stage.whenComplete((result, failure) -> {
            Runnable completion = () -> {
                if (failure != null) {
                    close(MqttConnectionCloseReason.INTERNAL_ERROR);
                } else if (channel().isActive()) {
                    success.accept(result);
                }
            };
            Channel channel = channel();
            if (channel.eventLoop().inEventLoop()) {
                completion.run();
            } else {
                channel.eventLoop().execute(completion);
            }
        });
    }

    private void configureKeepAlive(int keepAliveSeconds) {
        if (keepAliveSeconds == 0) {
            return;
        }
        long readerIdleMillis = keepAliveSeconds * 1500L;
        ChannelHandlerContext context = requiredNettyContext();
        context.pipeline().addBefore(context.name(), IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(readerIdleMillis, 0, 0, TimeUnit.MILLISECONDS));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.READER_IDLE) {
            close(MqttConnectionCloseReason.KEEP_ALIVE_TIMEOUT);
            return;
        }
        super.userEventTriggered(context, event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        recordCloseReason(MqttConnectionCloseReason.NETWORK_CLOSED);
        state = MqttConnectionState.CLOSED;
        broker.disconnect(this);
        publishPendingWill();
        super.channelInactive(context);
    }

    private void publishPendingWill() {
        MqttWillMessage willMessage = pendingWill;
        pendingWill = null;
        if (willMessage != null
                && closeReason != MqttConnectionCloseReason.NORMAL_DISCONNECT
                && closeReason != MqttConnectionCloseReason.CONNECTION_REFUSED) {
            broker.publishWill(willMessage);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        close(cause instanceof DecoderException
                ? MqttConnectionCloseReason.PROTOCOL_ERROR
                : MqttConnectionCloseReason.INTERNAL_ERROR);
    }

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

    public void sendPublish(
            MqttApplicationMessage message,
            int packetId,
            boolean retained,
            boolean duplicate) {
        Channel channel = channel();
        if (channel.eventLoop().inEventLoop()) {
            sendOnEventLoop(message, packetId, retained, duplicate);
        } else {
            channel.eventLoop().execute(
                    () -> sendOnEventLoop(message, packetId, retained, duplicate));
        }
    }

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
            publish.messageId(packetId);
        }
        MqttPublishMessage publishMessage = publish.build();
        if (duplicate) {
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

    public void close(MqttConnectionCloseReason reason) {
        Channel channel = channel();
        if (channel.eventLoop().inEventLoop()) {
            closeOnEventLoop(reason);
        } else {
            channel.eventLoop().execute(() -> closeOnEventLoop(reason));
        }
    }

    private void closeOnEventLoop(MqttConnectionCloseReason reason) {
        recordCloseReason(reason);
        if (state != MqttConnectionState.CLOSED) {
            state = MqttConnectionState.CLOSING;
        }
        channel().close();
    }

    private void recordCloseReason(MqttConnectionCloseReason reason) {
        if (closeReason == null) {
            closeReason = Objects.requireNonNull(reason);
        }
    }

    private ChannelHandlerContext requiredNettyContext() {
        return Objects.requireNonNull(
                nettyContext,
                "MQTT connection has not been added to a pipeline");
    }
}
