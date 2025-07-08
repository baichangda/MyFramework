package cn.bcd.server.business.process.backend.base.support_task;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.ThreadPoolExecutor;

public class TaskRunnable<T extends Task<K>, K extends Serializable> implements Runnable, Serializable {
    private final static Logger logger = LoggerFactory.getLogger(TaskRunnable.class);

    @Serial
    private final static long serialVersionUID = 1L;

    public final T task;
    public final Object[] params;
    public volatile boolean stop = false;

    public final TaskFunction<T, K> function;
    public final ThreadPoolExecutor executor;
    public final TaskBuilder<T, K> taskBuilder;
    public final TaskDao<T, K> taskDao;

    public TaskRunnable(T task, TaskFunction<T, K> function, Object[] params, TaskBuilder<T, K> taskBuilder) {
        this.task = task;
        this.function = function;
        this.params = params;
        this.taskBuilder = taskBuilder;
        //分配线程
        this.executor = taskBuilder.executorChooser.next();
        this.taskDao = taskBuilder.taskDao;
    }

    StopResult stop() {
        boolean removed = executor.remove(this);
        if (removed) {
            //取消成功
            executor.execute(() -> {
                taskBuilder.onCanceled(task);
            });
            return StopResult.CANCEL_SUCCEED;
        } else {
            //正在执行中、尝试停止
            stop = true;
            return StopResult.IN_EXECUTING_INTERRUPT_SUCCEED;
        }
    }

    @Override
    public void run() {
        //触发开始方法
        taskBuilder.onStarted(task);
        try {
            //执行任务
            boolean done = function.execute(this);
            if (stop && !done) {
                taskBuilder.onStopped(task);
            } else {
                taskBuilder.onSucceed(task);
            }
        } catch (Exception ex) {
            logger.error("execute task failed id[{}] function[{}]", task.getId(), function.getClass().getName(), ex);
            taskBuilder.onFailed(task, ex);
        } finally {
            //最后从当前服务器任务id和结果映射结果集中移除
            taskBuilder.taskIdToRunnable.remove(task.getId().toString());
        }
    }
}
