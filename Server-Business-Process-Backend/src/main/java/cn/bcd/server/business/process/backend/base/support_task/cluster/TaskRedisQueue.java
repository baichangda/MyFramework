package cn.bcd.server.business.process.backend.base.support_task.cluster;

import cn.bcd.lib.base.redis.RedisUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.server.business.process.backend.base.support_task.Task;
import cn.bcd.server.business.process.backend.base.support_task.TaskBuilder;
import cn.bcd.server.business.process.backend.base.support_task.TaskRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class TaskRedisQueue<T extends Task<K>, K extends Serializable> {

    static Logger logger = LoggerFactory.getLogger(TaskRedisQueue.class);
    private final String name;
    private final String queueName;

    private final TaskBuilder<T, K> taskBuilder;

    private final Semaphore semaphore;

    private final BoundListOperations<String, TaskRunnable<T, K>> boundListOperations;

    private volatile boolean stop;

    /**
     * 从redis中遍历数据的线程池
     */
    private final ExecutorService fetchPool = Executors.newSingleThreadExecutor();

    /**
     * 执行工作任务的线程池
     */
    private final ExecutorService workPool = Executors.newCachedThreadPool();

    public TaskRedisQueue(String name, RedisConnectionFactory connectionFactory, TaskBuilder<T, K> taskBuilder) {
        final RedisTemplate<String, TaskRunnable<T, K>> redisTemplate = RedisUtil.newRedisTemplate_string_serializable(connectionFactory);
        this.name = name;
        this.queueName = "sysTask:" + name;
        this.boundListOperations = redisTemplate.boundListOps(this.queueName);
        this.taskBuilder = taskBuilder;
        this.semaphore = new Semaphore(this.taskBuilder.getPoolSize());
    }


    /**
     * 从redis list中获取任务并执行
     *
     * @throws InterruptedException
     */
    private void fetchAndExecute() throws InterruptedException {
        semaphore.acquire();
        try {
            Object data = boundListOperations.rightPop(30, TimeUnit.SECONDS);
            if (data == null) {
                semaphore.release();
            } else {
                workPool.execute(() -> {
                    try {
                        onTask((TaskRunnable<T, K>) data);
                    } catch (Exception e) {
                        logger.error("onTask error", e);
                        semaphore.release();
                    }
                });
            }
        } catch (Exception ex) {
            semaphore.release();
            if (ex instanceof QueryTimeoutException) {
                logger.error("SysTaskRedisQueue[{}] fetchAndExecute QueryTimeoutException", name, ex);
            } else {
                logger.error("SysTaskRedisQueue[{}] fetchAndExecute error,try after 10s", name, ex);
                Thread.sleep(10000L);
            }
        }
    }

    /**
     * 接收到任务处理
     *
     * @param runnable
     */
    public void onTask(TaskRunnable<T, K> runnable) {
        //初始化环境
        runnable.init();
        taskBuilder.getTaskIdToRunnable().put(runnable.getTask().getId().toString(), runnable);
        runnable.getExecutor().execute(() -> {
            //使用线程池执行任务
            try {
                //3.1、执行任务
                runnable.run();
            } finally {
                //3.2、执行完毕后释放锁
                semaphore.release();
            }
        });
    }

    public void send(TaskRunnable<T, K> runnable) {
        boundListOperations.leftPush(runnable);
    }

    public boolean[] remove(K... ids) {
        if (ids == null || ids.length == 0) {
            return new boolean[0];
        }
        boolean[] res = new boolean[ids.length];
        List<TaskRunnable<T, K>> runnableList = boundListOperations.range(0L, -1L);
        if (runnableList != null) {
            for (int i = 0; i < ids.length; i++) {
                K id = ids[i];
                for (TaskRunnable<T, K> runnable : runnableList) {
                    if (id.equals(runnable.getTask().getId())) {
                        Long count = boundListOperations.remove(1, runnable);
                        if (count != null && count == 1) {
                            res[i] = true;
                            break;
                        } else {
                            res[i] = false;
                            break;
                        }
                    }
                }
            }
        }
        return res;
    }


    public void init() {
        stop = false;
        fetchPool.execute(() -> {
            while (!stop) {
                try {
                    fetchAndExecute();
                } catch (InterruptedException ex) {
                    //处理打断情况,此时退出
                    logger.error("SysTaskRedisQueue[{}] interrupted,exit...", name, ex);
                    break;
                }
            }
        });
    }

    public void destroy() {
        stop = true;
        ExecutorUtil.shutdownThenAwait(fetchPool, workPool);
    }


}
