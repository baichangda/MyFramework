package cn.bcd.lib.spring.monitor.client;

import cn.bcd.lib.spring.redis.RedisUtil;
import cn.bcd.lib.spring.redis.mq.ValueSerializerType;
import cn.bcd.lib.spring.redis.mq.topic.RedisTopicMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

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
                monitorProp.requestTopic);
        this.monitorProp = monitorProp;
        if (monitorProp.serverId != null) {
            this.redisTemplate = RedisUtil.newRedisTemplate_string_jackson(connectionFactory, MonitorData.class);
            initConsumer();
        }
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
        redisTemplate.opsForList().leftPush(monitorProp.responseList, monitorData);
        logger.info("finish monitor request[{}]", data);
    }
}
