package cn.bcd.dataProcess.gateway;

import cn.bcd.dataProcess.gateway.prop.GatewayProp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
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

    private void startHeartBeatToRedis() {
        final Duration maxBeforeOffline = gatewayProp.tcp.maxBeforeOffline;
        final String key = RedisKeyConst.gatewayOnline_redisKeyPre + gatewayProp.id;
        final long period = maxBeforeOffline.getSeconds() / 2 - 1;
        pool_saveRedis_heartBeat.scheduleAtFixedRate(() -> {
            redisTemplate.opsForValue().set(key, "", maxBeforeOffline);
        }, period, period, TimeUnit.SECONDS);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        startHeartBeatToRedis();
    }
}
