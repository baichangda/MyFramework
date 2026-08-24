package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.authentication.AnonymousMqttAuthenticator;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticationRequest;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.broker.MqttConnectResult;
import cn.bcd.app.mqtt.server.broker.MqttSubscribeResult;
import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.app.mqtt.server.message.MqttWillMessage;
import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.session.MqttInboundPublishStatus;
import cn.bcd.app.mqtt.server.session.MqttOutboundPublishState;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import io.netty.buffer.ByteBufUtil;
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
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MqttConnection extends SimpleChannelInboundHandler<MqttMessage> {

    static final String IDLE_STATE_HANDLER_NAME = "mqttIdleStateHandler";

    private final MqttBroker broker;
    private final MqttAuthenticator authenticator;

    private volatile ChannelHandlerContext nettyContext;
    private volatile MqttConnectionContext context;
    private volatile MqttSession session;
    private volatile MqttWillMessage pendingWill;
    private volatile MqttConnectionCloseReason closeReason;
    private volatile MqttConnectionState state = MqttConnectionState.NEW;

    public MqttConnection(MqttBroker broker) {
        this(broker, new AnonymousMqttAuthenticator());
    }

    public MqttConnection(MqttBroker broker, MqttAuthenticator authenticator) {
        this.broker = Objects.requireNonNull(broker);
        this.authenticator = Objects.requireNonNull(authenticator);
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
            case UNSUBSCRIBE -> onUnsubscribe(context, (MqttUnsubscribeMessage) message);
            case PUBLISH -> onPublish((MqttPublishMessage) message);
            case PUBACK -> onPubAck((MqttPubAckMessage) message);
            case PUBREC -> onPubRec(message);
            case PUBREL -> onPubRel(message);
            case PUBCOMP -> onPubComp(message);
            case DISCONNECT -> close(MqttConnectionCloseReason.NORMAL_DISCONNECT);
            default -> close(MqttConnectionCloseReason.PROTOCOL_ERROR);
        }
    }

    private void onPublish(MqttPublishMessage message) {
        MqttQoS qos = message.fixedHeader().qosLevel();
        if (qos == MqttQoS.AT_MOST_ONCE && message.fixedHeader().isDup()) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        int packetId = message.variableHeader().packetId();
        if (qos != MqttQoS.AT_MOST_ONCE && packetId == 0) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        String topicName = message.variableHeader().topicName();
        if (topicName == null || topicName.isEmpty()
                || topicName.indexOf('+') >= 0 || topicName.indexOf('#') >= 0) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttApplicationMessage applicationMessage = new MqttApplicationMessage(
                topicName, ByteBufUtil.getBytes(message.payload()), qos);
        if (qos == MqttQoS.EXACTLY_ONCE) {
            MqttInboundPublishStatus status = broker.receiveQosTwo(
                    this,
                    packetId,
                    applicationMessage,
                    message.fixedHeader().isRetain(),
                    message.fixedHeader().isDup());
            if (status == MqttInboundPublishStatus.PROTOCOL_ERROR) {
                close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                return;
            }
            writeQosControlPacket(MqttMessageType.PUBREC, packetId);
            return;
        }
        if (!broker.publish(this, applicationMessage, message.fixedHeader().isRetain())) {
            close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
            return;
        }
        if (qos == MqttQoS.AT_LEAST_ONCE) {
            requiredNettyContext().writeAndFlush(MqttMessageBuilders.pubAck()
                    .packetId(packetId)
                    .build());
        }
    }

    private void onPubAck(MqttPubAckMessage message) {
        int packetId = message.variableHeader().messageId();
        if (packetId == 0) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        broker.acknowledge(this, packetId);
    }

    private void onPubRec(MqttMessage message) {
        int packetId = packetId(message);
        broker.receivePubRec(this, packetId)
                .ifPresent(pending -> writeQosControlPacket(
                        MqttMessageType.PUBREL, pending.packetId()));
    }

    private void onPubRel(MqttMessage message) {
        int packetId = packetId(message);
        if (!broker.releaseQosTwo(this, packetId)) {
            close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
            return;
        }
        writeQosControlPacket(MqttMessageType.PUBCOMP, packetId);
    }

    private void onPubComp(MqttMessage message) {
        broker.receivePubComp(this, packetId(message));
    }

    private static int packetId(MqttMessage message) {
        return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
    }

    private void writeQosControlPacket(MqttMessageType messageType, int packetId) {
        MqttQoS headerQos = messageType == MqttMessageType.PUBREL
                ? MqttQoS.AT_LEAST_ONCE
                : MqttQoS.AT_MOST_ONCE;
        requiredNettyContext().writeAndFlush(new MqttMessage(
                new MqttFixedHeader(messageType, false, headerQos, false, 0),
                MqttMessageIdVariableHeader.from(packetId)));
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
        Map<String, MqttApplicationMessage> retainedMessages = new LinkedHashMap<>();
        for (MqttTopicSubscription request : requests) {
            String topicFilter = request.topicFilter();
            MqttQoS requestedQos = request.qualityOfService();
            if (!MqttTopicFilter.isValid(topicFilter) || !isSubscriptionQos(requestedQos)) {
                close(MqttConnectionCloseReason.PROTOCOL_ERROR);
                return;
            }
            MqttSubscribeResult result = broker.subscribe(
                    this, new MqttSubscription(topicFilter, requestedQos));
            if (!result.subscribed()) {
                close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
            result.retainedMessages().forEach(
                    retained -> retainedMessages.put(retained.topicName(), retained));
            subAck.addGrantedQos(requestedQos);
        }
        context.writeAndFlush(subAck.build());
        for (MqttApplicationMessage retained : retainedMessages.values()) {
            if (!broker.deliverRetained(this, retained)) {
                close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
                return;
            }
        }
    }

    private static boolean isSubscriptionQos(MqttQoS qos) {
        return qos == MqttQoS.AT_MOST_ONCE
                || qos == MqttQoS.AT_LEAST_ONCE
                || qos == MqttQoS.EXACTLY_ONCE;
    }

    private void onUnsubscribe(
            ChannelHandlerContext context,
            MqttUnsubscribeMessage message) {
        int packetId = message.variableHeader().messageId();
        List<String> topicFilters = message.payload().topics();
        if (packetId == 0 || topicFilters.isEmpty()
                || message.fixedHeader().qosLevel() != MqttQoS.AT_LEAST_ONCE
                || message.fixedHeader().isRetain()
                || topicFilters.stream().anyMatch(topicFilter -> !MqttTopicFilter.isValid(topicFilter))) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }
        if (!broker.unsubscribe(this, topicFilters)) {
            close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
            return;
        }
        context.writeAndFlush(MqttMessageBuilders.unsubAck()
                .packetId(packetId)
                .build());
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
        if (message.variableHeader().hasPassword()
                && !message.variableHeader().hasUserName()) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttWillMessage willMessage = willMessage(message);
        if (!isValidWill(message, willMessage)) {
            close(MqttConnectionCloseReason.PROTOCOL_ERROR);
            return;
        }

        MqttAuthenticationRequest authenticationRequest = new MqttAuthenticationRequest(
                clientId,
                message.payload().userName(),
                message.payload().passwordInBytes());
        if (!authenticator.authenticate(authenticationRequest)) {
            refuse(nettyContext, MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            return;
        }

        MqttConnectionContext connectionContext = new MqttConnectionContext(
                clientId,
                message.variableHeader().isCleanSession(),
                message.variableHeader().keepAliveTimeSeconds(),
                message.payload().userName(),
                willMessage);
        MqttConnectResult connectResult = broker.connect(
                this, connectionContext.clientId(), connectionContext.cleanSession());
        context = connectionContext;
        session = connectResult.session();
        pendingWill = willMessage;
        state = MqttConnectionState.CONNECTED;
        configureKeepAlive(nettyContext, connectionContext.keepAliveSeconds());
        nettyContext.writeAndFlush(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(connectResult.sessionPresent())
                .build());
        broker.pendingPublishes(this).forEach(pending -> {
            if (pending.state() == MqttOutboundPublishState.WAIT_PUBCOMP) {
                writeQosControlPacket(MqttMessageType.PUBREL, pending.packetId());
                return;
            }
            boolean duplicate = pending.sent();
            pending.markSent();
            sendPublish(
                    pending.message(), pending.packetId(), pending.retained(), duplicate);
        });
    }

    private static MqttWillMessage willMessage(MqttConnectMessage message) {
        if (!message.variableHeader().isWillFlag()) {
            return null;
        }
        int willQos = message.variableHeader().willQos();
        if (willQos < 0 || willQos > 2) {
            return null;
        }
        String willTopic = message.payload().willTopic();
        byte[] willPayload = message.payload().willMessageInBytes();
        if (!MqttTopicFilter.isValidTopicName(willTopic) || willPayload == null) {
            return null;
        }
        return new MqttWillMessage(
                new MqttApplicationMessage(
                        willTopic,
                        willPayload,
                        MqttQoS.valueOf(willQos)),
                message.variableHeader().isWillRetain());
    }

    private static boolean isValidWill(
            MqttConnectMessage message,
            MqttWillMessage willMessage) {
        if (!message.variableHeader().isWillFlag()) {
            return message.variableHeader().willQos() == 0
                    && !message.variableHeader().isWillRetain();
        }
        return willMessage != null;
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
        publishPendingWill();
        super.channelInactive(context);
    }

    private void publishPendingWill() {
        MqttWillMessage willMessage = pendingWill;
        pendingWill = null;
        if (willMessage != null && closeReason != MqttConnectionCloseReason.NORMAL_DISCONNECT
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
        return Objects.requireNonNull(nettyContext, "MQTT connection has not been added to a pipeline");
    }
}
