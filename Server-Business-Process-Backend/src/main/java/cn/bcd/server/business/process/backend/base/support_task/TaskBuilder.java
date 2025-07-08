package cn.bcd.server.business.process.backend.base.support_task;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskBuilder<T extends Task<K>, K extends Serializable> {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final TaskDao<T, K> taskDao;

    //线程属性
    protected final int poolSize;
    protected ThreadPoolExecutor[] pools;
    protected ExecutorChooser executorChooser;

    //任务id和任务对应
    protected final ConcurrentHashMap<String, TaskRunnable<T, K>> taskIdToRunnable = new ConcurrentHashMap<>();

    protected TaskBuilder(TaskDao<T, K> taskDao, int poolSize) {
        this.taskDao = taskDao;
        this.poolSize = poolSize;
    }

    public static <T extends Task<K>, K extends Serializable> TaskBuilder<T, K> newInstance(TaskDao<T, K> taskDao, int poolSize) {
        return new TaskBuilder<>(taskDao, poolSize);
    }

    public synchronized void init() {
        //初始化线程池
        pools = new ThreadPoolExecutor[poolSize];
        {
            for (int i = 0; i < poolSize; i++) {
                pools[i] = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            }
        }
        this.executorChooser = ExecutorChooser.getChooser(pools);
    }


    public synchronized void destroy() {
        if (pools != null) {
            for (ThreadPoolExecutor pool : pools) {
                pool.shutdown();
                try {
                    while (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    }
                } catch (InterruptedException ex) {
                    throw BaseException.get(ex);
                }
            }
        }
    }

    public K register(T task, TaskFunction<T, K> function, Object... params) {
        final T t = onCreated(task);
        //初始化
        TaskRunnable<T, K> runnable = new TaskRunnable<>(task, function, params, this);
        taskIdToRunnable.put(task.getId().toString(), runnable);
        runnable.executor.execute(runnable);
        return t.getId();

    }

    public StopResult[] stop(K... ids) {
        StopResult[] stopResults = new StopResult[ids.length];
        if (ids.length > 0) {
            for (int i = 0; i < ids.length; i++) {
                TaskRunnable<T, K> runnable = taskIdToRunnable.get(ids[i].toString());
                if (runnable == null) {
                    //此时找不到任务
                    stopResults[i] = StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND;
                } else {
                    logger.info("stop[{},{}]", ids[i], runnable.executor);
                    stopResults[i] = runnable.stop();
                }
            }
        }
        return stopResults;
    }

    protected T onCreated(T task) {
        try {
            task.setStatus(TaskStatus.WAITING.getStatus());
            task.onCreated();
        } catch (Exception e) {
            logger.error("task[{}] execute onCreate error", task.getId(), e);
        }
        return taskDao.doCreate(task);
    }

    protected void onStarted(T task) {
        try {
            task.setStatus(TaskStatus.EXECUTING.getStatus());
            task.onStarted();
            taskDao.doUpdate(task);
        } catch (Exception e) {
            logger.error("task[{}] execute onStart error", task.getId(), e);
        }
    }

    protected void onSucceed(T task) {
        try {
            task.setStatus(TaskStatus.SUCCEED.getStatus());
            task.onSucceed();
            taskDao.doUpdate(task);
        } catch (Exception e) {
            logger.error("task[{}] execute onSucceed error", task.getId(), e);
        }
    }

    protected void onFailed(T task, Exception ex) {
        try {
            task.setStatus(TaskStatus.FAILED.getStatus());
            task.onFailed(ex);
            taskDao.doUpdate(task);
        } catch (Exception e) {
            logger.error("task[{}] execute onFailed error", task.getId(), e);
        }
    }

    protected void onCanceled(T task) {
        try {
            task.setStatus(TaskStatus.CANCELED.getStatus());
            task.onCanceled();
            taskDao.doUpdate(task);
        } catch (Exception e) {
            logger.error("task[{}] execute onCanceled error", task.getId(), e);
        }
    }

    protected void onStopped(T task) {
        try {
            task.setStatus(TaskStatus.STOPPED.getStatus());
            task.onStopped();
            taskDao.doUpdate(task);
        } catch (Exception e) {
            logger.error("task[{}] execute onStop error", task.getId(), e);
        }
    }
}
