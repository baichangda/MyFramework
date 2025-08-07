package cn.bcd.lib.websocket.client;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MyWebSocketClient {

    static Logger logger = LoggerFactory.getLogger(MyWebSocketClient.class);

    static final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();

    public final String url;
    public final Duration autoReconnectPeriod;
    private final WebSocketClient webSocketClient;
    private final Handler<Void> closeHandler;
    private final Handler<String> textMessageHandler;
    private final AtomicReference<WebSocket> webSocketHolder = new AtomicReference<>();
    private final AtomicLong nextConnectTs = new AtomicLong();

    public MyWebSocketClient(String url, Duration autoReconnectPeriod, Handler<String> textMessageHandler) {
        this.url = url;
        this.textMessageHandler = textMessageHandler;
        closeHandler = v -> {
            logger.info("on close");
            webSocketHolder.set(null);
            nextConnectTs.set(0);
            connect();
        };
        Vertx vertx = Vertx.builder().build();
        webSocketClient = vertx.createWebSocketClient();
        this.autoReconnectPeriod = autoReconnectPeriod;
    }

    public boolean connected() {
        return webSocketHolder.get() != null;
    }

    /**
     * 发送文本消息
     * 返回null表示未连接
     * 否则返回发送结果
     *
     * @param text
     * @return
     */
    public Future<Void> sendText(String text) {
        WebSocket webSocket = webSocketHolder.get();
        if (webSocket == null) {
            return null;
        }
        return webSocket.writeTextMessage(text);
    }


    public void connect() {
        long ts = System.currentTimeMillis();
        long nextTs = nextConnectTs.get();
        long periodMs = autoReconnectPeriod.toMillis();
        if (nextTs == 0) {
            nextTs = ts;
        }
        long waitTs = nextTs - ts;
        nextConnectTs.set(nextTs + periodMs);
        pool.schedule(() -> {
            logger.info("connect ws[{}]", url);
            webSocketClient.connect(8080, "127.0.0.1", "")
                    .onSuccess(w -> {
                        webSocketHolder.set(w);
                        w.closeHandler(closeHandler);
                        w.textMessageHandler(textMessageHandler);
                    }).onFailure(t -> {
                        logger.error("error", t);
                        connect();
                    });
        }, waitTs, TimeUnit.MILLISECONDS);
    }
}
