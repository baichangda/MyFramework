package cn.bcd.lib.spring.mqtt.server;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MqttServerStarterTest {

    @Test
    void startsOnConfiguredPort() throws Exception {
        Path dataPath = Files.createTempDirectory("moquette-data-");
        MqttServerProp prop = new MqttServerProp();
        prop.address = "127.0.0.1";
        prop.port = findAvailablePort();
        prop.dataPath = dataPath.toString();
        prop.persistenceEnabled = false;
        MqttServerStarter starter = new MqttServerStarter(prop);

        try {
            starter.run();
            try (Socket socket = new Socket(prop.address, prop.port)) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(new byte[]{
                        0x10, 0x10,
                        0x00, 0x04, 'M', 'Q', 'T', 'T',
                        0x04, 0x02, 0x00, 0x3c,
                        0x00, 0x04, 't', 'e', 's', 't'
                });
                assertArrayEquals(
                        new byte[]{0x20, 0x02, 0x00, 0x00},
                        socket.getInputStream().readNBytes(4));
            }
        } finally {
            starter.destroy();
            try (var paths = Files.walk(dataPath)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
            }
        }
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
