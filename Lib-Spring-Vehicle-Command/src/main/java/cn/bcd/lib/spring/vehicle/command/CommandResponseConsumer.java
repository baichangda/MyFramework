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
        CommandSender.workExecutor.execute(() -> {
            String id = consumerRecord.key();
            Request<?, ?> request = CommandSender.requestMap.remove(id);
            if (request == null) {
                logger.debug("command response ignored, request not found, id[{}]", id);
                return;
            }
            try {
                request.timeoutFuture.cancel(false);
                Response response;
                try {
                    response = JsonUtil.MAPPER.readValue(consumerRecord.value(), Response.class);
                    if (response == null) {
                        throw new IllegalArgumentException("Command response must not be null");
                    }
                } catch (Exception ex) {
                    logger.error("command response parse failed, id[{}]", id, ex);
                    response = new Response<>(request.vin, request.flag, ResponseStatus.program_error, request.version, null);
                }
                response.setCommand(request.command);
                request.callback.callback(response);
            } catch (Exception ex) {
                logger.error("command response callback failed, id[{}]", id, ex);
            } finally {
                CommandSender.releaseLock(request.vin, request.flag);
            }
        });
    }
}
