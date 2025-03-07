package cn.bcd.server.data.process.gateway.mqtt;

import cn.bcd.server.data.process.gateway.mqtt.gb32960.Consumer_gb32960;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class Consumer_dispatch implements Consumer<Mqtt5Publish> {

    static Logger logger = LoggerFactory.getLogger(Consumer_dispatch.class);

    @Autowired
    Consumer_gb32960 consumer_gb32960;

    public void accept(Mqtt5Publish mqtt5Publish) {
        byte[] payload = mqtt5Publish.getPayloadAsBytes();
        if (payload[0] == 0x23 && payload[1] == 0x23) {
            consumer_gb32960.accept(mqtt5Publish);
        } else {
            logger.info("receive data header[{}] not support、hex:\n{}",
                    ByteBufUtil.hexDump(payload, 0, 2),
                    ByteBufUtil.hexDump(payload));
        }
    }
}
