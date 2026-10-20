package cn.bcd.lib.spring.monitor.client;

import cn.bcd.lib.spring.redis.RedisUtil;
import cn.bcd.lib.spring.redis.mq.RedisTopicMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

@EnableConfigurationProperties(MonitorProp.class)
@Component
public class MonitorRedisTopicMQ extends RedisTopicMQ<String> implements InitializingBean, DisposableBean {
    static Logger logger = LoggerFactory.getLogger(MonitorRedisTopicMQ.class);

    // 单键脚本兼容 Redis Cluster；迟到的响应最多保留一天。
    static final DefaultRedisScript<Long> RESPONSE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('LPUSH', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], 86400)
            return 1
            """, Long.class);

    RedisTemplate<String, MonitorData> redisTemplate;

    @Autowired(required = false)
    MonitorExtCollector monitorExtCollector;

    final MonitorProp monitorProp;

    public MonitorRedisTopicMQ(RedisConnectionFactory connectionFactory, MonitorProp monitorProp) {
        super(connectionFactory, 1, 1, String.class, monitorProp.requestTopic);
        this.monitorProp = monitorProp;
        Assert.hasText(monitorProp.requestTopic, "requestTopic must not be blank");
        Assert.hasText(monitorProp.responseList, "responseList must not be blank");
        if (monitorProp.serverId != null) {
            Assert.hasText(monitorProp.serverId, "serverId must not be blank");
            redisTemplate = RedisUtil.newRedisTemplate_string_jackson(connectionFactory, MonitorData.class);
        }
    }

    @Override
    public void afterPropertiesSet() {
        // 扩展采集器注入完成后再接受请求。
        if (monitorProp.serverId != null) {
            init();
        }
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public void onMessage(String data) {
        if (monitorProp.serverId == null) {
            return;
        }
        long batch = Long.parseLong(data);
        if (batch <= 0) {
            throw new IllegalArgumentException("batch must be positive");
        }
        logger.info("receive monitor request[{}]", data);
        MonitorData monitorData = new MonitorData();
        monitorData.serverId = monitorProp.serverId;
        monitorData.serverType = monitorProp.serverType;
        monitorData.batch = batch;
        monitorData.systemData = collectSystemData();
        if (monitorExtCollector != null) {
            try {
                monitorData.extData = monitorExtCollector.collect();
            } catch (Exception e) {
                logger.warn("extension collection failed for batch[{}]", batch, e);
            }
        }
        redisTemplate.execute(RESPONSE_SCRIPT, List.of(monitorProp.responseKey(batch)), monitorData);
        logger.info("finish monitor request[{}]", data);
    }

    protected SystemData collectSystemData() {
        return MonitorUtil.collect();
    }
}
