package cn.bcd.app.businessProcess.backend.base.support_task;

import cn.bcd.lib.base.util.DateUtil;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface TaskFunction<T extends Task<K>, K extends Serializable> {
    /**
     * task执行任务内容
     *
     * @param runnable 上下文环境
     * @return true: 执行成功、false: 任务被打断
     */
    boolean execute(TaskRunnable<T, K> runnable) throws Exception;

    /**
     * 启动一个线程池、周期更新任务信息
     * <p>
     * 注意、需要手动关闭线程池
     *
     * @param runnable
     * @param period
     * @param doBeforeUpdate
     * @return
     */
    default ScheduledExecutorService updateTaskPeriodInNewThread(TaskRunnable<T, K> runnable, Duration period, Consumer<T> doBeforeUpdate) {
        final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            final T task = runnable.task;
            doBeforeUpdate.accept(task);
            runnable.taskDao.doUpdate(task);
        }, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return scheduledExecutorService;
    }


    /**
     * 在循环中使用更新任务、保证更新的周期不小于 period 参数
     * 主要是使用在for循环中、避免过快的更新
     * <p>
     * 备注:
     * 需要显式的在for循环中需要保存的地方调用
     *
     * @param runnable        上下文环境
     * @param doBeforeUpdate  执行保存之前的回调
     * @param minUpdatePeriod 最小保存的间隔(秒)
     * @param prevSaveTs      上一次保存的时间戳(毫秒)
     * @return 最近一次保存的ts
     */
    default long updateTaskPeriodInCurrentThread(TaskRunnable<T, K> runnable, Consumer<T> doBeforeUpdate, int minUpdatePeriod, long prevSaveTs) {
        final long curTs = DateUtil.CacheMillisecond.current();
        if (prevSaveTs == 0) {
            return curTs;
        }
        if ((curTs - prevSaveTs) > minUpdatePeriod * 1000L) {
            final T task = runnable.task;
            doBeforeUpdate.accept(task);
            runnable.taskDao.doUpdate(task);
            return curTs;
        } else {
            return prevSaveTs;
        }
    }
}
