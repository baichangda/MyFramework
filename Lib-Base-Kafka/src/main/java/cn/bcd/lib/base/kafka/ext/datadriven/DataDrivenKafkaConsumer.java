package cn.bcd.lib.base.kafka.ext.datadriven;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.kafka.ext.KafkaExtUtil;
import cn.bcd.lib.base.kafka.ext.PartitionMode;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.base.util.FloatUtil;
import cn.bcd.lib.base.util.StringUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 此类要求提供 kafka-client即可、不依赖spring-kafka
 * 数据驱动模型
 * 即消费到数据后、每一个数据将会根据{@link #id(ConsumerRecord)}、{@link #getWorkExecutor(String)}分配到固定的{@link WorkExecutor}
 * 同时会通过{@link #newHandler(String)}构造数据对象
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
 * 工作任务执行器中的计划任务线程可能有多个、和工作任务执行器数量有关、开头为 {name}-worker、以 -schedule 结尾
 * 需要开启{@link #workExecutorSchedule}才会有
 * 例如test-worker(1/3)-schedule
 * 其中test-worker(1/3)即工作线程名称、接后缀 -schedule
 * <p>
 * 工作任务执行器中的阻塞检查线程可能有多个、和工作任务执行器数量有关、开头为 {name}-worker、以 -blockingChecker 结尾
 * 需要开启{@link #workExecutorBlockingChecker}才会有
 * 例如test-worker(1/3)-blockingChecker
 * 其中test-worker(1/3)即工作线程名称、接后缀 -blockingChecker
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
public abstract class DataDrivenKafkaConsumer {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    public final String name;
    public final int workExecutorNum;
    public final boolean workExecutorSchedule;
    public final BlockingChecker workExecutorBlockingChecker;
    public final int maxBlockingNum;
    public final boolean autoReleaseBlocking;
    public final int maxConsumeSpeed;
    public final WorkHandlerScanner workHandlerScanner;
    public final int monitor_period;
    public final String topic;
    public final PartitionMode partitionMode;
    /**
     * 当前阻塞数量
     */
    public final LongAdder blockingNum = new LongAdder();

    /**
     * 消费线程
     */
    public Thread consumeThread;
    public Thread[] consumeThreads;


    /**
     * 工作执行器数组
     */
    public WorkExecutor[] workExecutors;


    /**
     * 重置消费计数
     */
    public AtomicInteger consumeCount;
    public ScheduledExecutorService resetConsumeCountPool;

    /**
     * 扫描过期线程池
     */
    public ScheduledExecutorService scannerPool;

    /**
     * 监控信息
     */
    public LongAdder monitor_workHandlerCount;
    public LongAdder monitor_consumeCount;
    //处理数据数量(无论是否发生异常)
    public LongAdder monitor_workCount;
    public ScheduledExecutorService monitor_pool;

    /**
     * 是否运行中
     */
    volatile boolean running;

    /**
     * 控制退出线程标志
     */
    volatile boolean running_consume = true;

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
     * @param name                        当前消费者的名称(用于标定线程名称)
     * @param workExecutorNum             工作任务执行器个数
     * @param workExecutorSchedule        工作任务执是否开启计划任务
     *                                    开启后会启动一个计划线程池用于接收计划任务
     * @param workExecutorBlockingChecker 工作任务执行器阻塞检查参数
     *                                    null代表不启动阻塞检查
     *                                    否则会启动阻塞检查、每一个执行器会启动一个周期任务线程池、周期进行检查操作
     *                                    检查逻辑为
     *                                    向执行器中写入一个空任务、然后等待{@link BlockingChecker#maxBlockingTimeInSecond}后、检查任务是否完成
     *                                    如果未完成、则告警并每{@link BlockingChecker#periodWhenBlockingInSecond}秒执行一次检查、直到完成
     * @param maxBlockingNum              最大阻塞数量(0代表不限制)、当内存中达到最大阻塞数量时候、消费者会停止消费
     *                                    当不限制时候、还是会记录{@link #blockingNum}、便于监控阻塞数量
     * @param autoReleaseBlocking         是否自动释放阻塞、适用于工作内容为同步处理的逻辑
     * @param maxConsumeSpeed             最大消费速度每秒(0代表不限制)、kafka一次消费一批数据、设置过小会导致不起作用、此时会每秒处理一批数据
     *                                    每消费一次的数据量大小取决于如下消费者参数
     *                                    {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} 一次poll消费最大数据量
     *                                    {@link ConsumerConfig#MAX_PARTITION_FETCH_BYTES_CONFIG} 每个分区最大拉取字节数
     * @param workHandlerScanner          定时扫描并销毁过期的{@link WorkHandler}、销毁时候会执行其{@link WorkHandler#destroy()}方法、由对应的工作任务执行器执行
     *                                    null则代表不启动扫描
     * @param monitor_period              监控信息打印周期(秒)、0则代表不打印
     * @param topic                       消费的topic
     * @param partitionMode               null则代表PartitionMode.get(0)、即启动单线程、一个消费者、使用{@link KafkaConsumer#subscribe(Pattern)}完成订阅这个topic的所有分区\
     *                                    其他情况参考{@link PartitionMode#mode}
     */
    public DataDrivenKafkaConsumer(String name,
                                   int workExecutorNum,
                                   boolean workExecutorSchedule,
                                   BlockingChecker workExecutorBlockingChecker,
                                   int maxBlockingNum,
                                   boolean autoReleaseBlocking,
                                   int maxConsumeSpeed,
                                   WorkHandlerScanner workHandlerScanner,
                                   int monitor_period,
                                   String topic,
                                   PartitionMode partitionMode) {
        this.name = name;
        this.workExecutorNum = workExecutorNum;
        this.workExecutorSchedule = workExecutorSchedule;
        this.workExecutorBlockingChecker = workExecutorBlockingChecker;
        this.maxBlockingNum = maxBlockingNum;
        this.autoReleaseBlocking = autoReleaseBlocking;
        this.maxConsumeSpeed = maxConsumeSpeed;
        this.workHandlerScanner = workHandlerScanner;
        this.monitor_period = monitor_period;
        this.topic = topic;
        if (partitionMode == null) {
            this.partitionMode = PartitionMode.get(0);
        } else {
            this.partitionMode = partitionMode;
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
        int index = Math.floorMod(id.hashCode(), workExecutorNum);
        return workExecutors[index];
    }

    /**
     * 根据id构造workHandler
     *
     * @param id
     * @return
     */
    public abstract WorkHandler newHandler(String id,byte[] first);

    /**
     * 移除workHandler
     *
     * @param id
     * @return
     */
    public final CompletableFuture<Void> removeHandler(String id) {
        WorkExecutor workExecutor = getWorkExecutor(id);
        return removeHandler(id, workExecutor);
    }

    public final CompletableFuture<Void> removeHandler(String id, WorkExecutor executor) {
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


    /**
     * 初始化
     * 构造线程池
     * 启动线程
     * 注册销毁狗子
     *
     * @param consumerProp
     */
    public synchronized void init(Map<String, Object> consumerProp) {
        if (!running) {
            try {
                //标记可用
                running = true;
                running_consume = true;
                //初始化重置消费计数线程池(如果有限制最大消费速度)、提交工作任务、每秒重置消费数量
                if (maxConsumeSpeed > 0) {
                    consumeCount = new AtomicInteger();
                    resetConsumeCountPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-reset"));
                    resetConsumeCountPool.scheduleAtFixedRate(() -> {
                        consumeCount.set(0);
                    }, 1, 1, TimeUnit.SECONDS);
                }
                //启动监控
                if (monitor_period != 0) {
                    monitor_workHandlerCount = new LongAdder();
                    monitor_consumeCount = new LongAdder();
                    monitor_workCount = new LongAdder();
                    monitor_pool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-monitor"));
                    monitor_pool.scheduleAtFixedRate(() -> logger.info(monitor_log()), monitor_period, monitor_period, TimeUnit.SECONDS);
                }
                //启动扫描过期数据
                if (workHandlerScanner != null) {
                    scannerPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-scanner"));
                    scannerPool.scheduleAtFixedRate(() -> scanAndDestroyWorkHandler(workHandlerScanner.expiredInSecond), workHandlerScanner.periodInSecond, workHandlerScanner.periodInSecond, TimeUnit.SECONDS);
                }
                //启动任务执行器
                this.workExecutors = new WorkExecutor[workExecutorNum];
                for (int i = 0; i < workExecutorNum; i++) {
                    this.workExecutors[i] = new WorkExecutor(name + "-worker(" + (i + 1) + "/" + workExecutorNum + ")", workExecutorSchedule, workExecutorBlockingChecker);
                    this.workExecutors[i].init();
                }
                //启动消费者
                KafkaExtUtil.ConsumerThreadHolder holder = KafkaExtUtil.startConsumer(name, topic, consumerProp, partitionMode, this::consume);
                consumeThread = holder.thread();
                consumeThreads = holder.threads();
            } catch (Exception ex) {
                destroy();
                throw BaseException.get(ex);
            }
        }
    }

    /**
     * 销毁资源
     */
    public synchronized void destroy() {
        if (running) {
            running = false;
            //打上退出标记、等待消费线程退出
            running_consume = false;
            ExecutorUtil.shutdownThenAwait(consumeThread, consumeThreads, resetConsumeCountPool);
            consumeThread = null;
            consumeThreads = null;
            resetConsumeCountPool = null;
            //等待工作执行器退出
            List<CompletableFuture<?>> futureList = new ArrayList<>();
            for (WorkExecutor workExecutor : workExecutors) {
                try {
                    futureList.add(workExecutor.destroy(() -> {
                        for (String id : workExecutor.workHandlers.keySet()) {
                            removeHandler(id);
                        }
                    }));
                } catch (Exception ex) {
                    throw BaseException.get(ex);
                }
            }
            try {
                for (CompletableFuture<?> future : futureList) {
                    future.join();
                }
            } catch (Exception ex) {
                logger.error("error", ex);
            }
            //取消监控、扫描过期线程
            ExecutorUtil.shutdownAllThenAwait(monitor_pool, scannerPool);

            //清空变量
            consumeThread = null;
            consumeThreads = null;
            workExecutors = null;
            consumeCount = null;
            resetConsumeCountPool = null;
            scannerPool = null;
            monitor_workHandlerCount = null;
            monitor_consumeCount = null;
            monitor_workCount = null;
            monitor_pool = null;
            pause_consume = false;
        }
    }


    /**
     * 消费
     */
    public void consume(KafkaConsumer<String, byte[]> consumer) {
        try (consumer){
            while (running_consume) {
                try {
                    //检查暂停消费
                    if (pause_consume) {
                        do {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } while (pause_consume);
                    }

                    //检查阻塞
                    if (maxBlockingNum > 0) {
                        if (blockingNum.sum() >= maxBlockingNum) {
                            TimeUnit.MILLISECONDS.sleep(100);
                            continue;
                        }
                    }
                    //消费一批数据
                    final ConsumerRecords<String, byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(1));
                    if (consumerRecords == null || consumerRecords.isEmpty()) {
                        continue;
                    }

                    //统计
                    final int count = consumerRecords.count();
                    blockingNum.add(count);
                    if (monitor_period > 0) {
                        monitor_consumeCount.add(count);
                    }

                    //检查速度、如果速度太快则阻塞
                    if (maxConsumeSpeed > 0) {
                        //控制每秒消费、如果消费过快、则阻塞一会、放慢速度
                        final int curConsumeCount = consumeCount.addAndGet(count);
                        if (curConsumeCount >= maxConsumeSpeed) {
                            do {
                                TimeUnit.MILLISECONDS.sleep(50);
                            } while (consumeCount.get() >= maxConsumeSpeed);
                        }
                    }
                    //发布消息
                    for (ConsumerRecord<String, byte[]> consumerRecord : consumerRecords) {
                        final String id = id(consumerRecord);
                        WorkExecutor workExecutor = getWorkExecutor(id);
                        //交给执行器处理
                        workExecutor.execute(() -> {
                            //首先获取workHandler
                            WorkHandler workHandler = workExecutor.workHandlers.computeIfAbsent(id, k -> {
                                //初始化workHandler
                                try {
                                    WorkHandler temp = newHandler(id);
                                    temp.afterConstruct(workExecutor, this);
                                    temp.init(consumerRecord);
                                    if (monitor_period > 0) {
                                        monitor_workHandlerCount.increment();
                                    }
                                    return temp;
                                } catch (Exception ex) {
                                    //初始化workHandler失败时候、释放阻塞
                                    blockingNum.decrement();
                                    logger.error("workHandler init error id[{}]", id, ex);
                                    return null;
                                }
                            });
                            //处理数据
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
                    }
                } catch (Exception ex) {
                    logger.error("kafka consumer topic[{}] cycle error,try again after 3s", topic, ex);
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        throw BaseException.get(e);
                    }
                }
            }
        } finally {
            String assignment = consumer.assignment().stream().map(e -> e.topic() + ":" + e.partition()).collect(Collectors.joining(","));
            logger.info("consumer[{}] assignment[{}] close", this.getClass().getName(), assignment);
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
        double consumeSpeed = FloatUtil.format(monitor_consumeCount.sumThenReset() / ((double) monitor_period), 2);
        String workQueueStatus = Arrays.stream(workExecutors).map(e -> e.blockingQueue.size() + "").collect(Collectors.joining(" "));
        double workSpeed = FloatUtil.format(monitor_workCount.sumThenReset() / ((double) monitor_period), 2);
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

