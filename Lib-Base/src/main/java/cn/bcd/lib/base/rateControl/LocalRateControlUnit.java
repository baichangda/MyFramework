package cn.bcd.lib.base.rateControl;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流量控制单元
 */
public class LocalRateControlUnit implements AutoCloseable {
    public final String name;
    public final int timeInSecond;
    public final int maxAccessCount;
    public final int waitTimeWhenExceedInMillis;
    public final ScheduledExecutorService resetExecutor;
    ScheduledFuture<?> scheduledFuture;

    private volatile boolean available = false;

    static final int RESET_EXECUTOR_NUM = 1;
    static final ScheduledExecutorService[] resetPool = new ScheduledExecutorService[RESET_EXECUTOR_NUM];

    static {
        for (int i = 0; i < resetPool.length; i++) {
            int no = i + 1;
            resetPool[i] = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "localRateControl(" + no + "/" + resetPool.length + ")"));
        }
    }

    private final AtomicInteger count = new AtomicInteger();

    /**
     * 创建一个流量控制单元
     *
     * @param name                       名称、用于线程名
     * @param timeInSecond               限定时间
     * @param maxAccessCount             限定时间访问次数
     * @param waitTimeWhenExceedInMillis 当发生超过访问次数的访问时候、线程等待的时间
     */
    public LocalRateControlUnit(String name,
                                int timeInSecond,
                                int maxAccessCount,
                                int waitTimeWhenExceedInMillis) {
        this.name = name;
        this.timeInSecond = timeInSecond;
        this.maxAccessCount = maxAccessCount;
        this.waitTimeWhenExceedInMillis = waitTimeWhenExceedInMillis;
        this.resetExecutor = resetPool[Math.floorMod(name.hashCode(), resetPool.length)];
        init();
    }

    private synchronized void init() {
        if (available) {
            return;
        }
        available = true;
        this.scheduledFuture = this.resetExecutor.scheduleAtFixedRate(() -> count.set(0), timeInSecond * 1000L - DateUtil.CacheMillisecond.current() % 1000, timeInSecond * 1000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (!available) {
            return;
        }
        available = false;
        if (this.scheduledFuture != null) {
            this.scheduledFuture.cancel(true);
            this.scheduledFuture = null;
        }
    }

    public boolean tryAdd(int i) {
        if (!available) {
            throw BaseException.get("rate control unit closed");
        }
        while (true) {
            final int current = count.get();
            final int next = current + i;
            if (next > maxAccessCount) {
                return false;
            }
            if (count.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public void add(int i) throws InterruptedException {
        while (!tryAdd(i)) {
            TimeUnit.MILLISECONDS.sleep(waitTimeWhenExceedInMillis);
        }
    }


}
