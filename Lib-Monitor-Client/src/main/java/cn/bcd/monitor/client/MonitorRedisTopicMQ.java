package cn.bcd.monitor.client;

import cn.bcd.base.redis.mq.ValueSerializerType;
import cn.bcd.base.redis.mq.topic.RedisTopicMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class MonitorRedisTopicMQ extends RedisTopicMQ<String> {

    static Logger logger = LoggerFactory.getLogger(MonitorRedisTopicMQ.class);

    @Autowired
    RedisTemplate<String, MonitorData> redisTemplate;

    @Autowired(required = false)
    MonitorExtCollector monitorExtCollector;

    public MonitorRedisTopicMQ(RedisConnectionFactory connectionFactory) {
        super(connectionFactory, 1, 1, ValueSerializerType.STRING, "monitorRequest");
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
        redisTemplate.opsForList().leftPush("monitorResponseList", monitorData);
        logger.info("finish monitor request[{}]", data);
    }
}
