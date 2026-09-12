package cn.bcd.lib.websocket.server;

import cn.bcd.lib.websocket.Const;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyWebSocketServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyWebSocketServer.class);

    public final String host;
    public final int port;
    public final String uri;
    public final Handler<ServerWebSocket> webSocketHandler;

    private final HttpServer httpServer;
    private final Promise<MyWebSocketServer> startPromise = Promise.promise();
    private final Promise<Void> closePromise = Promise.promise();
    private final AtomicBoolean closeRequested = new AtomicBoolean();

    /**
     * 创建并异步启动 WebSocket 服务。可通过 {@link #startFuture()} 等待启动结果。
     *
     * @param host             WebSocket 服务地址
     * @param port             WebSocket 服务端口
     * @param uri              为 null 或空时接收所有 WebSocket 路径
     * @param webSocketHandler WebSocket 连接处理函数
     */
    public MyWebSocketServer(String host, int port, String uri, Handler<ServerWebSocket> webSocketHandler) {
        this.host = Objects.requireNonNull(host, "host");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
        this.uri = uri;
        this.webSocketHandler = Objects.requireNonNull(webSocketHandler, "webSocketHandler");
        this.httpServer = Const.vertx.createHttpServer();

        if (uri == null || uri.isEmpty()) {
            httpServer.webSocketHandler(this::handleWebSocket);
        } else {
            Router router = Router.router(Const.vertx);
            router.route(uri).handler(ctx -> ctx.request().toWebSocket()
                    .onSuccess(this::handleWebSocket)
                    .onFailure(cause -> {
                        if (!ctx.response().ended()) {
                            ctx.response().setStatusCode(426);
                            if (ctx.request().version() == HttpVersion.HTTP_1_1) {
                                ctx.response().putHeader(HttpHeaders.UPGRADE, "websocket");
                            }
                            ctx.response().end();
                        }
                    }));
            httpServer.requestHandler(router);
        }

        httpServer.listen(port, host).onComplete(ar -> {
            if (ar.succeeded()) {
                LOGGER.info("host[{}] port[{}] uri[{}] listen succeed", host, actualPort(), uri);
                startPromise.tryComplete(this);
            } else {
                LOGGER.error("host[{}] port[{}] uri[{}] listen failed", host, port, uri, ar.cause());
                startPromise.tryFail(ar.cause());
            }
        });
    }

    /**
     * 创建服务并返回可观察的启动结果。
     */
    public static Future<MyWebSocketServer> start(String host,
                                                   int port,
                                                   String uri,
                                                   Handler<ServerWebSocket> webSocketHandler) {
        return new MyWebSocketServer(host, port, uri, webSocketHandler).startFuture();
    }

    public Future<MyWebSocketServer> startFuture() {
        return startPromise.future();
    }

    /**
     * 返回实际监听端口，使用端口 0 启动测试服务时尤其有用。
     */
    public int actualPort() {
        return httpServer.actualPort();
    }

    /**
     * 异步关闭服务。并发调用会返回同一个完成结果。
     */
    public Future<Void> closeAsync() {
        if (closeRequested.compareAndSet(false, true)) {
            startPromise.future().onComplete(ignored -> httpServer.close().onComplete(ar -> {
                if (ar.succeeded()) {
                    LOGGER.info("host[{}] port[{}] uri[{}] close succeed", host, port, uri);
                    closePromise.tryComplete();
                } else {
                    LOGGER.error("host[{}] port[{}] uri[{}] close failed", host, port, uri, ar.cause());
                    closePromise.tryFail(ar.cause());
                }
            }));
        }
        return closePromise.future();
    }

    @Override
    public void close() {
        closeAsync();
    }

    private void handleWebSocket(ServerWebSocket socket) {
        try {
            webSocketHandler.handle(socket);
        } catch (RuntimeException ex) {
            LOGGER.error("host[{}] port[{}] uri[{}] websocket handler failed", host, port, uri, ex);
            socket.close();
        }
    }
}
