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

    private final static HashMap<String, TaskBuilder> storage = new HashMap<>();

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final TaskDao<T, K> taskDao;

    protected final String name;

    //线程属性
    protected final int poolSize;
    protected ThreadPoolExecutor[] pools;
    protected ExecutorChooser executorChooser;

    //任务id和任务对应
    protected final ConcurrentHashMap<String, TaskRunnable<T, K>> taskIdToRunnable = new ConcurrentHashMap<>();

    protected TaskBuilder(String name, TaskDao<T, K> taskDao, int poolSize) {
        this.name = name;
        this.taskDao = taskDao;
        this.poolSize = poolSize;
    }

    public static <T extends Task<K>, K extends Serializable> TaskBuilder<T, K> from(String name) {
        return storage.get(name);
    }

    public static <T extends Task<K>, K extends Serializable> TaskBuilder<T, K> newInstance(String name, TaskDao<T, K> taskDao, int poolSize) {
        return new TaskBuilder<>(name, taskDao, poolSize);
    }

    public void init() {
        //检查名称
        synchronized (storage) {
            if (storage.containsKey(name)) {
                throw BaseException.get("TaskBuilder[{}] [{}] exist", name, storage.get(name));
            } else {
                storage.put(name, this);
            }
        }

        //初始化线程池
        pools = new ThreadPoolExecutor[poolSize];
        {
            for (int i = 0; i < poolSize; i++) {
                pools[i] = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            }
        }
        this.executorChooser = ExecutorChooser.getChooser(pools);
    }


    public void destroy() {
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

    public K registerTask(T task, TaskFunction<T, K> function, Object... params) {
        final T t = onCreated(task);
        //初始化
        TaskRunnable<T, K> runnable = new TaskRunnable<>(task, function, params, this);
        runnable.init();
        taskIdToRunnable.put(task.getId().toString(), runnable);
        runnable.getExecutor().execute(runnable);
        return t.getId();

    }

    public StopResult[] stopTask(K... ids) {
        StopResult[] stopResults = new StopResult[ids.length];
        if (ids.length > 0) {
            for (int i = 0; i < ids.length; i++) {
                TaskRunnable<T, K> runnable = taskIdToRunnable.get(ids[i].toString());
                if (runnable == null) {
                    //此时找不到任务
                    stopResults[i] = StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND;
                } else {
                    logger.info("stop[{},{}]", ids[i], runnable.getExecutor());
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

    protected T onStarted(T task) {
        try {
            task.setStatus(TaskStatus.EXECUTING.getStatus());
            task.onStarted();
            taskDao.doUpdate(task);
            return task;
        } catch (Exception e) {
            logger.error("task[{}] execute onStart error", task.getId(), e);
            return task;
        }
    }

    protected T onSucceed(T task) {
        try {
            task.setStatus(TaskStatus.SUCCEED.getStatus());
            task.onSucceed();
            taskDao.doUpdate(task);
            return task;
        } catch (Exception e) {
            logger.error("task[{}] execute onSucceed error", task.getId(), e);
            return task;
        }
    }

    protected T onFailed(T task, Exception ex) {
        try {
            task.setStatus(TaskStatus.FAILED.getStatus());
            task.onFailed(ex);
            taskDao.doUpdate(task);
            return task;
        } catch (Exception e) {
            logger.error("task[{}] execute onFailed error", task.getId(), e);
            return task;
        }
    }

    protected T onCanceled(T task) {
        try {
            task.setStatus(TaskStatus.CANCELED.getStatus());
            task.onCanceled();
            taskDao.doUpdate(task);
            return task;
        } catch (Exception e) {
            logger.error("task[{}] execute onCanceled error", task.getId(), e);
            return task;
        }
    }

    protected T onStopped(T task) {
        try {
            task.setStatus(TaskStatus.STOPPED.getStatus());
            task.onStopped();
            taskDao.doUpdate(task);
            return task;
        } catch (Exception e) {
            logger.error("task[{}] execute onStop error", task.getId(), e);
            return task;
        }
    }

    public TaskDao<T, K> getTaskDao() {
        return taskDao;
    }

    public String getName() {
        return name;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public ThreadPoolExecutor[] getPools() {
        return pools;
    }

    public ExecutorChooser getExecutorChooser() {
        return executorChooser;
    }

    public ConcurrentHashMap<String, TaskRunnable<T, K>> getTaskIdToRunnable() {
        return taskIdToRunnable;
    }
}
