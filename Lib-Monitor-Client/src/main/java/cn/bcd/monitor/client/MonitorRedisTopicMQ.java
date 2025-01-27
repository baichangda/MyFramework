package cn.bcd.monitor.client;

import cn.bcd.base.redis.RedisUtil;
import cn.bcd.base.redis.mq.ValueSerializerType;
import cn.bcd.base.redis.mq.topic.RedisTopicMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@ConditionalOnProperty("monitor.monitorRequestTopic")
@EnableConfigurationProperties(MonitorProp.class)
@Component
public class MonitorRedisTopicMQ extends RedisTopicMQ<String> {

    static Logger logger = LoggerFactory.getLogger(MonitorRedisTopicMQ.class);

    RedisTemplate<String, MonitorData> redisTemplate;

    @Autowired(required = false)
    MonitorExtCollector monitorExtCollector;

    final MonitorProp monitorProp;

    public MonitorRedisTopicMQ(RedisConnectionFactory connectionFactory, MonitorProp monitorProp) {
        super(connectionFactory, 1, 1, ValueSerializerType.STRING,
                monitorProp.monitorRequestTopic);
        this.monitorProp = monitorProp;
        this.redisTemplate= RedisUtil.newString_JacksonBeanRedisTemplate(connectionFactory, MonitorData.class);
    }

    @Override
    public void onMessage(String data) {
        logger.info("receive monitor request[{}]", data);
        SystemData systemData = MonitorUtil.collect();
        MonitorData monitorData = new MonitorData();
        monitorData.systemData = systemData;
        if (monitorExtCollector != null) {
            monitorData.extData = monitorExtCollector.collect();
        }
        redisTemplate.opsForList().leftPush(monitorProp.monitorResponseList, monitorData);
        logger.info("finish monitor request[{}]", data);
    }
}
