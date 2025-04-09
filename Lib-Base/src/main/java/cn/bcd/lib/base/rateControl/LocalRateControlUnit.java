package cn.bcd.lib.base.rateControl;

import cn.bcd.lib.base.util.DateUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流量控制单元
 */
public class LocalRateControlUnit {
    public final String name;
    public final int timeInSecond;
    public final int maxAccessCount;
    public final int waitMills;
    public final ScheduledExecutorService resetExecutor;
    ScheduledFuture<?> scheduledFuture;

    static final int RESET_EXECUTOR_NUM = Runtime.getRuntime().availableProcessors();
    static final ScheduledExecutorService[] resetPool = new ScheduledExecutorService[RESET_EXECUTOR_NUM];

    static {
        for (int i = 0; i < resetPool.length; i++) {
            int no = i + 1;
            resetPool[i] = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "localRateControl(" + no + "/" + resetPool.length + ")"));
        }
    }

    public final AtomicInteger count = new AtomicInteger();

    public LocalRateControlUnit(String name,
                                int timeInSecond,
                                int maxAccessCount,
                                int waitMills) {
        this.name = name;
        this.timeInSecond = timeInSecond;
        this.maxAccessCount = maxAccessCount;
        this.waitMills = waitMills;
        this.resetExecutor = resetPool[Math.floorMod(name.hashCode(), resetPool.length)];
    }

    public synchronized void init() {
        this.scheduledFuture = this.resetExecutor.scheduleAtFixedRate(() -> count.set(0), timeInSecond * 1000L + DateUtil.CacheMillisecond.current() % 1000, timeInSecond * 1000L, TimeUnit.MILLISECONDS);
    }

    public synchronized void destroy() {
        if (this.scheduledFuture != null) {
            this.scheduledFuture.cancel(true);
            this.scheduledFuture = null;
        }
    }

    public void add(int i) throws InterruptedException {
        final int c = count.addAndGet(i);
        if (c >= maxAccessCount) {
            do {
                TimeUnit.MILLISECONDS.sleep(waitMills);
            } while (count.get() >= maxAccessCount);
        }
    }
}
