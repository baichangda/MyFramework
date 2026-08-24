package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.broker.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.broker.MqttConnectResult;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MqttConnection extends SimpleChannelInboundHandler<MqttMessage> {

    static final String IDLE_STATE_HANDLER_NAME = "mqttIdleStateHandler";

    private final MqttBroker broker;

    private volatile ChannelHandlerContext nettyContext;
    private volatile MqttConnectionContext context;
    private volatile MqttSession session;
    private volatile MqttConnectionCloseReason closeReason;
    private volatile MqttConnectionState state = MqttConnectionState.NEW;

    public MqttConnection(MqttBroker broker) {
        this.broker = Objects.requireNonNull(broker);
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
            onConnect(context, (MqttConnectMessage) message);
            return;
        }
        if (state != MqttConnectionState.CONNECTED) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        switch (messageType) {
            case PINGREQ -> context.writeAndFlush(MqttMessage.PINGRESP);
            case SUBSCRIBE -> onSubscribe(context, (MqttSubscribeMessage) message);
            case PUBLISH -> onPublish((MqttPublishMessage) message);
            case DISCONNECT -> close(MqttConnectionCloseReason.NORMAL_DISCONNECT);
            default -> close(MqttConnectionCloseReason.PROTOCOL_ERROR);
        }
    }

    private void onPublish(MqttPublishMessage message) {
        if (message.fixedHeader().qosLevel() != MqttQoS.AT_MOST_ONCE
                || message.fixedHeader().isRetain()) {
            close(MqttConnectionCloseReason.UNSUPPORTED_FEATURE);
            return;
        }
        if (message.fixedHeader().isDup()) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        String topicName = message.variableHeader().topicName();
        if (topicName == null || topicName.isEmpty() || containsWildcard(topicName)) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttApplicationMessage applicationMessage = new MqttApplicationMessage(
                topicName, ByteBufUtil.getBytes(message.payload()));
        if (!broker.publish(this, applicationMessage)) {
            close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
        }
    }

    private void onSubscribe(ChannelHandlerContext context, MqttSubscribeMessage message) {
        int packetId = message.variableHeader().messageId();
        List<MqttTopicSubscription> requests = message.payload().topicSubscriptions();
        if (packetId == 0 || requests.isEmpty()
                || message.fixedHeader().qosLevel() != MqttQoS.AT_LEAST_ONCE
                || message.fixedHeader().isRetain()) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttMessageBuilders.SubAckBuilder subAck = MqttMessageBuilders.subAck().packetId(packetId);
        for (MqttTopicSubscription request : requests) {
            String topicName = request.topicFilter();
            MqttQoS requestedQos = request.qualityOfService();
            if (topicName == null || topicName.isEmpty() || !isSubscriptionQos(requestedQos)) {
                close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                return;
            }
            if (containsWildcard(topicName)) {
                subAck.addGrantedQos(MqttQoS.FAILURE);
                continue;
            }
            if (!broker.subscribe(this, new MqttSubscription(topicName, requestedQos))) {
                close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
            subAck.addGrantedQos(requestedQos);
        }
        context.writeAndFlush(subAck.build());
    }

    private static boolean isSubscriptionQos(MqttQoS qos) {
        return qos == MqttQoS.AT_MOST_ONCE
                || qos == MqttQoS.AT_LEAST_ONCE
                || qos == MqttQoS.EXACTLY_ONCE;
    }

    private static boolean containsWildcard(String topicName) {
        return topicName.indexOf('+') >= 0 || topicName.indexOf('#') >= 0;
    }

    private void onConnect(ChannelHandlerContext nettyContext, MqttConnectMessage message) {
        int protocolVersion = message.variableHeader().version();
        if (protocolVersion != MqttVersion.MQTT_3_1_1.protocolLevel()) {
            refuse(nettyContext, protocolVersion == MqttVersion.MQTT_5.protocolLevel()
                    ? MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION
                    : MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }

        String clientId = message.payload().clientIdentifier();
        if (clientId == null || clientId.isEmpty()) {
            refuse(nettyContext, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }

        MqttConnectionContext connectionContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                message.payload().userName());
        MqttConnectResult connectResult = broker.connect(
                this, connectionContext.clientId(), connectionContext.cleanSession());
        context = connectionContext;
        session = connectResult.session();
        state = MqttConnectionState.CONNECTED;
        configureKeepAlive(nettyContext, connectionContext.keepAliveSeconds());
        nettyContext.writeAndFlush(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(connectResult.sessionPresent())
                .build());
    }

    private void configureKeepAlive(ChannelHandlerContext context, int keepAliveSeconds) {
        if (keepAliveSeconds == 0) {
            return;
        }
        long readerIdleMillis = keepAliveSeconds * 1500L;
        context.pipeline().addBefore(context.name(), IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(readerIdleMillis, 0, 0, TimeUnit.MILLISECONDS));
    }

    private void refuse(ChannelHandlerContext context, MqttConnectReturnCode returnCode) {
        recordCloseReason(MqttConnectionCloseReason.CONNECTION_REFUSED);
        state = MqttConnectionState.CLOSING;
        context.writeAndFlush(MqttMessageBuilders.connAck()
                        .returnCode(returnCode)
                        .sessionPresent(false)
                        .build())
                .addListener(ChannelFutureListener.CLOSE);
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
        super.channelInactive(context);
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

    public void sendPublish(MqttApplicationMessage message) {
        Channel channel = channel();
        if (channel.eventLoop().inEventLoop()) {
            sendOnEventLoop(message);
        } else {
            channel.eventLoop().execute(() -> sendOnEventLoop(message));
        }
    }

    private void sendOnEventLoop(MqttApplicationMessage message) {
        if (state != MqttConnectionState.CONNECTED || !channel().isActive()) {
            return;
        }
        channel().writeAndFlush(MqttMessageBuilders.publish()
                .topicName(message.topicName())
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .payload(Unpooled.wrappedBuffer(message.payload()))
                .build());
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
        return Objects.requireNonNull(nettyContext, "MQTT connection has not been added to a pipeline");
    }
}
