package cn.bcd.lib.websocket.client;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.websocket.Const;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MyWebSocketClient implements AutoCloseable {

    static Logger logger = LoggerFactory.getLogger(MyWebSocketClient.class);

    public final String url;
    public final String host;
    public final int port;
    public final String uri;
    public final Duration autoReconnectPeriod;
    private final WebSocketClient webSocketClient;
    private final Handler<Void> closeHandler;
    private WebSocket webSocket;
    private boolean closed = false;
    private final Runnable connectRunnable;
    private final Context context;

    public MyWebSocketClient(String url,
                             Duration autoReconnectPeriod,
                             Handler<String> textMessageHandler) {
        this(url, autoReconnectPeriod, textMessageHandler, null, null);
    }


    /**
     * 创建一个websocket客户端
     *
     * @param url                  websocket服务地址、例如：127.0.0.1:8080/ws
     * @param autoReconnectPeriod  自动重连间隔、不能为空
     * @param onTextMessageHandler 文本消息处理函数
     * @param onOpenHandler        连接成功回调、重连成功也会调用
     * @param onCloseHandler       连接断开回调
     */
    public MyWebSocketClient(String url,
                             Duration autoReconnectPeriod,
                             Handler<String> onTextMessageHandler,
                             Consumer<WebSocket> onOpenHandler,
                             Consumer<WebSocket> onCloseHandler) {
        this.url = url;
        this.autoReconnectPeriod = autoReconnectPeriod;
        this.context = Const.vertx.getOrCreateContext();
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

        //断线重连逻辑
        closeHandler = v -> {
            logger.info("on close ws[{}]", url);
            if (onCloseHandler != null) {
                onCloseHandler.accept(webSocket);
            }
            webSocket = null;
            connectInterval(true);
        };

        webSocketClient = Const.vertx.createWebSocketClient();
        connectRunnable = () -> {
            logger.info("connecting ws[{}]", url);
            webSocketClient.connect(port, host, uri)
                    .onSuccess(w -> {
                        logger.info("connect ws[{}] succeed", url);
                        webSocket = w;
                        w.closeHandler(closeHandler);
                        w.textMessageHandler(onTextMessageHandler);
                        if (onOpenHandler != null) {
                            onOpenHandler.accept(w);
                        }
                    }).onFailure(t -> {
                        logger.error("connect ws[{}] failed", url, t);
                        connectInterval(false);
                    });
        };

        context.runOnContext(e -> {
            //触发主动连接
            connectInterval(true);
        });
    }

    /**
     * 发送文本消息
     * 否则返回发送结果
     *
     * @param text
     * @return
     */
    public final CompletableFuture<Void> sendText(String text) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.runOnContext(e -> {
            if (webSocket == null) {
                future.completeExceptionally(BaseException.get("client disconnect"));
            } else {
                webSocket.writeTextMessage(text).onComplete(ar -> {
                    if (ar.succeeded()) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
            }
        });
        return future;
    }

    /**
     * 异步关闭
     *
     * @return
     */
    public void close() {
        context.runOnContext(v -> {
            closed = true;
            //提交关闭任务
            if (webSocket != null) {
                webSocket.closeHandler(null);
                webSocket.textMessageHandler(null);
                webSocket.close();
                webSocket = null;
            }
            webSocketClient.close();
        });
    }


    /**
     * 周期性重连
     */
    private void connectInterval(boolean connectImmediately) {
        if (closed) {
            return;
        }
        if (connectImmediately) {
            connectRunnable.run();
        } else {
            context.owner().setTimer(autoReconnectPeriod.toMillis(), l -> {
                connectRunnable.run();
            });
        }
    }
}
