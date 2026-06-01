package cn.bcd.lib.websocket.server;


import cn.bcd.lib.websocket.Const;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyWebSocketServer implements AutoCloseable {
    static Logger logger = LoggerFactory.getLogger(MyWebSocketServer.class);
    public final String host;
    public final int port;
    public final String uri;
    public final Handler<ServerWebSocket> webSocketHandler;
    public HttpServer httpServer;

    /**
     * 创建一个websocket服务
     *
     * @param host             websocket服务地址
     * @param port             websocket服务端口
     * @param uri              可以为null
     * @param webSocketHandler websocket连接处理函数
     */
    public MyWebSocketServer(String host, int port, String uri, Handler<ServerWebSocket> webSocketHandler) {
        this.host = host;
        this.port = port;
        this.uri = uri;
        this.webSocketHandler = webSocketHandler;
        httpServer = Const.vertx.createHttpServer();
        if (uri == null || uri.isEmpty()) {
            httpServer.webSocketHandler(webSocketHandler);
        } else {
            Router router = Router.router(Const.vertx);
            router.route(uri).handler(ctx -> {
                ctx.request().toWebSocket().onSuccess(webSocketHandler);
            });
            httpServer.requestHandler(router);
        }
        httpServer.listen(port, host).onComplete(ar -> {
            if (ar.succeeded()) {
                logger.info("host[{}] port[{}] uri[{}] listen succeed", host, port, uri);
            } else {
                logger.error("host[{}] port[{}] uri[{}] listen failed", host, port, uri, ar.cause());
            }
        });
    }

    public void close() {
        if (httpServer != null) {
            httpServer.close().onComplete(ar -> {
                if (ar.succeeded()) {
                    logger.info("host[{}] port[{}] uri[{}] close succeed", host, port, uri);
                } else {
                    logger.error("host[{}] port[{}] uri[{}] close failed", host, port, uri, ar.cause());
                }
            });
            httpServer = null;
        }
    }
}
