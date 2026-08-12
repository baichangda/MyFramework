package cn.bcd.lib.websocket.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyWebSocketClientCloseTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void closeCancelsPendingReconnectTimer() throws Exception {
        int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }

        MyWebSocketClient client = new MyWebSocketClient(
                "127.0.0.1:" + unusedPort + "/ws", Duration.ofSeconds(2), ignored -> {
        });
        try {
            Field timerField = MyWebSocketClient.class.getDeclaredField("reconnectTimerId");
            timerField.setAccessible(true);
            Field closedField = MyWebSocketClient.class.getDeclaredField("closed");
            closedField.setAccessible(true);

            await(() -> getLong(timerField, client) != -1, Duration.ofSeconds(3));
            client.close();
            await(() -> getBoolean(closedField, client), Duration.ofSeconds(3));

            assertEquals(-1, timerField.getLong(client));
            TimeUnit.MILLISECONDS.sleep(2200);
            assertEquals(-1, timerField.getLong(client));
        } finally {
            client.close();
        }
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "condition was not met before timeout");
    }

    private static long getLong(Field field, Object target) {
        try {
            return field.getLong(target);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    private static boolean getBoolean(Field field, Object target) {
        try {
            return field.getBoolean(target);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }
}
