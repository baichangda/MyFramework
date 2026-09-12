package cn.bcd.lib.websocket.client;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.websocket.Const;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final WebSocketConnectOptions connectOptions;
    private final Handler<String> textMessageHandler;
    private final Consumer<WebSocket> openHandler;
    private final Consumer<WebSocket> closeHandler;
    private final long reconnectDelayMillis;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final Promise<Void> closePromise = Promise.promise();

    // These fields are only changed on the Vert.x context.
    private WebSocket webSocket;
    private long reconnectTimerId = -1;

    public MyWebSocketClient(String url,
                             Duration autoReconnectPeriod,
                             Handler<String> textMessageHandler) {
        this(url, autoReconnectPeriod, textMessageHandler, null, null);
    }

    /**
     * 创建一个 WebSocket 客户端。
     *
     * @param url                 WebSocket 服务地址，支持 ws://、wss:// 和兼容格式 host:port/path
     * @param autoReconnectPeriod 自动重连间隔，必须不少于 1 毫秒
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
        this.autoReconnectPeriod = Objects.requireNonNull(autoReconnectPeriod, "autoReconnectPeriod");
        try {
            this.reconnectDelayMillis = autoReconnectPeriod.toMillis();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("autoReconnectPeriod is too large", ex);
        }
        if (reconnectDelayMillis < 1) {
            throw new IllegalArgumentException("autoReconnectPeriod must be at least one millisecond");
        }

        this.url = endpoint.url();
        this.host = endpoint.options().getHost();
        this.port = endpoint.options().getPort();
        this.uri = endpoint.options().getURI();
        this.connectOptions = endpoint.options();
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
        Objects.requireNonNull(text, "text");
        CompletableFuture<Void> result = new CompletableFuture<>();
        context.runOnContext(ignored -> {
            WebSocket socket = webSocket;
            if (closeRequested.get() || socket == null) {
                result.completeExceptionally(BaseException.get("client disconnect"));
                return;
            }
            try {
                socket.writeTextMessage(text).onComplete(ar -> {
                    if (ar.succeeded()) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(ar.cause());
                    }
                });
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    /**
     * 异步关闭连接、重连定时器和底层客户端。并发调用会返回同一个完成结果。
     */
    public Future<Void> closeAsync() {
        if (closeRequested.compareAndSet(false, true)) {
            try {
                context.runOnContext(ignored -> closeOnContext());
            } catch (RuntimeException ex) {
                closePromise.tryFail(ex);
            }
        }
        return closePromise.future();
    }

    @Override
    public void close() {
        closeAsync();
    }

    private void connect() {
        if (closeRequested.get()) {
            return;
        }
        LOGGER.info("connecting ws[{}]", url);
        client.connect(new WebSocketConnectOptions(connectOptions))
                .onSuccess(this::onConnected)
                .onFailure(cause -> {
                    if (!closeRequested.get()) {
                        LOGGER.error("connect ws[{}] failed", url, cause);
                        scheduleReconnect();
                    }
                });
    }

    private void onConnected(WebSocket socket) {
        if (closeRequested.get()) {
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
        if (closeRequested.get() || webSocket != socket) {
            return;
        }

        LOGGER.info("close ws[{}]", url);
        webSocket = null;
        invokeHandler(closeHandler, socket, "close");
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (closeRequested.get() || reconnectTimerId != -1) {
            return;
        }
        reconnectTimerId = context.owner().setTimer(reconnectDelayMillis, timerId -> {
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

    private void closeOnContext() {
        cancelReconnect();
        WebSocket socket = webSocket;
        webSocket = null;
        Future<Void> socketClose = socket == null ? Future.succeededFuture() : socket.close();
        socketClose.onComplete(socketResult -> client.close().onComplete(clientResult -> {
            if (socketResult.failed()) {
                closePromise.tryFail(socketResult.cause());
            } else if (clientResult.failed()) {
                closePromise.tryFail(clientResult.cause());
            } else {
                closePromise.tryComplete();
            }
        }));
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
        if (value.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        String absoluteUrl = value.contains("://") ? value : "ws://" + value;
        URI parsed;
        try {
            parsed = URI.create(absoluteUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid WebSocket url: " + url, ex);
        }
        if ((!"ws".equalsIgnoreCase(parsed.getScheme()) && !"wss".equalsIgnoreCase(parsed.getScheme()))
                || parsed.getHost() == null || parsed.getRawFragment() != null || parsed.getRawUserInfo() != null) {
            throw new IllegalArgumentException("url must be an absolute ws:// or wss:// URL");
        }

        WebSocketConnectOptions options = new WebSocketConnectOptions().setAbsoluteURI(parsed.toString());
        if (options.getURI() == null || options.getURI().isEmpty()) {
            options.setURI("/");
        }
        return new Endpoint(value, options);
    }

    private record Endpoint(String url, WebSocketConnectOptions options) {
    }
}
