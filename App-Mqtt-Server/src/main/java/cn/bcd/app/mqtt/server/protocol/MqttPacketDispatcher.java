package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.connection.MqttConnectionRegistry;
import cn.bcd.app.mqtt.server.handler.MqttConnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttDisconnectHandler;
import cn.bcd.app.mqtt.server.handler.MqttPingHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public final class MqttPacketDispatcher extends SimpleChannelInboundHandler<MqttMessage> {

    private final MqttConnection connection;
    private final MqttConnectionRegistry connectionRegistry;
    private final MqttConnectHandler connectHandler;
    private final MqttPingHandler pingHandler;
    private final MqttDisconnectHandler disconnectHandler;

    public MqttPacketDispatcher(
            MqttConnection connection,
            MqttConnectionRegistry connectionRegistry,
            MqttConnectHandler connectHandler,
            MqttPingHandler pingHandler,
            MqttDisconnectHandler disconnectHandler) {
        this.connection = connection;
        this.connectionRegistry = connectionRegistry;
        this.connectHandler = connectHandler;
        this.pingHandler = pingHandler;
        this.disconnectHandler = disconnectHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, MqttMessage message) {
        if (message.decoderResult().isFailure()) {
            closeForProtocolError();
            return;
        }

        boolean connected = connection.context() != null;
        if (!connected && message.fixedHeader().messageType() == MqttMessageType.CONNECT) {
            connectHandler.handle(context, connection, (MqttConnectMessage) message);
            return;
        }

        if (!connected) {
            closeForProtocolError();
            return;
        }

        switch (message.fixedHeader().messageType()) {
            case PINGREQ -> pingHandler.handle(context);
            case DISCONNECT -> disconnectHandler.handle(connection);
            default -> closeForProtocolError();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.READER_IDLE) {
            connection.close(MqttConnectionCloseReason.KEEP_ALIVE_TIMEOUT);
            return;
        }
        super.userEventTriggered(context, event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        connection.recordCloseReason(MqttConnectionCloseReason.NETWORK_CLOSED);
        connectionRegistry.unregister(connection);
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        connection.close(cause instanceof DecoderException
                ? MqttConnectionCloseReason.PROTOCOL_ERROR
                : MqttConnectionCloseReason.INTERNAL_ERROR);
    }

    MqttConnection connection() {
        return connection;
    }

    private void closeForProtocolError() {
        connection.close(MqttConnectionCloseReason.PROTOCOL_ERROR);
    }
}
