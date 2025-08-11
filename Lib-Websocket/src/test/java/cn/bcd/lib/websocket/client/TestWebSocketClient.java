package cn.bcd.lib.websocket.client;

import cn.bcd.lib.base.util.DateZoneUtil;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TestWebSocketClient {

    static Logger logger = LoggerFactory.getLogger(TestWebSocketClient.class);

    @Test
    public void test1() throws InterruptedException {
        MyWebSocketClient webSocketClient = new MyWebSocketClient("127.0.0.1:8080", Duration.ofSeconds(1), msg -> {
            logger.info("on text message:{}", msg);
        });
        webSocketClient.connect();

        test2();

        TimeUnit.SECONDS.sleep(Long.MAX_VALUE);

    }

    @Test
    public void test2() throws InterruptedException {
        while (true) {
            Vertx vertx = Vertx.vertx();
            HttpServer httpServer = vertx.createHttpServer();
            httpServer.webSocketHandler(ws -> {
                logger.info("websocket connect");
                ws.writeTextMessage(DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date()));
            }).listen(8080).onSuccess(server -> logger.info("listen on 8080"));
            TimeUnit.SECONDS.sleep(5);
            httpServer.close();
            TimeUnit.SECONDS.sleep(5);
        }
    }
}
