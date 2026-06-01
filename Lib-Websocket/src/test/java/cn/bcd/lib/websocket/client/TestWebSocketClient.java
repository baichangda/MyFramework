package cn.bcd.lib.websocket.client;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.websocket.server.MyWebSocketServer;
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
        MyWebSocketClient webSocketClient = new MyWebSocketClient("127.0.0.1:8080", Duration.ofSeconds(2), msg -> {
            logger.info("on text message:{}", msg);
        });
        test2();
    }

    @Test
    public void test2() throws InterruptedException {
        while (true) {
            MyWebSocketServer webSocketServer = new MyWebSocketServer("0.0.0.0", 8080, "/", ws -> {
                logger.info("websocket connect");
                ws.writeTextMessage(DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date()));
            });
            logger.info("websocket server start");
            TimeUnit.SECONDS.sleep(5);
            webSocketServer.close();
            logger.info("websocket server close");
            TimeUnit.SECONDS.sleep(5);
        }
    }
}
