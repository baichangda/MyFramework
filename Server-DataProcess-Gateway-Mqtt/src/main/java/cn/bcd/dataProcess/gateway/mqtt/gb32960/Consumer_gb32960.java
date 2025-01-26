package cn.bcd.dataProcess.gateway.mqtt.gb32960;

import cn.bcd.dataProcess.gateway.mqtt.GatewayProp;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.netty.buffer.ByteBufUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class Consumer_gb32960 implements Consumer<Mqtt5Publish> {

    @Autowired
    KafkaTemplate<byte[],byte[]> kafkaTemplate;

    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    Mqtt5AsyncClient client;


    @Override
    public void accept(Mqtt5Publish mqtt5Publish) {
        byte[] payload = mqtt5Publish.getPayloadAsBytes();
        String vin = new String(payload, 4, 17);
        kafkaTemplate.send(gatewayProp.getParseTopic(), vin.getBytes(), payload);
        byte[] responseByte = response_succeed(payload);
        client.publish(Mqtt5Publish.builder().topic(gatewayProp.getMqttRespTopicPrefix() + vin).payload(responseByte).build());
    }

    public static byte[] response_succeed(byte[] data) {
        byte[] response = new byte[31];
        System.arraycopy(data, 0, response, 0, 30);
        response[3] = 1;
        response[22] = 0;
        response[23] = 6;
        fixCode(response);
        return response;
    }

    /**
     * 修正异或校验位
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fixCode(byte[] data) {
        byte xor = 0;
        int codeIndex = data.length - 1;
        for (int i = 0; i < codeIndex; i++) {
            xor ^= data[i];
        }
        data[codeIndex] = xor;
    }
}
