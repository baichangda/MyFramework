package cn.bcd.lib.spring.redis.register;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.redis.RedisUtil;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class RegisterInfo {
    public final RegisterServer server;
    final BoundHashOperations<String, String, String> boundHashOperations;

    record Info(String[] hosts, long lastUpdateTs) {

    }

    volatile Info info;
    AtomicLong index = new AtomicLong(0);

    public RegisterInfo(RegisterServer server, RedisConnectionFactory redisConnectionFactory) {
        this.server = server;
        this.boundHashOperations = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory).boundHashOps(RegisterUtil.redisKeyPre + server.name());
    }

    public String[] hosts() {
        Info temp = this.info;
        long curTs = System.currentTimeMillis();
        long localExpireTs = curTs - server.consumer_localCacheExpired_ms;
        if (temp == null || temp.lastUpdateTs < localExpireTs) {
            synchronized (this) {
                temp = this.info;
                curTs = System.currentTimeMillis();
                localExpireTs = curTs - server.consumer_localCacheExpired_ms;
                if (temp == null || temp.lastUpdateTs < localExpireTs) {
                    //从redis中加载
                    final Map<String, String> entries = boundHashOperations.entries();
                    final String[] hosts;
                    if (entries == null || entries.isEmpty()) {
                        hosts = new String[0];
                    } else {
                        List<String> hostList = new ArrayList<>();
                        for (Map.Entry<String, String> entry : entries.entrySet()) {
                            long heartbeatTs = DateZoneUtil.strToDate_yyyyMMddHHmmss(entry.getValue()).getTime();
                            long currentTs = System.currentTimeMillis();
                            if (currentTs - heartbeatTs <= server.consumer_providerInfoExpired_ms) {
                                hostList.add(entry.getKey());
                            }
                        }
                        if (hostList.isEmpty()) {
                            hosts = new String[0];
                        } else {
                            hosts = hostList.toArray(new String[0]);
                            Arrays.sort(hosts);
                        }
                    }
                    this.info = new Info(hosts, curTs);
                    return hosts;
                } else {
                    return temp.hosts;
                }
            }
        } else {
            return temp.hosts;
        }
    }

    public String host() {
        String[] hosts = hosts();
        if (hosts.length == 0) {
            return null;
        }
        return hosts[(int) (index.getAndIncrement() % hosts.length)];
    }

    public void clearCache() {
        synchronized (this) {
            info = null;
        }
    }
}
