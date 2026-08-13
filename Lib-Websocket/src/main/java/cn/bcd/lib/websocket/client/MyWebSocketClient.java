package cn.bcd.lib.websocket.client;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.websocket.Const;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MyWebSocketClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyWebSocketClient.class);

    public final String url;
    public final String host;
    public final int port;
    public final String uri;
    public final Duration autoReconnectPeriod;

    private final Context context;
    private final WebSocketClient client;
    private final Handler<String> textMessageHandler;
    private final Consumer<WebSocket> openHandler;
    private final Consumer<WebSocket> closeHandler;

    // The fields below are only changed on the Vert.x context.
    private WebSocket webSocket;
    private volatile boolean closed;
    private volatile long reconnectTimerId = -1;

    public MyWebSocketClient(String url,
                             Duration autoReconnectPeriod,
                             Handler<String> textMessageHandler) {
        this(url, autoReconnectPeriod, textMessageHandler, null, null);
    }

    /**
     * 创建一个 WebSocket 客户端。
     *
     * @param url                 WebSocket 服务地址，例如：127.0.0.1:8080/ws
     * @param autoReconnectPeriod 自动重连间隔，必须大于 0
     * @param textMessageHandler  文本消息处理函数
     * @param openHandler         连接成功回调，重连成功也会调用
     * @param closeHandler        连接断开回调
     */
    public MyWebSocketClient(String url,
                             Duration autoReconnectPeriod,
                             Handler<String> textMessageHandler,
                             Consumer<WebSocket> openHandler,
                             Consumer<WebSocket> closeHandler) {
        Endpoint endpoint = parseEndpoint(url);
        if (Objects.requireNonNull(autoReconnectPeriod, "autoReconnectPeriod").isZero()
                || autoReconnectPeriod.isNegative()) {
            throw new IllegalArgumentException("autoReconnectPeriod must be greater than zero");
        }

        this.url = url.trim();
        this.host = endpoint.host();
        this.port = endpoint.port();
        this.uri = endpoint.uri();
        this.autoReconnectPeriod = autoReconnectPeriod;
        this.textMessageHandler = Objects.requireNonNull(textMessageHandler, "textMessageHandler");
        this.openHandler = openHandler;
        this.closeHandler = closeHandler;
        this.context = Const.vertx.getOrCreateContext();
        this.client = Const.vertx.createWebSocketClient();

        context.runOnContext(ignored -> connect());
    }

    /**
     * 发送文本消息。客户端未连接或发送失败时，返回的 Future 会异常完成。
     */
    public CompletableFuture<Void> sendText(String text) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        context.runOnContext(ignored -> {
            WebSocket socket = webSocket;
            if (closed || socket == null) {
                result.completeExceptionally(BaseException.get("client disconnect"));
                return;
            }
            socket.writeTextMessage(text).onComplete(ar -> {
                if (ar.succeeded()) {
                    result.complete(null);
                } else {
                    result.completeExceptionally(ar.cause());
                }
            });
        });
        return result;
    }

    @Override
    public void close() {
        context.runOnContext(ignored -> {
            if (closed) {
                return;
            }
            closed = true;
            cancelReconnect();

            WebSocket socket = webSocket;
            webSocket = null;
            if (socket != null) {
                socket.closeHandler(null);
                socket.textMessageHandler(null);
                socket.close();
            }
            client.close();
        });
    }

    private void connect() {
        if (closed) {
            return;
        }
        LOGGER.info("connecting ws[{}]", url);
        client.connect(port, host, uri)
                .onSuccess(this::onConnected)
                .onFailure(cause -> {
                    if (!closed) {
                        LOGGER.error("connect ws[{}] failed", url, cause);
                        scheduleReconnect();
                    }
                });
    }

    private void onConnected(WebSocket socket) {
        if (closed) {
            socket.close();
            return;
        }

        LOGGER.info("connect ws[{}] succeed", url);
        webSocket = socket;
        socket.textMessageHandler(textMessageHandler);
        socket.closeHandler(ignored -> onDisconnected(socket));
        invokeHandler(openHandler, socket, "open");
    }

    private void onDisconnected(WebSocket socket) {
        if (closed || webSocket != socket) {
            return;
        }

        LOGGER.info("close ws[{}]", url);
        webSocket = null;
        invokeHandler(closeHandler, socket, "close");
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (closed || reconnectTimerId != -1) {
            return;
        }
        reconnectTimerId = context.owner().setTimer(autoReconnectPeriod.toMillis(), timerId -> {
            reconnectTimerId = -1;
            connect();
        });
    }

    private void cancelReconnect() {
        if (reconnectTimerId == -1) {
            return;
        }
        context.owner().cancelTimer(reconnectTimerId);
        reconnectTimerId = -1;
    }

    private void invokeHandler(Consumer<WebSocket> handler, WebSocket socket, String name) {
        if (handler == null) {
            return;
        }
        try {
            handler.accept(socket);
        } catch (RuntimeException ex) {
            LOGGER.error("ws[{}] {} handler failed", url, name, ex);
        }
    }

    private static Endpoint parseEndpoint(String url) {
        String value = Objects.requireNonNull(url, "url").trim();
        if (value.isEmpty() || value.contains("://")) {
            throw new IllegalArgumentException("url must have the form host:port[/path]");
        }

        URI parsed;
        try {
            parsed = URI.create("ws://" + value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid WebSocket url: " + url, ex);
        }
        if (parsed.getHost() == null || parsed.getPort() < 0 || parsed.getPort() > 65535
                || parsed.getRawFragment() != null) {
            throw new IllegalArgumentException("url must have the form host:port[/path]");
        }

        String path = parsed.getRawPath();
        String query = parsed.getRawQuery();
        String requestUri = (path == null ? "" : path) + (query == null ? "" : "?" + query);
        return new Endpoint(parsed.getHost(), parsed.getPort(), requestUri);
    }

    private record Endpoint(String host, int port, String uri) {
    }
}
