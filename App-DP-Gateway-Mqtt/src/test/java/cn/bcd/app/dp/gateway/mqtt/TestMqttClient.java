package cn.bcd.app.dp.gateway.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestMqttClient {
    static Logger logger = LoggerFactory.getLogger(TestMqttClient.class);

    @Test
    public void test1() throws InterruptedException {
        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier("test123")
                .serverHost("www.baicd.fun")
                .serverPort(31883)
                .addConnectedListener(ctx -> {
                    logger.info("mqtt connected");
                })
                .addDisconnectedListener(ctx -> {
                    logger.info("mqtt disconnected,Exception message: {}", ctx.getCause().getMessage());
                })
                .sslWithDefaultConfig()
                .automaticReconnectWithDefaultConfig()
                .buildAsync();
        client.subscribe(Mqtt5Subscribe.builder().topicFilter("test/123").build(), p -> {
            logger.info("receive: {}", new String(p.getPayloadAsBytes()));
        });
        client.connect().join();
        System.out.println("connected");
        Thread.sleep(Long.MAX_VALUE);
    }
}
