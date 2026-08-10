package cn.bcd.lib.base.rateControl;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于进程内状态的固定窗口流量控制单元。
 */
public class LocalRateControlUnit implements AutoCloseable {
    private static final int COUNT_BITS = 31;
    private static final long COUNT_MASK = (1L << COUNT_BITS) - 1;
    private static final long WINDOW_MASK = (1L << (Long.SIZE - COUNT_BITS)) - 1;
    private static final long ADD_SUCCEED = 0;

    public final String name;
    public final int timeInSecond;
    public final int maxAccessCount;

    private final long windowInMillis;
    private final AtomicLong state = new AtomicLong();

    private volatile boolean available = true;

    /**
     * 创建一个流量控制单元。
     *
     * @param name           名称
     * @param timeInSecond   固定窗口秒数
     * @param maxAccessCount 窗口内允许使用的最大额度
     */
    public LocalRateControlUnit(String name,
                                int timeInSecond,
                                int maxAccessCount) {
        this.name = Objects.requireNonNull(name, "name");
        if (timeInSecond <= 0) {
            throw new IllegalArgumentException("timeInSecond must be greater than 0");
        }
        if (maxAccessCount <= 0) {
            throw new IllegalArgumentException("maxAccessCount must be greater than 0");
        }
        this.timeInSecond = timeInSecond;
        this.maxAccessCount = maxAccessCount;
        this.windowInMillis = timeInSecond * 1000L;
    }

    @Override
    public void close() {
        available = false;
    }

    public boolean tryAdd(int i) {
        validateIncrement(i);
        return tryAddInternal(i) == ADD_SUCCEED;
    }

    public void add(int i) throws InterruptedException {
        validateIncrement(i);
        while (true) {
            long result = tryAddInternal(i);
            if (result == ADD_SUCCEED) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(-result);
        }
    }

    /**
     * @return 0 表示成功，负数的绝对值表示失败后需要等待的毫秒数
     */
    private long tryAddInternal(int i) {
        while (true) {
            if (!available) {
                throw BaseException.get("rate control unit closed");
            }
            long now = DateUtil.CacheMillisecond.current();
            long currentWindowId = Math.floorDiv(now, windowInMillis) & WINDOW_MASK;
            long currentState = state.get();
            long stateWindowId = currentState >>> COUNT_BITS;
            long currentCount = currentState & COUNT_MASK;

            if (stateWindowId == currentWindowId && i > maxAccessCount - currentCount) {
                long retryAfterMillis = windowInMillis - Math.floorMod(now, windowInMillis);
                return -retryAfterMillis;
            }

            long nextCount = stateWindowId == currentWindowId ? currentCount + i : i;
            long nextState = (currentWindowId << COUNT_BITS) | nextCount;
            if (state.compareAndSet(currentState, nextState)) {
                return ADD_SUCCEED;
            }
        }
    }

    private void validateIncrement(int i) {
        if (i <= 0) {
            throw new IllegalArgumentException("i must be greater than 0");
        }
        if (i > maxAccessCount) {
            throw new IllegalArgumentException("i must not be greater than maxAccessCount");
        }
    }
}
