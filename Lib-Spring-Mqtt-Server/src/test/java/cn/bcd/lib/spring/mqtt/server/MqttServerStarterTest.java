package cn.bcd.lib.spring.mqtt.server;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttServerStarterTest {

    @Test
    void startsOnConfiguredPort() throws Exception {
        Path dataPath = Files.createTempDirectory("hivemq-data-");
        MqttServerProp prop = new MqttServerProp();
        prop.address = "127.0.0.1";
        prop.port = findAvailablePort();
        prop.dataPath = dataPath.toString();
        MqttServerStarter starter = new MqttServerStarter(prop);

        try {
            starter.run();
            try (Socket socket = new Socket(prop.address, prop.port)) {
                assertTrue(socket.isConnected());
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
