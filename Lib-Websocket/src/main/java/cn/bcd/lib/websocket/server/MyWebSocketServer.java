package cn.bcd.lib.websocket.server;


import cn.bcd.lib.websocket.Const;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyWebSocketServer {
    static Logger logger = LoggerFactory.getLogger(MyWebSocketServer.class);
    public final String host;
    public final int port;
    public final String uri;
    public final Handler<ServerWebSocket> webSocketHandler;
    public HttpServer httpServer;

    /**
     * @param host
     * @param port
     * @param uri              可以为null
     * @param webSocketHandler
     */
    public MyWebSocketServer(String host, int port, String uri, Handler<ServerWebSocket> webSocketHandler) {
        this.host = host;
        this.port = port;
        this.uri = uri;
        this.webSocketHandler = webSocketHandler;
        this.httpServer = Const.vertx.createHttpServer();
    }

    public synchronized Future<HttpServer> init() {
        if (httpServer == null) {
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
            return httpServer.listen(port, host);
        } else {
            return Future.succeededFuture();
        }
    }

    public synchronized Future<Void> close() {
        if (httpServer == null) {
            return Future.succeededFuture();
        } else {
            Future<Void> future = httpServer.close();
            httpServer = null;
            return future;
        }
    }
}
