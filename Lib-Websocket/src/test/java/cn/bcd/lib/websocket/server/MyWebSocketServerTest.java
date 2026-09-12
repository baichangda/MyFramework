package cn.bcd.lib.websocket.server;

import cn.bcd.lib.websocket.client.MyWebSocketClient;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MyWebSocketServerTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void exposesStartupFailureAndClosesIdempotently() throws Exception {
        MyWebSocketServer first = await(MyWebSocketServer.start("127.0.0.1", 0, "/ws", ignored -> {
        }));
        MyWebSocketServer second = new MyWebSocketServer("127.0.0.1", first.actualPort(), "/ws", ignored -> {
        });
        try {
            assertThrows(ExecutionException.class, () -> await(second.startFuture()));
            await(second.closeAsync());
            await(second.closeAsync());
        } finally {
            await(first.closeAsync());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void rejectsNonWebSocketRequest() throws Exception {
        MyWebSocketServer server = await(MyWebSocketServer.start("127.0.0.1", 0, "/ws", ignored -> {
        }));
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.actualPort() + "/ws"))
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            assertEquals(400, response.statusCode());
        } finally {
            await(server.closeAsync());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void exchangesTextMessagesWithAbsoluteWsUrl() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();
        MyWebSocketServer server = await(MyWebSocketServer.start("127.0.0.1", 0, "/ws",
                socket -> socket.textMessageHandler(received::complete)));
        CompletableFuture<Void> opened = new CompletableFuture<>();
        MyWebSocketClient client = new MyWebSocketClient(
                "ws://127.0.0.1:" + server.actualPort() + "/ws",
                Duration.ofSeconds(1),
                ignored -> {
                },
                ignored -> opened.complete(null),
                null);
        try {
            opened.get(5, TimeUnit.SECONDS);
            client.sendText("hello").get(5, TimeUnit.SECONDS);
            assertEquals("hello", received.get(5, TimeUnit.SECONDS));
        } finally {
            await(client.closeAsync());
            await(server.closeAsync());
        }
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
