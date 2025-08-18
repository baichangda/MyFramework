package cn.bcd.lib.websocket.client;

import cn.bcd.lib.websocket.Const;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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


    public final String url;
    public final String host;
    public final int port;
    public final String uri;
    public final Duration autoReconnectPeriod;
    private final WebSocketClient webSocketClient;
    private final Handler<Void> closeHandler;
    private final Handler<String> textMessageHandler;
    private volatile WebSocket webSocket;
    private long nextConnectTs;

    public MyWebSocketClient(String url, Duration autoReconnectPeriod, Handler<String> textMessageHandler) {
        this.url = url;
        String[] split = url.split(":");
        this.host = split[0];
        String s1 = split[1];
        int index = s1.indexOf("/");
        if (index == -1) {
            this.port = Integer.parseInt(s1);
            this.uri = "";
        } else {
            this.port = Integer.parseInt(s1.substring(0, index));
            this.uri = s1.substring(index);
        }
        this.textMessageHandler = textMessageHandler;
        closeHandler = v -> {
            pool.execute(() -> {
                logger.info("on close ws[{}]", url);
                webSocket = null;
                nextConnectTs = 0;
                connect();
            });
        };
        webSocketClient = Const.vertx.createWebSocketClient();
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
            return Future.failedFuture("ws[" + url + "] has closed");
        }
        return webSocket.writeTextMessage(text);
    }

    /**
     * 异步连接
     *
     * @return
     */
    public final CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pool.execute(() -> {
            connectInterval(future);
        });
        return future;
    }

    /**
     * 异步关闭
     *
     * @return
     */
    public final CompletableFuture<Void> close() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pool.execute(() -> {
            if (webSocket == null) {
                return;
            }
            webSocket.closeHandler(null);
            webSocket.textMessageHandler(null);
            webSocket = null;
            webSocketClient.close().onSuccess(v -> future.complete(null));
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
            logger.info("connecting ws[{}]", url);
            webSocketClient.connect(port, host, "")
                    .onSuccess(w -> {
                        pool.execute(() -> {
                            logger.error("connect ws[{}] succeed", url);
                            webSocket = w;
                            w.closeHandler(closeHandler);
                            w.textMessageHandler(textMessageHandler);
                            future.complete(null);
                        });

                    }).onFailure(t -> {
                        pool.execute(() -> {
                            logger.error("connect ws[{}] failed", url, t);
                            connectInterval(future);
                        });
                    });
        }, waitTs, TimeUnit.MILLISECONDS);
    }
}
