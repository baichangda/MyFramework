package cn.bcd.lib.websocket.client;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MyWebSocketClientCloseTest {

    @Test
    void parsesAndValidatesConfiguration() throws Exception {
        MyWebSocketClient legacyClient = new MyWebSocketClient(
                " 127.0.0.1:65535/ws/events?type=text ", Duration.ofSeconds(1), ignored -> {
        });
        try {
            assertEquals("127.0.0.1:65535/ws/events?type=text", legacyClient.url);
            assertEquals("127.0.0.1", legacyClient.host);
            assertEquals(65535, legacyClient.port);
            assertEquals("/ws/events?type=text", legacyClient.uri);
        } finally {
            await(legacyClient.closeAsync());
        }

        MyWebSocketClient secureClient = new MyWebSocketClient(
                "wss://example.com/events", Duration.ofSeconds(1), ignored -> {
        });
        try {
            assertEquals("example.com", secureClient.host);
            assertEquals(443, secureClient.port);
            assertEquals("/events", secureClient.uri);
        } finally {
            await(secureClient.closeAsync());
        }

        assertThrows(IllegalArgumentException.class, () -> new MyWebSocketClient(
                "127.0.0.1:8080/ws", Duration.ZERO, ignored -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> new MyWebSocketClient(
                "127.0.0.1:8080/ws", Duration.ofNanos(1), ignored -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> new MyWebSocketClient(
                "http://127.0.0.1:8080/ws", Duration.ofSeconds(1), ignored -> {
        }));
        assertThrows(NullPointerException.class, () -> new MyWebSocketClient(
                "127.0.0.1:8080/ws", Duration.ofSeconds(1), null));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void closeAsyncCancelsReconnectAndIsIdempotent() throws Exception {
        int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }

        MyWebSocketClient client = new MyWebSocketClient(
                "ws://127.0.0.1:" + unusedPort + "/ws", Duration.ofMillis(100), ignored -> {
        });
        TimeUnit.MILLISECONDS.sleep(250);

        await(client.closeAsync());
        await(client.closeAsync());
        assertThrows(ExecutionException.class,
                () -> client.sendText("after-close").get(3, TimeUnit.SECONDS));
    }

    @Test
    void sendTextRejectsNullSynchronously() throws Exception {
        MyWebSocketClient client = new MyWebSocketClient(
                "ws://127.0.0.1:65535/ws", Duration.ofSeconds(1), ignored -> {
        });
        try {
            assertThrows(NullPointerException.class, () -> client.sendText(null));
        } finally {
            await(client.closeAsync());
        }
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
