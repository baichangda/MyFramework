package cn.bcd.monitor.collector;

import cn.bcd.base.exception.BaseException;
import cn.bcd.base.json.JsonUtil;
import cn.bcd.base.redis.RedisUtil;
import cn.bcd.base.util.DateZoneUtil;
import cn.bcd.monitor.client.MonitorData;
import cn.bcd.monitor.client.MonitorProp;
import cn.bcd.monitor.client.MonitorRedisTopicMQ;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EnableConfigurationProperties(MonitorProp.class)
@Component
public class MonitorCollector {

    @Autowired
    MonitorProp monitorProp;

    @Autowired
    MonitorRedisTopicMQ monitorRedisTopicMQ;

    RedisTemplate<String, MonitorData> redisTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;


    static Logger logger = LoggerFactory.getLogger(MonitorCollector.class);

    public MonitorCollector(RedisConnectionFactory redisConnectionFactory) {
        redisTemplate = RedisUtil.newString_JacksonBeanRedisTemplate(redisConnectionFactory, MonitorData.class);
    }

    @Scheduled(cron = "${monitor.collect-cron}")
    public void collect() {
        String dateStr = DateZoneUtil.dateToString_second(new Date());
        long batch = Long.parseLong(dateStr);
        logger.info("start batch[{}]", batch);
        monitorRedisTopicMQ.send(dateStr);
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }
        //等待10s
        List<MonitorData> list = redisTemplate.opsForList().range(monitorProp.getResponseList(), 0, -1);
        if (list == null) {
            list = new ArrayList<>();
        }
        logger.info("receive batch[{}] serverIds[{}]", batch, list.stream().map(e -> e.serverId).collect(Collectors.joining(",")));
        List<SaveData> saveDataList = new ArrayList<>();
        List<ServerData> serverDataList = getAllServerData();
        Map<String, MonitorData> serverId_monitorData = list.stream().collect(Collectors.toMap(e -> e.serverId, e -> e));
        for (ServerData serverData : serverDataList) {
            SaveData saveData = new SaveData(serverData, batch, serverId_monitorData.get(serverData.serverId));
            logger.info("receive batch[{}] serverId[{}] status[{}]", saveData.batch, saveData.serverId, saveData.status);
            saveDataList.add(saveData);
        }
        insert(saveDataList);
    }

    private void insert(List<SaveData> list) {
        List<Object[]> args = list.stream().map(e -> new Object[]{e.serverId, e.serverType, e.batch, e.status, e.systemData, e.extData}).toList();
        jdbcTemplate.batchUpdate("""
                insert into t_monitor_data(server_id,server_type,batch,status,system_data,ext_data)
                values(?,?,?,?,?,?)
                """, args);
    }

    private List<ServerData> getAllServerData() {
        return jdbcTemplate.query("select * from t_server_data", new BeanPropertyRowMapper<>(ServerData.class));
    }
}
