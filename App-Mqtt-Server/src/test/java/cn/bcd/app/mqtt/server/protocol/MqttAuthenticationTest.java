package cn.bcd.app.mqtt.server.protocol;

import cn.bcd.app.mqtt.server.authentication.MqttAuthenticationRequest;
import cn.bcd.app.mqtt.server.authentication.MqttAuthenticator;
import cn.bcd.app.mqtt.server.authentication.SimpleMqttAuthenticator;
import cn.bcd.app.mqtt.server.broker.MqttBroker;
import cn.bcd.app.mqtt.server.config.MqttAuthenticationProperties;
import cn.bcd.app.mqtt.server.connection.MqttConnection;
import cn.bcd.app.mqtt.server.connection.MqttConnectionCloseReason;
import cn.bcd.app.mqtt.server.support.MqttTestBroker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttAuthenticationTest {

    @Test
    void shouldAcceptConfiguredUsernameAndPassword() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = newChannel(broker, simpleAuthenticator("device", "secret"));

        channel.writeInbound(connectPacket("cid", "device", bytes("secret"), true));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, readOutbound(channel));
        assertEquals("device", channel.pipeline().get(MqttConnection.class)
                .context().username());
        assertTrue(broker.findSession("cid").isPresent());
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRejectWrongPasswordWithoutCreatingSession() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = newChannel(broker, simpleAuthenticator("device", "secret"));

        channel.writeInbound(connectPacket("cid", "device", bytes("wrong"), true));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x04}, readOutbound(channel));
        assertFalse(channel.isActive());
        assertTrue(broker.findSession("cid").isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRejectMissingCredentials() {
        MqttBroker broker = MqttTestBroker.create();
        EmbeddedChannel channel = newChannel(broker, simpleAuthenticator("device", "secret"));

        channel.writeInbound(connectPacket("cid", null, null, true));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x04}, readOutbound(channel));
        assertFalse(channel.isActive());
        assertTrue(broker.findSession("cid").isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseWhenPasswordFlagIsSetWithoutUsernameFlag() {
        MqttBroker broker = MqttTestBroker.create();
        MqttAuthenticator authenticator = request -> true;
        EmbeddedChannel channel = newChannel(broker, authenticator);
        MqttConnection connection = channel.pipeline().get(MqttConnection.class);

        channel.writeInbound(connectPacket("cid", null, bytes("secret"), true));

        assertFalse(channel.isActive());
        assertTrue(channel.outboundMessages().isEmpty());
        assertEquals(MqttConnectionCloseReason.PROTOCOL_ERROR, connection.closeReason());
        assertTrue(broker.findSession("cid").isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldPassBinaryPasswordToCustomAuthenticator() {
        MqttBroker broker = MqttTestBroker.create();
        byte[] expectedPassword = new byte[]{0x00, (byte) 0xff, 0x01};
        MqttAuthenticator authenticator = request -> {
            assertEquals("cid", request.clientId());
            assertEquals("device", request.username());
            assertArrayEquals(expectedPassword, request.password());
            return true;
        };
        EmbeddedChannel channel = newChannel(broker, authenticator);

        channel.writeInbound(connectPacket("cid", "device", expectedPassword, true));

        assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00}, readOutbound(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldDefensivelyCopyAuthenticationPassword() {
        byte[] password = bytes("secret");
        MqttAuthenticationRequest request = new MqttAuthenticationRequest(
                "cid", "device", password);
        password[0] = 0;
        byte[] returnedPassword = request.password();
        returnedPassword[1] = 0;

        assertArrayEquals(bytes("secret"), request.password());
        assertNull(new MqttAuthenticationRequest("cid", null, null).password());
    }

    private static MqttAuthenticator simpleAuthenticator(
            String username,
            String password) {
        MqttAuthenticationProperties properties = new MqttAuthenticationProperties();
        properties.getSimple().getUsers().put(username, password);
        return new SimpleMqttAuthenticator(properties);
    }

    private static EmbeddedChannel newChannel(
            MqttBroker broker,
            MqttAuthenticator authenticator) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MqttDecoder(1024, 64, true));
        channel.pipeline().addLast(MqttEncoder.INSTANCE);
        channel.pipeline().addLast(new MqttConnection(broker, authenticator));
        return channel;
    }

    private static ByteBuf connectPacket(
            String clientId,
            String username,
            byte[] password,
            boolean cleanSession) {
        byte[] clientIdBytes = bytes(clientId);
        byte[] usernameBytes = username == null ? new byte[0] : bytes(username);
        int remainingLength = 12 + clientIdBytes.length;
        if (username != null) {
            remainingLength += 2 + usernameBytes.length;
        }
        if (password != null) {
            remainingLength += 2 + password.length;
        }
        ByteBuf connect = Unpooled.buffer(2 + remainingLength);
        connect.writeByte(0x10);
        connect.writeByte(remainingLength);
        connect.writeShort(4).writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
        connect.writeByte(4);
        int flags = cleanSession ? 0x02 : 0;
        if (username != null) {
            flags |= 0x80;
        }
        if (password != null) {
            flags |= 0x40;
        }
        connect.writeByte(flags);
        connect.writeShort(60);
        connect.writeShort(clientIdBytes.length).writeBytes(clientIdBytes);
        if (username != null) {
            connect.writeShort(usernameBytes.length).writeBytes(usernameBytes);
        }
        if (password != null) {
            connect.writeShort(password.length).writeBytes(password);
        }
        return connect;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readOutbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }
}
