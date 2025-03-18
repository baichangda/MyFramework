package cn.bcd.lib.base.executor.queue;

import java.util.concurrent.locks.LockSupport;

public enum WaitStrategy {

    SPIN(idleCounter -> idleCounter + 1),
    YIELD(idleCounter -> {
        Thread.yield();
        return idleCounter + 1;
    }),
    PARK(idleCounter -> {
        LockSupport.parkNanos(1L);
        return idleCounter + 1;
    }),
    PROGRESSIVE(idleCounter -> {
        if (idleCounter > 200) {
            LockSupport.parkNanos(1L);
        } else if (idleCounter > 100) {
            Thread.yield();
        }
        return idleCounter + 1;
    }),
    PROGRESSIVE_MS(idleCounter -> {
        if (idleCounter > 200) {
            //休眠10ms
            LockSupport.parkNanos(10_000_000);
        } else if (idleCounter > 100) {
            Thread.yield();
        }
        return idleCounter + 1;
    });

    private final Idle idle;

    public int idle(final int idleCounter) {
        return idle.idle(idleCounter);
    }

    WaitStrategy(final Idle idle) {
        this.idle = idle;
    }
}