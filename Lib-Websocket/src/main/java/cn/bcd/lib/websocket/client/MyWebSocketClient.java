package cn.bcd.lib.websocket.client;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyWebSocketClient {

    static Logger logger = LoggerFactory.getLogger(MyWebSocketClient.class);

    static final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();

    public final String host;
    public final int port;
    public final String url;
    public final Duration autoReconnectPeriod;
    private final WebSocketClient webSocketClient;
    private final Handler<Void> closeHandler;
    private final Handler<String> textMessageHandler;
    private volatile WebSocket webSocket;
    private long nextConnectTs;

    public MyWebSocketClient(String host, int port, Duration autoReconnectPeriod, Handler<String> textMessageHandler) {
        this.host = host;
        this.port = port;
        this.url = host + ":" + port;
        this.textMessageHandler = textMessageHandler;
        closeHandler = v -> {
            pool.execute(() -> {
                logger.info("on close");
                webSocket = null;
                nextConnectTs = 0;
                connect();
            });
        };
        Vertx vertx = Vertx.builder().build();
        webSocketClient = vertx.createWebSocketClient();
        this.autoReconnectPeriod = autoReconnectPeriod;
    }

    public final boolean connected() {
        return webSocket != null;
    }

    /**
     * 发送文本消息
     * 返回null表示未连接
     * 否则返回发送结果
     *
     * @param text
     * @return
     */
    public final Future<Void> sendText(String text) {
        WebSocket webSocket = this.webSocket;
        if (webSocket == null) {
            return null;
        }
        return webSocket.writeTextMessage(text);
    }

    public final CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pool.execute(() -> {
            connectInterval(future);
        });
        return future;
    }

    private void connectInterval(CompletableFuture<Void> future) {
        long ts = System.currentTimeMillis();
        long nextTs = nextConnectTs;
        long periodMs = autoReconnectPeriod.toMillis();
        if (nextTs == 0) {
            nextTs = ts;
        }
        long waitTs = nextTs - ts;
        nextConnectTs = nextTs + periodMs;
        pool.schedule(() -> {
            logger.info("connect ws[{}]", url);
            webSocketClient.connect(8080, "127.0.0.1", "")
                    .onSuccess(w -> {
                        logger.error("connect ws[{}] succeed", url);
                        webSocket = w;
                        w.closeHandler(closeHandler);
                        w.textMessageHandler(textMessageHandler);
                        future.complete(null);
                    }).onFailure(t -> {
                        logger.error("connect ws[{}] failed", url, t);
                        connectInterval(future);
                    });
        }, waitTs, TimeUnit.MILLISECONDS);
    }
}
