package cn.bcd.app.dataProcess.gateway.mqtt;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
public class MqttConsumer implements Consumer<Mqtt5Publish>, ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(MqttConsumer.class);


    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    Mqtt5AsyncClient client;

    @Autowired
    VehicleConsumeExecutorGroup vehicleConsumeExecutorGroup;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        subscribe();
    }

    private void subscribe() {
        Mqtt5Subscribe subscribe = Mqtt5Subscribe.builder().addSubscription(
                Mqtt5Subscription.builder().topicFilter(gatewayProp.getTopic()).qos(MqttQos.AT_LEAST_ONCE).build()
        ).build();
        AtomicInteger index = new AtomicInteger();
        client.subscribe(subscribe, this,
                Executors.newFixedThreadPool(gatewayProp.getConsumeThreadNum(),
                        r -> new Thread(r, "mqtt-consumer(" + index.incrementAndGet() + "/" + gatewayProp.getConsumeThreadNum() + ")")
                )
        );
    }

    public void accept(Mqtt5Publish mqtt5Publish) {
        byte[] bytes = mqtt5Publish.getPayloadAsBytes();
        try {
            vehicleConsumeExecutorGroup.onMessage(bytes);
        } catch (Exception ex) {
            logger.error("error", ex);
        }
    }

}
