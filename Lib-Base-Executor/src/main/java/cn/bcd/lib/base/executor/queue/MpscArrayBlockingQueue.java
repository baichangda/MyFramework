package cn.bcd.lib.base.executor.queue;

import org.jctools.queues.MpscArrayQueue;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * 和log4j-core中MpscBlockingQueue实现一致
 * @param <E> 队列元素类型
 */
public class MpscArrayBlockingQueue<E> extends MpscArrayQueue<E> implements BlockingQueue<E> {
    private final cn.bcd.lib.base.executor.queue.WaitStrategy waitStrategy;

    public MpscArrayBlockingQueue(final int capacity, final cn.bcd.lib.base.executor.queue.WaitStrategy waitStrategy) {
        super(capacity);
        this.waitStrategy = waitStrategy;
    }

    @Override
    public int drainTo(final Collection<? super E> c) {
        return drainTo(c, capacity());
    }

    @Override
    public int drainTo(final Collection<? super E> c, final int maxElements) {
        return drain(c::add, maxElements);
    }

    @Override
    public boolean offer(final E e, final long timeout, final TimeUnit unit) throws InterruptedException {
        int idleCounter = 0;
        final long timeoutNanos = System.nanoTime() + unit.toNanos(timeout);
        do {
            if (offer(e)) {
                return true;
            } else if (System.nanoTime() - timeoutNanos > 0) {
                return false;
            }
            idleCounter = waitStrategy.idle(idleCounter);
        } while (!Thread.interrupted()); // clear interrupted flag
        throw new InterruptedException();
    }

    @Override
    public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        int idleCounter = 0;
        final long timeoutNanos = System.nanoTime() + unit.toNanos(timeout);
        do {
            final E result = poll();
            if (result != null) {
                return result;
            } else if (System.nanoTime() - timeoutNanos > 0) {
                return null;
            }
            idleCounter = waitStrategy.idle(idleCounter);
        } while (!Thread.interrupted()); // clear interrupted flag
        throw new InterruptedException();
    }

    @Override
    public void put(final E e) throws InterruptedException {
        int idleCounter = 0;
        do {
            if (offer(e)) {
                return;
            }
            idleCounter = waitStrategy.idle(idleCounter);
        } while (!Thread.interrupted()); // clear interrupted flag
        throw new InterruptedException();
    }

    @Override
    public boolean offer(final E e) {
        // keep 2 cache lines empty to avoid false sharing that will slow the consumer thread when queue is full.
        return offerIfBelowThreshold(e, capacity() - 32);
    }

    @Override
    public int remainingCapacity() {
        return capacity() - size();
    }

    @Override
    public E take() throws InterruptedException {
        int idleCounter = 100;
        do {
            final E result = relaxedPoll();
            if (result != null) {
                return result;
            }
            idleCounter = waitStrategy.idle(idleCounter);
        } while (!Thread.interrupted()); // clear interrupted flag
        throw new InterruptedException();
    }
}