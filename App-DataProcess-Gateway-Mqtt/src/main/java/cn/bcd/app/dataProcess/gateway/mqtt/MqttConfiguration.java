package cn.bcd.app.dataProcess.gateway.mqtt;

import cn.bcd.lib.base.json.JsonUtil;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @Author：Liqi
 * @CreateTime：2025-01-22
 * @Description：TODO
 **/

@Configuration
@EnableConfigurationProperties(value = MqttProp.class)
public class MqttConfiguration implements ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(MqttConfiguration.class);

    Mqtt5AsyncClient client;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        client.connect().join();
    }

    @Bean
    public Mqtt5AsyncClient mqtt5AsyncClient(MqttProp mqttProp) {
        logger.info("mqttProp:\n{}", JsonUtil.toJsonPretty(mqttProp));
        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(mqttProp.getClientId())
                .sslWithDefaultConfig()
                .serverHost(mqttProp.getServerHost())
                .serverPort(mqttProp.getServerPort())
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(ctx -> logger.info("mqtt connected successful "))
                .addDisconnectedListener(ctx -> logger.info("mqtt disconnected, Exception message: {}",ctx.getCause().getMessage()))
                .buildAsync();
        return client;
    }

    public static void main(String[] args) {
        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier("f0753460-71ce-11f0-92bd-5d8bbd04d385")
                .simpleAuth(Mqtt5SimpleAuth.builder().username("A1_TEST_TOKEN").build())
//                .sslWithDefaultConfig()
                .serverHost("127.0.0.1")
                .serverPort(51883)
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(ctx -> {
                    logger.info("mqtt connected");
                })
                .addDisconnectedListener(ctx -> {
                    logger.info("mqtt disconnected,Exception message: {}",ctx.getCause().getMessage());
                })
                .buildAsync();
        Mqtt5Subscribe subscribe = Mqtt5Subscribe.builder().addSubscription(
                Mqtt5Subscription.builder().topicFilter("vin/+").qos(MqttQos.AT_MOST_ONCE).build()
        ).build();
        Consumer<Mqtt5Publish> consumer = publish -> {
            System.out.println(new String(publish.getPayloadAsBytes()));
        };
        client.subscribe(subscribe, consumer, Executors.newSingleThreadExecutor());
        client.connect().join();
    }

}
