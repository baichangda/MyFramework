package cn.bcd.lib.spring.kafka.ext;

import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 管理一次 Kafka 消费启动：复制参数、创建消费线程、汇总初始化结果，并处理停止和启动失败。
 * 每个实例只启动一次；失败后重试应新建实例，避免旧线程影响下一次启动。
 * <p>
 * 连接的创建、订阅、seek、消费和关闭均在各自消费线程中执行。
 * 协调线程只负责启动和等待结果；业务消息如何派发给工作执行器，由 consume 回调决定。
 */
public final class ConsumerStartup implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerStartup.class);

    /** 本次启动的全部消费线程；构造后数组内容固定，可供关闭和线程归属判断使用。 */
    private final Thread[] threads;
    /** 短生命周期的协调线程，避免启动调用方等待网络初始化或失败清理。 */
    private final Thread coordinator;
    /** 内部初始化信号：全部成功才完成，任意初始化失败即可唤醒协调线程。 */
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    /** 对外结果：成功表示全部初始化完成；失败时还保证本轮消费线程已退出。 */
    private final CompletableFuture<Void> result = new CompletableFuture<>();
    private final Runnable onStartupFailure;
    /** 由本对象的监视器保护，防止重复启动协调线程。 */
    private boolean started;
    /** 由关闭或失败路径写入，消费线程在进入业务消费前检查。 */
    private volatile boolean stopped;

    /**
     * 只准备线程和参数快照；调用方保存本对象后，再调用 start()。
     * 先保存再启动，才能让消费回调中的 close() 正确识别所属线程，避免等待自己退出。
     *
     * @param name 消费线程和协调线程的名称前缀
     * @param properties 原生消费者配置，构造时复制，后续修改原 Map 不影响本次启动
     * @param consumerParam 消费模式、订阅目标和起始位置，构造时拆分为独立快照
     * @param consume 在所属消费线程内执行的消费循环，返回后关闭该线程的 Kafka 连接
     * @param onStartupFailure 失败或未启动即关闭时通知调用方停止消费；必须快速返回，不能抛异常
     *                         或等待本次启动完成，否则会阻塞失败清理
     */
    public ConsumerStartup(String name, Map<String, Object> properties, ConsumerParam consumerParam,
                           Consumer<KafkaConsumer<String, byte[]>> consume, Runnable onStartupFailure) {
        Map<String, Object> config = new HashMap<>(properties);
        List<ConsumerParam> params = split(Objects.requireNonNull(consumerParam, "consumerParam"));
        Objects.requireNonNull(consume, "consume");
        this.onStartupFailure = Objects.requireNonNull(onStartupFailure, "onStartupFailure");
        // 多个消费线程并行初始化，最后一个成功者发布 initialized 的成功结果。
        AtomicInteger remaining = new AtomicInteger(params.size());
        threads = new Thread[params.size()];
        for (int i = 0; i < threads.length; i++) {
            ConsumerParam param = params.get(i);
            threads[i] = new Thread(() -> runConsumer(param, config, consume, remaining),
                    name + "-consumer(" + (i + 1) + "/" + threads.length + ")");
        }
        coordinator = Thread.ofVirtual().name(name + "-startup").unstarted(() -> {
            try {
                // 与 close() 使用同一把锁，保证关闭后不会再启动尚未启动的消费线程。
                synchronized (this) {
                    if (stopped) {
                        throw new IllegalStateException("consumer closed during startup");
                    }
                    for (Thread thread : threads) {
                        thread.start();
                    }
                }
                // 等待发生在协调线程，不占用调用 start() 的线程或任何消费线程。
                initialized.join();
                // 非 Async 的完成回调可能在这里同步执行，调用方不应在回调中长时间阻塞。
                result.complete(null);
            } catch (RuntimeException | Error ex) {
                stopped = true;
                // 先让业务消费循环停止，再中断并等待所有线程，最后才允许调用方看到失败并重试。
                onStartupFailure.run();
                ExecutorUtil.shutdownAllThenAwait(true, (Object) threads);
                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                logger.error("kafka consumer start error", cause);
                result.completeExceptionally(cause);
            }
        });
    }

    /**
     * 非阻塞地发起启动，重复调用返回同一次结果，不重新创建连接。
     * 成功仅表示连接创建、订阅/分区指定和 seek 完成，不代表消费结束或消息处理成功。
     * 默认 subscribe 模式不主动等待分区分配；分配发生在消费循环后续的 poll 中。
     * 失败结果在全部消费线程退出后发布，调用方可安全创建新的 ConsumerStartup 重试。
     */
    public synchronized CompletableFuture<Void> start() {
        if (!started && !stopped) {
            started = true;
            try {
                coordinator.start();
            } catch (RuntimeException | Error ex) {
                // 协调线程本身未能启动，此时尚未启动任何消费线程，无需等待网络资源清理。
                stopped = true;
                onStartupFailure.run();
                result.completeExceptionally(ex);
            }
        }
        return result;
    }

    private void runConsumer(ConsumerParam param, Map<String, Object> config,
                             Consumer<KafkaConsumer<String, byte[]>> consume, AtomicInteger remaining) {
        // 区分本线程初始化失败与进入消费后的异常，后者不会改写已经发布的启动成功结果。
        boolean ready = false;
        try (KafkaConsumer<String, byte[]> consumer = newConsumer(param, config)) {
            // 初始化可能涉及网络等待，其间已收到关闭请求时不再进入业务消费。
            if (stopped || Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("consumer stopped during startup");
            }
            ready = true;
            if (remaining.decrementAndGet() == 0) {
                initialized.complete(null);
            }
            consume.accept(consumer);
        } catch (Exception | Error ex) {
            if (!ready) {
                initialized.completeExceptionally(ex);
            } else {
                logger.error("kafka consumer run or close error", ex);
            }
        }
    }

    /** 包含协调线程，保证启动完成回调中发起关闭时也能被识别为内部调用。 */
    public boolean contains(Thread thread) {
        return thread == coordinator || Arrays.asList(threads).contains(thread);
    }

    /** 返回不可修改的消费线程列表，用于诊断；不包含协调线程。 */
    public List<Thread> threads() {
        return List.of(threads);
    }

    /**
     * 阻止继续启动并中断所有消费线程；外部调用等待消费线程和协调线程全部退出。
     * 内部线程调用只发出停止请求，由线程退出及协调线程的失败路径继续收尾，避免自等待。
     * 本方法只处理 Kafka 线程和连接，业务工作队列由外层 Consumer 负责关闭。
     */
    @Override
    public void close() {
        synchronized (this) {
            stopped = true;
            IllegalStateException error = new IllegalStateException("consumer stopped during startup");
            // 即使尚无线程报告初始化结果，也要唤醒正在等待的协调线程。
            initialized.completeExceptionally(error);
            if (!started && !result.isDone()) {
                onStartupFailure.run();
                result.completeExceptionally(error);
            }
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }
        // join 必须放在锁外，否则协调线程可能拿不到同一把锁而无法退出。
        if (!contains(Thread.currentThread())) {
            ExecutorUtil.await((Object) threads, coordinator);
        }
    }

    /**
     * 将四种模式展开为每个线程的一份单消费者参数，统一后续连接创建逻辑。
     * 单线程模式复制整个数组，多线程模式为每个目标新建数组；同时复制 seek 位置，
     * 使启动后的线程不再依赖调用方可修改的原参数。不在这里创建网络连接。
     */
    static List<ConsumerParam> split(ConsumerParam source) {
        List<ConsumerParam> params = new ArrayList<>();
        switch (source.mode) {
            case 1 -> params.add(ConsumerParam.get_singleConsumer_subscribeTopics(source.topics.clone()));
            case 2 -> {
                for (String topic : source.topics) {
                    params.add(ConsumerParam.get_singleConsumer_subscribeTopics(topic));
                }
            }
            case 3 -> params.add(ConsumerParam.get_singleConsumer_assignTopicPartitions(source.topicPartitions.clone()));
            case 4 -> {
                for (TopicPartition partition : source.topicPartitions) {
                    params.add(ConsumerParam.get_singleConsumer_assignTopicPartitions(partition));
                }
            }
            default -> throw new IllegalArgumentException("unsupported consumer mode: " + source.mode);
        }
        if (params.isEmpty()) {
            throw new IllegalArgumentException("consumer task cannot be empty");
        }
        for (ConsumerParam param : params) {
            param.seekTimestamp = source.seekTimestamp;
        }
        return List.copyOf(params);
    }

    /**
     * 在所属消费线程内创建并初始化一个消费者，成功后由 runConsumer 的资源块负责关闭。
     * 初始化尚未成功时资源尚未交给 runConsumer，必须在本方法中主动关闭。
     */
    private static KafkaConsumer<String, byte[]> newConsumer(ConsumerParam param, Map<String, Object> properties) {
        if (param.mode != 1 && param.mode != 3) {
            throw new IllegalStateException("split multi-consumer parameters before creating a consumer");
        }
        Map<String, Object> config = new HashMap<>(properties);
        if (param.seekTimestamp == -2) {
            // seek 工具会先 poll 等待分区；无已提交位点时先使用 earliest，随后再显式 seek。
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }
        KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(config);
        try {
            // subscribe 参与组内自动分区分配；assign 直接指定分区，不依赖组内自动分配。
            if (param.mode == 1) {
                consumer.subscribe(Arrays.asList(param.topics), new ConsumerRebalanceLogger(consumer));
            } else {
                consumer.assign(Arrays.asList(param.topicPartitions));
            }
            if (param.seekTimestamp == -3) {
                KafkaUtil.consumerSeekToEnd(consumer);
            } else if (param.seekTimestamp == -2) {
                KafkaUtil.consumerSeekToBeginning(consumer);
            } else if (param.seekTimestamp >= 0) {
                KafkaUtil.consumerSeekToTimestamp(consumer, param.seekTimestamp);
            }
            return consumer;
        } catch (RuntimeException | Error ex) {
            try {
                consumer.close();
            } catch (RuntimeException | Error closeError) {
                // 保留最初的初始化错误作为主异常，关闭错误只作为附加信息。
                ex.addSuppressed(closeError);
            }
            throw ex;
        }
    }
}
