package cn.bcd.app.mqtt.server;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.lifecycle.MqttServerLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "mqtt.server.bind-address=127.0.0.1",
        "mqtt.server.port=0",
        "mqtt.server.persistence.retained-message.sqlite.database-path=:memory:"
})
class ApplicationContextTest {

    private static final byte[] CONNECT = {
            0x10, 0x0f,
            0x00, 0x04, 'M', 'Q', 'T', 'T',
            0x04, 0x02, 0x00, 0x3c,
            0x00, 0x03, 'c', 'i', 'd'
    };

    @Autowired
    MqttServerProperties properties;

    @Autowired
    MqttServerLifecycle lifecycle;

    @Test
    void shouldBindConfigurationAndAcceptMqttConnect() throws IOException {
        assertEquals("127.0.0.1", properties.getBindAddress());
        assertEquals(0, properties.getPort());
        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.getBoundPort() > 0);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", lifecycle.getBoundPort()));
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(CONNECT);
            assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00},
                    socket.getInputStream().readNBytes(4));
            socket.getOutputStream().write(new byte[]{(byte) 0xc0, 0x00});
            assertArrayEquals(new byte[]{(byte) 0xd0, 0x00},
                    socket.getInputStream().readNBytes(2));
            socket.getOutputStream().write(new byte[]{(byte) 0xe0, 0x00});
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void shouldTakeOverExistingTcpConnectionWithSameClientId() throws IOException {
        try (Socket first = connect(); Socket second = connect()) {
            first.getOutputStream().write(CONNECT);
            assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00},
                    first.getInputStream().readNBytes(4));

            second.getOutputStream().write(CONNECT);
            assertArrayEquals(new byte[]{0x20, 0x02, 0x00, 0x00},
                    second.getInputStream().readNBytes(4));

            assertEquals(-1, first.getInputStream().read());
            assertTrue(second.isConnected());
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", lifecycle.getBoundPort()));
        socket.setSoTimeout(3000);
        return socket;
    }
}
