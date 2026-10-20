package cn.bcd.app.monitor.collector;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.spring.monitor.client.MonitorData;
import cn.bcd.lib.spring.monitor.client.MonitorProp;
import cn.bcd.lib.spring.monitor.client.MonitorRedisTopicMQ;
import cn.bcd.lib.spring.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@EnableConfigurationProperties(MonitorProp.class)
@Component
public class MonitorCollector {
    static Logger logger = LoggerFactory.getLogger(MonitorCollector.class);

    @Autowired
    MonitorProp monitorProp;
    @Autowired
    MonitorRedisTopicMQ monitorRedisTopicMQ;
    @Autowired
    JdbcTemplate jdbcTemplate;

    RedisTemplate<String, MonitorData> redisTemplate;

    public MonitorCollector(RedisConnectionFactory connectionFactory) {
        redisTemplate = RedisUtil.newRedisTemplate_string_jackson(connectionFactory, MonitorData.class);
    }

    // 同一个监控域只运行一个 Collector；不同域应使用不同的 topic 和 responseList。
    @Scheduled(cron = "${lib.spring.monitor.client.collect-cron:${lib.spring.monitor.client.collectCron:0/15 * * * * *}}")
    public synchronized void collect() {
        // 先读取监控名单，再广播采集请求。
        List<ServerData> servers = jdbcTemplate.query("select * from t_server_data",
                new BeanPropertyRowMapper<>(ServerData.class));
        String request = DateUtil.dateToStr_yyyyMMddHHmmss(new Date());
        long batch = Long.parseLong(request);
        logger.info("start batch[{}]", batch);
        monitorRedisTopicMQ.send(request);
        try {
            awaitResponses();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        List<MonitorData> responses = redisTemplate.opsForList().range(monitorProp.responseKey(batch), 0, -1);
        List<SaveData> rows = buildSaveData(servers, batch, responses);
        // 每批只处理一次；迟到响应仅进入原批次队列，遗留队列由 TTL 清理。
        redisTemplate.delete(monitorProp.responseKey(batch));
        insert(rows);
        logger.info("saved batch[{}] servers[{}]", batch, rows.size());
    }

    protected void awaitResponses() throws InterruptedException {
        TimeUnit.SECONDS.sleep(10);
    }

    static List<SaveData> buildSaveData(List<ServerData> servers, long batch, List<MonitorData> responses) {
        Map<String, MonitorData> byServer = new HashMap<>();
        if (responses != null) {
            // LPUSH + LRANGE 返回新响应在前，重复 ID 只保留最新有效响应。
            for (MonitorData response : responses) {
                if (response == null || response.batch != batch || response.serverId == null
                        || response.serverId.isBlank() || response.systemData == null) {
                    logger.warn("ignore invalid or mismatched response for batch[{}]", batch);
                    continue;
                }
                byServer.putIfAbsent(response.serverId, response);
            }
        }
        List<SaveData> result = new ArrayList<>();
        for (ServerData server : servers) {
            result.add(new SaveData(server, batch, byServer.get(server.serverId)));
        }
        return result;
    }

    private void insert(List<SaveData> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> args = rows.stream()
                .map(e -> new Object[]{e.serverId, e.serverType, e.batch, e.status, e.systemData, e.extData})
                .toList();
        jdbcTemplate.batchUpdate("""
                insert into t_monitor_data(server_id,server_type,batch,status,system_data,ext_data)
                values(?,?,?,?,?,?)
                """, args);
    }
}
