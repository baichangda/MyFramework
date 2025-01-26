package cn.bcd.dataProcess.gateway.tcp;

import cn.bcd.base.util.DateZoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GatewayHeartBeat implements ApplicationListener<ContextRefreshedEvent> {
    //保持网关在redis状态
    static ScheduledExecutorService pool_saveRedis_heartBeat = Executors.newSingleThreadScheduledExecutor();
    @Autowired
    GatewayProp gatewayProp;
    @Autowired
    @Qualifier("string_string_redisTemplate")
    RedisTemplate<String, String> redisTemplate;

    /**
     * set key gatewayId val
     */
    static String gatewayOnline_redisHashKey = "gw-online";

    private void startHeartBeatToRedis() {
        long seconds = gatewayProp.heartBeatPeriod.getSeconds();
        pool_saveRedis_heartBeat.scheduleAtFixedRate(() -> {
            redisTemplate.opsForHash().put(gatewayOnline_redisHashKey,
                    gatewayProp.id,
                    DateZoneUtil.dateToString_second(new Date()));
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startHeartBeatToRedis();
    }
}
