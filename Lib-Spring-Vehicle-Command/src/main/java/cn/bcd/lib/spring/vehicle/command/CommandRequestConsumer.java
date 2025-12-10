package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(CommandProp.class)
@ConditionalOnProperty("lib.spring.vehicle.command.receiverGroupId")
@Component
public class CommandRequestConsumer extends ThreadDrivenKafkaConsumer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    KafkaProperties kafkaProp;

    @Autowired
    CommandReceiver commandReceiver;

    static KafkaProducer<String, byte[]> kafkaProducer;
    static CommandProp commandProp;

    public CommandRequestConsumer(CommandProp commandProp,
                                  KafkaProperties kafkaProp) {
        super("commandResponse",
                false,
                1,
                1000,
                1000,
                true,
                0,
                0,
                ConsumerParam.get_singleConsumer(commandProp.requestTopic));
        CommandRequestConsumer.commandProp = commandProp;
        CommandRequestConsumer.kafkaProducer = KafkaUtil.newKafkaProducer_string_bytes(kafkaProp.getProducer().buildProperties(new DefaultSslBundleRegistry()));
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startConsume(kafkaProp.getConsumer().buildProperties(new DefaultSslBundleRegistry()));
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) throws Exception {
        logger.info("receive command request:\n{}", new String(consumerRecord.value()));
        Request request = JsonUtil.OBJECT_MAPPER.readValue(consumerRecord.value(), Request.class);
        commandReceiver.onRequest(request);
    }
}
