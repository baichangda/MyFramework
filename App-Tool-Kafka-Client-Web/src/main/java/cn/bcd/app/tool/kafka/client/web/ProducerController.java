package cn.bcd.app.tool.kafka.client.web;

import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import io.netty.util.concurrent.CompleteFuture;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


@RestController
public class ProducerController {

    static Logger logger = LoggerFactory.getLogger(ProducerController.class);

    @PostMapping("/producer")
    public Result<?> send(@RequestBody ProducerParam param) {
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(Arrays.asList(param.kafkaAddrs));
        Map<String, Object> prop = producerProp.buildProperties();
        String[] messages;
        try (KafkaProducer<String, String> producer = KafkaUtil.newKafkaProducer_string_string(prop)) {
            if (param.msgSplitType == null || param.msgSplitType.isEmpty()) {
                messages = new String[]{param.data};
            } else {
                if ("1".equals(param.msgSplitType)) {
                    messages = param.data.split("\n");
                } else {
                    messages = param.data.split(param.msgSplit);
                }
            }
            List<Future<?>> futureList = new ArrayList<>();
            for (String s : messages) {
                futureList.add(producer.send(new ProducerRecord<>(param.kafkaTopic, s)));
            }
            for (Future<?> future : futureList) {
                future.get();
            }
            return Result.successMessage("发送{}条数据到kafka成功", messages.length);
        } catch (Exception ex) {
            logger.error("error", ex);
            return Result.fail("发送数据到kafka失败、错误原因:{}", ex.getMessage());
        }
    }
}
