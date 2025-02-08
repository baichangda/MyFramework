package cn.bcd.dataProcess.gateway.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Configuration
@EnableConfigurationProperties(value = GatewayProp.class)
public class MqttConfiguration implements ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(MqttConfiguration.class);

    Mqtt5AsyncClient client;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        client.connect().join();
    }

    @Bean
    public Mqtt5AsyncClient mqtt5AsyncClient(GatewayProp gatewayProp,
                                             Consumer_dispatch consumer_dispatch) {
        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(gatewayProp.getId())
                .serverHost(gatewayProp.getMqttHost())
                .serverPort(gatewayProp.getMqttPort())
                .sslConfig(MqttSslSupport.getMqttClientSslConfig(gatewayProp.getMqttSslCertFilePath(), gatewayProp.getMqttSslCertPassword()))
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(ctx -> {
                    logger.info("mqtt connected");
                })
                .addDisconnectedListener(ctx -> {
                    logger.info("mqtt disconnected");
                })
                .buildAsync();
        Mqtt5Subscribe subscribe = Mqtt5Subscribe.builder().addSubscription(
                Mqtt5Subscription.builder().topicFilter(gatewayProp.getMqttTopic()).qos(MqttQos.AT_MOST_ONCE).build()
        ).build();
        AtomicInteger index = new AtomicInteger();
        client.subscribe(subscribe, consumer_dispatch,
                Executors.newFixedThreadPool(gatewayProp.getMqttConsumeThreadNum(),
                        r -> new Thread(r, "mqtt-consumer(" + index.incrementAndGet() + "/" + gatewayProp.getMqttConsumeThreadNum() + ")")
                )
        );
        return client;
    }

    public static void main(String[] args) {
        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier("tsp-gateway")
                .serverHost("10.0.11.50")
                .serverPort(11883)
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
