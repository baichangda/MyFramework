package cn.bcd.app.transponder.gb32960;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class Monitor {
    static Logger logger = LoggerFactory.getLogger(Monitor.class);
    public final static Set<ClientMetric> clientMetrics = ConcurrentHashMap.newKeySet();

    public static void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            StringJoiner sj = new StringJoiner("\n");
            List<ClientMetric> list = clientMetrics.stream().sorted(Comparator.comparing(e->e.connectTime)).toList();
            for (ClientMetric clientMetric : list) {
                sj.add(StringUtil.format("username[{}] connectTime[{}] count[{},{}]",
                        clientMetric.username == null ? "" : clientMetric.username,
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(clientMetric.connectTime),
                        clientMetric.unLoginCount.sum(),
                        clientMetric.loginCount.sum()));
            }
            logger.info("client metrics:\n{}", sj);
        }, 3, 3, TimeUnit.SECONDS);
    }

    public static class ClientMetric {
        public Date connectTime;
        public String username;
        public LongAdder unLoginCount = new LongAdder();
        public LongAdder loginCount = new LongAdder();
        public ClientMetric(Date connectTime) {
            this.connectTime = connectTime;
        }
    }
}
