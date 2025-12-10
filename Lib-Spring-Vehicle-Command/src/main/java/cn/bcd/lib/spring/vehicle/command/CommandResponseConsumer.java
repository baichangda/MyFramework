package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(CommandProp.class)
@ConditionalOnProperty("lib.spring.vehicle.command.senderGroupId")
@Component
public class CommandResponseConsumer extends ThreadDrivenKafkaConsumer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    KafkaProperties kafkaProp;

    public CommandResponseConsumer(CommandProp commandProp) {
        super("commandResponse",
                false,
                1,
                1000,
                1000,
                true,
                0,
                0,
                ConsumerParam.get_singleConsumer(commandProp.responseTopic));
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startConsume(kafkaProp.getConsumer().buildProperties(new DefaultSslBundleRegistry()));
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
        byte[] value = consumerRecord.value();
        String str = new String(value);
        logger.info("receive command response:\n{}", str);
        String id = consumerRecord.key();
        CommandSender.workExecutor.execute(() -> {
            Request<?, ?> request = CommandSender.requestMap.remove(id);
            if (request == null) {
                logger.info("command response failed、request not found、id[{}]", id);
            } else {
                try {
                    logger.info("command response succeed、id[{}]", id);
                    //取消超时任务
                    request.timeoutFuture.cancel(false);
                    //执行回调
                    Response response = JsonUtil.OBJECT_MAPPER.readValue(value, Response.class);
                    response.setCommand(request.getCommand());
                    request.callback.callback(response);
                } catch (Exception ex) {
                    logger.error("error", ex);
                } finally {
                    CommandSender.releaseLock(request.vin, request.flag);
                }
            }
        });
    }
}
