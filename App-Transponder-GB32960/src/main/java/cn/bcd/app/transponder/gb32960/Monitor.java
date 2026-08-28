package cn.bcd.app.transponder.gb32960;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public final class Monitor {
    private static final Logger logger = LoggerFactory.getLogger(Monitor.class);
    public static final Set<ClientMetric> clientMetrics = ConcurrentHashMap.newKeySet();
    private static ScheduledExecutorService executor;

    private Monitor() {
    }

    public static synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "gb32960-monitor");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(Monitor::logMetrics, 30, 30, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        clientMetrics.clear();
    }

    private static void logMetrics() {
        try {
            long unLoginCount = 0;
            long loginCount = 0;
            for (ClientMetric metric : clientMetrics) {
                unLoginCount += metric.unLoginCount.sum();
                loginCount += metric.loginCount.sum();
            }
            logger.info("client metrics: activeClients[{}] count[{},{}]",
                    clientMetrics.size(), unLoginCount, loginCount);
        } catch (Exception e) {
            logger.warn("log client metrics error", e);
        }
    }

    public static class ClientMetric {
        public final Date connectTime;
        public volatile String username;
        public final LongAdder unLoginCount = new LongAdder();
        public final LongAdder loginCount = new LongAdder();

        public ClientMetric(Date connectTime) {
            this.connectTime = connectTime;
        }
    }
}
