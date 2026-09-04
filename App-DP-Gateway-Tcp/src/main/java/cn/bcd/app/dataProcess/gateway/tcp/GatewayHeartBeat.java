package cn.bcd.app.dataProcess.gateway.tcp;

import cn.bcd.lib.base.util.DateZoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    static Logger logger = LoggerFactory.getLogger(GatewayHeartBeat.class);
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
            try {
                redisTemplate.opsForHash().put(gatewayOnline_redisHashKey,
                        gatewayProp.id,
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date()));
            } catch (Exception ex) {
                logger.error("heartbeat error", ex);
            }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startHeartBeatToRedis();
    }
}
