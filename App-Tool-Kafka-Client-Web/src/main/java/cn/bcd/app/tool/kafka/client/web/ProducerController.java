package cn.bcd.app.tool.kafka.client.web;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.kafka.KafkaUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;


@RestController
public class ProducerController {

    static Logger logger = LoggerFactory.getLogger(ProducerController.class);

    @PostMapping("/producer")
    public Result<?> send(@RequestBody ProducerParam param) {
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(Arrays.asList(param.kafkaAddrs));
        Map<String, Object> prop = producerProp.buildProperties(new DefaultSslBundleRegistry());
        String[] messages;
        try (KafkaProducer<String, String> producer = KafkaUtil.newKafkaProducer_string_string(prop)) {
            if (param.msgSplitType == null || param.msgSplitType.isEmpty()) {
                messages = new String[]{param.data};
                producer.send(new ProducerRecord<>(param.kafkaTopic, param.data));
            } else {
                if ("1".equals(param.msgSplitType)) {
                    messages = param.data.split("\n");
                } else {
                    messages = param.data.split(param.msgSplit);
                }
            }
            for (String s : messages) {
                producer.send(new ProducerRecord<>(param.kafkaTopic, s));
            }
            return Result.success().message("发送" + messages.length + "条数据到kafka成功");
        } catch (Exception ex) {
            logger.error("error", ex);
            return Result.fail().message("发送数据到kafka失败、错误原因:[" + ex.getMessage() + "]");
        }
    }
}
