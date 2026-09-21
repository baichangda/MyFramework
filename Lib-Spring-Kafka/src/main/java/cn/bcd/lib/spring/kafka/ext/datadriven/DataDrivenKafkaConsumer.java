package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.*;
import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import cn.bcd.lib.spring.kafka.ext.KafkaExtUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 此类要求提供 kafka-client即可、不依赖spring-kafka
 * 数据驱动模型
 * 即消费到数据后、每一个数据将会根据{@link #id(ConsumerRecord)}、{@link #getWorkExecutor(String)}分配到固定的{@link WorkExecutor}
 * 同时会通过{@link #newHandler(String, ConsumerRecord)}构造数据对象
 * 后续{@link WorkHandler}中所有的操作都会由分配的{@link WorkExecutor}来执行
 * 这样做的好处是能保证{@link WorkHandler}所有方法都是线程安全的
 * <p>
 * 此类会产生如下线程
 * <p>
 * 消费者线程可能有多个、开头为 {name}-consumer
 * 例如test-consumer(1/3)-partition(0)
 * consumer(1/3)代表有3个消费线程、这是第一个
 * partition(0)代表这个消费线程消费哪个分区
 * <p>
 * 工作任务执行器线程可能有多个、和工作任务执行器数量有关、开头为 {name}-worker
 * 例如test-worker(1/3)
 * worker(1/3)代表有3个工作线程、这是第一个
 * <p>
 * 监控信息线程只有一个、开头为 {name}-monitor
 * 需要开启{@link #monitor_period}才会有
 * 例如test-monitor
 * <p>
 * 工作任务执行器支持计划任务、计划任务由对应的工作线程执行、不额外创建线程
 * <p>
 * 限速重置消费计数线程只有一个、开头为 {name}-reset
 * 需要开启{@link #maxConsumeSpeed}才会有
 * 例如test-reset
 * <p>
 * 定时扫描过期workHandler线程只有一个、开头为 {name}-scanner
 * 需要开启{@link #workHandlerScanner}才会有
 * 例如test-scanner
 * <p>
 */
public abstract class DataDrivenKafkaConsumer implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    public final String name;
    public final int workExecutorNum;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final WorkHandlerScanner workHandlerScanner;
    public final int monitor_period;
    public final ConsumerParam consumerParam;
    /**
     * 当前阻塞数量
     */
    public final LongAdder blockingNum = new LongAdder();

    /**
     * 消费线程
     */
    public KafkaExtUtil.ConsumerThreadHolder consumerThreadHolder;


    /**
     * 工作执行器数组
     */
    public final WorkExecutor[] workExecutors;


    /**
     * 重置消费计数
     */
    public final AtomicInteger consumeCount;
    public final ScheduledExecutorService resetConsumeCountPool;

    /**
     * 扫描过期线程池
     */
    public final ScheduledExecutorService scannerPool;

    /**
     * 监控信息
     */
    public final LongAdder monitor_workHandlerCount;
    public final LongAdder monitor_consumeCount;
    //处理数据数量(无论是否发生异常)
    public final LongAdder monitor_workCount;
    public final ScheduledExecutorService monitor_pool;

    /**
     * 是否关闭
     */
    boolean closed;

    /**
     * 控制退出线程标志
     */
    volatile boolean running_consume;

    /**
     * 是否暂停消费
     */
    volatile boolean pause_consume = false;


    public static class WorkHandlerScanner {
        public final int periodInSecond;
        public final int expiredInSecond;

        private WorkHandlerScanner(int periodInSecond, int expiredInSecond) {
            this.periodInSecond = periodInSecond;
            this.expiredInSecond = expiredInSecond;
        }

        /**
         * @param periodInSecond  定时任务扫描周期(秒)
         * @param expiredInSecond 判断workHandler过期的时间(秒)
         */
        public static WorkHandlerScanner get(int periodInSecond, int expiredInSecond) {
            return new WorkHandlerScanner(periodInSecond, expiredInSecond);
        }

    }

    /**
     * @param name                当前消费者的名称(用于标定线程名称)
     * @param workExecutorNum     工作任务执行器个数、最好是2的倍数、如果不是向上取整到2的倍数
     * @param maxBlockingNum      最大阻塞数量(0代表不限制)、当内存中达到最大阻塞数量时候、消费者会停止消费
     *                            当不限制时候、还是会记录{@link #blockingNum}、便于监控阻塞数量
     * @param autoReleaseBlocking 是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     * @param maxConsumeSpeed     最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     *                            每消费一次的数据量大小取决于如下消费者参数
     *                            {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} 一次poll消费最大数据量
     *                            {@link ConsumerConfig#MAX_PARTITION_FETCH_BYTES_CONFIG} 每个分区最大拉取字节数
     * @param workHandlerScanner  定时扫描并销毁过期的{@link WorkHandler}、销毁时候会执行其{@link WorkHandler#destroy()}方法、由对应的工作任务执行器执行
     *                            null则代表不启动扫描
     * @param monitor_period      监控信息打印周期(秒)、0则代表不打印
     * @param consumerParam       消费者的参数、不能为null
     *                            主要用于设置消费的topic、分区、消费线程、消费者开始消费的位置
     *                            具体参考{@link ConsumerParam}中静态方法
     */
    public DataDrivenKafkaConsumer(String name,
                                   int workExecutorNum,
                                   int maxBlockingNum,
                                   boolean autoReleaseBlocking,
                                   int maxConsumeSpeed,
                                   WorkHandlerScanner workHandlerScanner,
                                   int monitor_period,
                                   ConsumerParam consumerParam) {
        this.name = name;
        this.workExecutorNum = tableSizeFor(workExecutorNum);
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.workHandlerScanner = workHandlerScanner;
        this.monitor_period = monitor_period;
        this.consumerParam = consumerParam;

        try {
            //初始化重置消费计数线程池(如果有限制最大消费速度)、提交工作任务、每秒重置消费数量
            if (maxConsumeSpeed == 0) {
                consumeCount = null;
                resetConsumeCountPool = null;
            } else {
                consumeCount = new AtomicInteger();
                resetConsumeCountPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-reset"));
                resetConsumeCountPool.scheduleAtFixedRate(() -> {
                    consumeCount.set(0);
                }, 1, 1, TimeUnit.SECONDS);
            }
            //启动监控
            if (monitor_period == 0) {
                monitor_workHandlerCount = null;
                monitor_consumeCount = null;
                monitor_workCount = null;
                monitor_pool = null;
            } else {
                monitor_workHandlerCount = new LongAdder();
                monitor_consumeCount = new LongAdder();
                monitor_workCount = new LongAdder();
                monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-monitor"));
                monitor_pool.scheduleAtFixedRate(() -> logger.info(monitor_log()), monitor_period, monitor_period, TimeUnit.SECONDS);
            }

            //启动扫描过期数据
            if (workHandlerScanner == null) {
                scannerPool = null;
            } else {
                scannerPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-scanner"));
                scannerPool.scheduleAtFixedRate(() -> scanAndDestroyWorkHandler(workHandlerScanner.expiredInSecond), workHandlerScanner.periodInSecond, workHandlerScanner.periodInSecond, TimeUnit.SECONDS);
            }

            //启动任务执行器
            this.workExecutors = new WorkExecutor[this.workExecutorNum];
            for (int i = 0; i < this.workExecutorNum; i++) {
                String workThreadName = name + "-worker(" + (i + 1) + "/" + this.workExecutorNum + ")";
                this.workExecutors[i] = new WorkExecutor(r -> new Thread(r, workThreadName));
            }

        } catch (Exception ex) {
            close();
            throw BaseException.get(ex);
        }
    }

    private static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }

    /**
     * 开始消费
     */
    public final synchronized void startConsume(Map<String, Object> consumerProp) {
        if (closed) {
            throw new IllegalStateException("consumer already closed");
        }
        if (!running_consume) {
            running_consume = true;
            try {
                consumerThreadHolder = KafkaExtUtil.startConsumer(name, consumerProp, consumerParam, this::consume);
                consumerThreadHolder.start();
            } catch (RuntimeException ex) {
                running_consume = false;
                throw ex;
            }
        }
    }

    /**
     * 根据消费的数据获取其id
     * 可以由子类重写
     *
     * @param consumerRecord
     * @return
     */
    protected String id(ConsumerRecord<String, byte[]> consumerRecord) {
        return consumerRecord.key();
    }

    /**
     * 根据id分配到对应的工作执行者上
     *
     * @param id
     * @return
     */
    public WorkExecutor getWorkExecutor(String id) {
        if (id == null) {
            return workExecutors[0];
        }
        int h = id.hashCode();
        h = h ^ (h >>> 16);
        return workExecutors[h & (workExecutorNum - 1)];
    }

    /**
     * 根据id构造workHandler
     *
     * @param id
     * @return
     */
    public abstract WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first);

    /**
     * 移除workHandler
     *
     * @param id
     * @return
     */
    public final Future<?> removeHandler(String id) {
        WorkExecutor workExecutor = getWorkExecutor(id);
        return removeHandler(id, workExecutor);
    }

    private Future<?> removeHandler(String id, WorkExecutor executor) {
        return executor.submit(() -> {
            WorkHandler workHandler = executor.workHandlers.remove(id);
            if (workHandler != null) {
                try {
                    workHandler.destroy();
                } catch (Exception ex) {
                    logger.error("workHandler destroy error id[{}]", workHandler.id, ex);
                }
                if (monitor_period > 0) {
                    monitor_workHandlerCount.decrement();
                }
            }
        });
    }

    /**
     * 暂停消费
     */
    public final void pauseConsume() {
        pause_consume = true;
    }

    /**
     * 恢复消费
     */
    public final void resumeConsume() {
        pause_consume = false;
    }

    /**
     * 根据id获取对应WorkHandler
     *
     * @param id
     * @return
     */
    @SuppressWarnings("unchecked")
    public final <V extends WorkHandler> V getHandler(String id) {
        WorkExecutor workExecutor = getWorkExecutor(id);
        try {
            return (V) workExecutor.submit(() -> workExecutor.workHandlers.get(id)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw BaseException.get(e);
        }
    }


    @Override
    public void close() {
        boolean async;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            async = isCurrentWorkExecutorThread();
        }
        if (async) {
            new Thread(this::closeInternal, name + "-shutdown").start();
        } else {
            closeInternal();
        }
    }

    private void closeInternal() {
        //打上退出标记、等待消费线程退出
        running_consume = false;
        if (consumerThreadHolder != null) {
            ExecutorUtil.shutdownThenAwait(true, consumerThreadHolder.thread(), consumerThreadHolder.threads());
        }
        ExecutorUtil.shutdownThenAwait(true, resetConsumeCountPool);
        //等待工作执行器退出
        if (workExecutors != null) {
            for (WorkExecutor workExecutor : workExecutors) {
                //添加删除任务
                try {
                    workExecutor.submit(() -> {
                        for (String id : workExecutor.workHandlers.keySet()) {
                            removeHandler(id);
                        }
                    }).get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("error", e);
                }
                //关闭线程池
                workExecutor.shutdownGracefully();
            }
            for (WorkExecutor workExecutor : workExecutors) {
                //等待工作执行器退出
                ExecutorUtil.await(workExecutor);
            }
        }
        //取消监控、扫描过期线程
        ExecutorUtil.shutdownAllThenAwait(false, monitor_pool, scannerPool);
    }

    private boolean isCurrentWorkExecutorThread() {
        if (workExecutors == null) {
            return false;
        }
        for (WorkExecutor workExecutor : workExecutors) {
            if (workExecutor != null && workExecutor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }


    /**
     * 消费
     */
    public void consume(KafkaConsumer<String, byte[]> consumer) {
        try {
            boolean paused = false;
            while (running_consume) {
                try {
                    boolean throttled = pause_consume
                            || (maxBlockingNum > 0 && blockingNum.sum() >= maxBlockingNum)
                            || (maxConsumeSpeed > 0 && consumeCount.get() >= maxConsumeSpeed);
                    Duration pollDuration;
                    if (throttled) {
                        Set<org.apache.kafka.common.TopicPartition> assignment = consumer.assignment();
                        if (!assignment.isEmpty()) {
                            consumer.pause(assignment);
                            paused = true;
                        }
                        // pause() only stops fetching; poll must continue to keep group membership alive.
                        pollDuration = Duration.ofMillis(100);
                    } else {
                        if (paused) {
                            consumer.resume(consumer.assignment());
                            paused = false;
                        }
                        pollDuration = Duration.ofSeconds(1);
                    }

                    ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(pollDuration);
                    if (consumerRecords.isEmpty()) {
                        continue;
                    }
                    if (maxConsumeSpeed > 0) {
                        consumeCount.addAndGet(consumerRecords.count());
                    }
                    for (ConsumerRecord<String, byte[]> consumerRecord : consumerRecords) {
                        final String id = id(consumerRecord);
                        WorkExecutor workExecutor = getWorkExecutor(id);
                        blockingNum.increment();
                        if (monitor_period > 0) {
                            monitor_consumeCount.increment();
                        }
                        try {
                            workExecutor.execute(() -> {
                                WorkHandler workHandler = workExecutor.workHandlers.computeIfAbsent(id, k -> {
                                    try {
                                        WorkHandler temp = newHandler(id, consumerRecord);
                                        temp.afterConstruct(workExecutor, this);
                                        temp.init(consumerRecord);
                                        if (monitor_period > 0) {
                                            monitor_workHandlerCount.increment();
                                        }
                                        return temp;
                                    } catch (Exception ex) {
                                        blockingNum.decrement();
                                        logger.error("workHandler init error id[{}]", id, ex);
                                        return null;
                                    }
                                });
                                if (workHandler != null) {
                                    workHandler.lastMessageTime = DateUtil.CacheSecond.current();
                                    try {
                                        workHandler.onMessage(consumerRecord);
                                    } catch (Exception ex) {
                                        logger.error("workHandler onMessage error id[{}]", id, ex);
                                    }
                                    if (autoReleaseBlocking) {
                                        blockingNum.decrement();
                                    }
                                    if (monitor_period > 0) {
                                        monitor_workCount.increment();
                                    }
                                }
                            });
                        } catch (RuntimeException ex) {
                            blockingNum.decrement();
                            throw ex;
                        }
                    }
                } catch (Exception ex) {
                    if (!running_consume) {
                        break;
                    }
                    logger.error("kafka consumer cycle error,try again after 3s", ex);
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            String assignment = "";
            try {
                assignment = consumer.assignment().stream().map(e -> e.topic() + ":" + e.partition()).collect(Collectors.joining(","));
            } catch (Exception ex) {
                logger.debug("get consumer assignment before close error", ex);
            }
            try {
                consumer.close();
            } finally {
                logger.info("consumer[{}] assignment[{}] close", this.getClass().getName(), assignment);
            }
        }

    }

    /**
     * 监控日志
     * 如果需要修改日志、可以重写此方法
     * <p>
     * workExecutor 任务执行器个数
     * workHandler 任务处理器个数
     * blocking 当前阻塞数量/最大阻塞数量
     * consumeSpeed 每秒消费速度
     * workQueueTaskNum 消费任务和工作任务之间所有执行器的队列中任务总和
     * queues 所有执行器的队列情况、每个执行器 当前队列大小/最大队列大小(如果有最大队列大小)
     * workSpeed 每秒工作速度
     */
    public String monitor_log() {
        int workExecutorCount = workExecutors.length;
        long workHandlerCount = monitor_workHandlerCount.sum();
        long curBlockingNum = blockingNum.sum();
        double consumeSpeed = FloatUtil.round(monitor_consumeCount.sumThenReset() / ((double) monitor_period), 2);
        String workQueueStatus = Arrays.stream(workExecutors).map(e -> e.pendingTasks() + "").collect(Collectors.joining(" "));
        double workSpeed = FloatUtil.round(monitor_workCount.sumThenReset() / ((double) monitor_period), 2);
        return StringUtil.format("name[{}] " +
                        "workExecutor[{}] " +
                        "workHandler[{}] " +
                        "blocking[{}/{}] " +
                        "consumeSpeed[{}/s] " +
                        "queues[{}] " +
                        "workSpeed[{}/s]",
                name,
                workExecutorCount,
                workHandlerCount,
                curBlockingNum, maxBlockingNum,
                consumeSpeed,
                workQueueStatus,
                workSpeed);
    }

    /**
     * 扫描并销毁过期的workHandler
     *
     * @param expiredInSecond 过期时间
     */
    public final void scanAndDestroyWorkHandler(int expiredInSecond) {
        long ts = DateUtil.CacheSecond.current() - expiredInSecond;
        for (WorkExecutor workExecutor : workExecutors) {
            workExecutor.execute(() -> {
                List<String> ids = new ArrayList<>();
                for (WorkHandler workHandler : workExecutor.workHandlers.values()) {
                    if (workHandler.lastMessageTime < ts) {
                        ids.add(workHandler.id);
                    }
                }
                for (String id : ids) {
                    removeHandler(id, workExecutor);
                }
            });
        }
    }
}

