package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@EnableConfigurationProperties(CommandProp.class)
@ConditionalOnProperty("lib.spring.vehicle.command.senderGroupId")
@Component
@SuppressWarnings("unchecked")
public class CommandResponseConsumer extends ThreadDrivenKafkaConsumer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    KafkaProperties kafkaProp;

    public CommandResponseConsumer(CommandProp commandProp) {
        super("commandResponse",
                1,
                1000,
                true,
                0,
                0,
                ConsumerParam.get_singleConsumer(commandProp.responseTopic));
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startConsume(kafkaProp.getConsumer().buildProperties());
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord, SingleThreadEventExecutor executor) {
        byte[] value = consumerRecord.value();
        String str = value == null ? null : new String(value, StandardCharsets.UTF_8);
        logger.info("receive command response:\n{}", str);
        String id = consumerRecord.key();
        CommandSender.workExecutor.execute(() -> {
            Request<?, ?> request = CommandSender.requestMap.get(id);
            if (request == null) {
                logger.info("command response failed、request not found、id[{}]", id);
            } else {
                Response response;
                try {
                    response = JsonUtil.MAPPER.readValue(value, Response.class);
                    if (response == null) {
                        throw new IllegalArgumentException("Command response must not be null");
                    }
                } catch (Exception ex) {
                    logger.error("command response parse failed, id[{}]", id, ex);
                    CommandSender.complete(id, ResponseStatus.program_error);
                    return;
                }
                CommandSender.requestMap.remove(id);
                try {
                    logger.info("command response succeed、id[{}]", id);
                    //取消超时任务
                    request.timeoutFuture.cancel(false);
                    //执行回调
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
