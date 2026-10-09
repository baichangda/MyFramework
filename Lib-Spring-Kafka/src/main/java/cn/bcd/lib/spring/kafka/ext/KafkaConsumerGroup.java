package cn.bcd.lib.spring.kafka.ext;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.kafka.KafkaUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 一组Kafka消费线程及其启动结果。
 */
public final class KafkaConsumerGroup implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerGroup.class);

    private final List<Thread> threads;
    private final CompletableFuture<Void> startupResult;
    private boolean started;

    private KafkaConsumerGroup(List<Thread> threads, CompletableFuture<Void> startupResult) {
        this.threads = List.copyOf(threads);
        this.startupResult = startupResult;
    }

    public static KafkaConsumerGroup create(String name,
                                            Map<String, Object> properties,
                                            ConsumerParam consumerParam,
                                            Consumer<KafkaConsumer<String, byte[]>> consumerLoop) {
        Map<String, Object> config = new HashMap<>(properties);
        if (consumerParam.seekTimestamp == -2) {
            config.put("auto.offset.reset", "earliest");
        }

        List<ConsumerTask> tasks = createTasks(name, consumerParam);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("consumer task cannot be empty");
        }

        List<Thread> threads = new ArrayList<>(tasks.size());
        List<CompletableFuture<Void>> startupResults = new ArrayList<>(tasks.size());
        for (ConsumerTask task : tasks) {
            CompletableFuture<Void> startupResult = new CompletableFuture<>();
            threads.add(new Thread(() -> {
                KafkaConsumer<String, byte[]> consumer;
                try {
                    consumer = KafkaUtil.newKafkaConsumer_string_bytes(config);
                } catch (Exception ex) {
                    completeStartupExceptionally(startupResult, ex);
                    return;
                }

                try (consumer) {
                    try {
                        task.initializer().accept(consumer);
                        seek(consumer, consumerParam.seekTimestamp);
                    } catch (Exception ex) {
                        completeStartupExceptionally(startupResult, ex);
                        return;
                    }
                    startupResult.complete(null);

                    try {
                        consumerLoop.accept(consumer);
                    } catch (Exception ex) {
                        logger.error("kafka consumer run error", ex);
                    }
                } catch (Exception ex) {
                    logger.error("kafka consumer close error", ex);
                }
            }, task.threadName()));
            startupResults.add(startupResult);
            logger.info("create consumer[{}]", task.threadName());
        }
        return new KafkaConsumerGroup(threads,
                CompletableFuture.allOf(startupResults.toArray(CompletableFuture[]::new)));
    }

    public synchronized CompletableFuture<Void> start() {
        if (!started) {
            started = true;
            try {
                threads.forEach(Thread::start);
            } catch (RuntimeException | Error ex) {
                startupResult.completeExceptionally(ex);
                logger.error("start kafka consumer thread error", ex);
            }
        }
        return startupResult;
    }

    public boolean contains(Thread thread) {
        return threads.contains(thread);
    }

    private static List<ConsumerTask> createTasks(String name, ConsumerParam consumerParam) {
        List<ConsumerTask> tasks = new ArrayList<>();
        switch (consumerParam.mode) {
            case 1 -> tasks.add(new ConsumerTask(threadName(name, 0, 1),
                    consumer -> consumer.subscribe(Arrays.asList(consumerParam.topics),
                            new ConsumerRebalanceLogger(consumer))));
            case 2 -> {
                for (int i = 0; i < consumerParam.topics.length; i++) {
                    String topic = consumerParam.topics[i];
                    tasks.add(new ConsumerTask(threadName(name, i, consumerParam.topics.length),
                            consumer -> consumer.subscribe(Collections.singletonList(topic),
                                    new ConsumerRebalanceLogger(consumer))));
                }
            }
            case 3 -> tasks.add(new ConsumerTask(threadName(name, 0, 1),
                    consumer -> consumer.assign(Arrays.asList(consumerParam.topicPartitions))));
            case 4 -> {
                for (int i = 0; i < consumerParam.topicPartitions.length; i++) {
                    TopicPartition partition = consumerParam.topicPartitions[i];
                    tasks.add(new ConsumerTask(threadName(name, i, consumerParam.topicPartitions.length),
                            consumer -> consumer.assign(Collections.singletonList(partition))));
                }
            }
            default -> throw BaseException.get("ConsumerParam mode not support", name, consumerParam.mode);
        }
        return tasks;
    }

    private static String threadName(String name, int index, int count) {
        return name + "-consumer(" + (index + 1) + "/" + count + ")";
    }

    private static void seek(KafkaConsumer<String, byte[]> consumer, long timestamp) {
        if (timestamp == -3) {
            KafkaUtil.consumerSeekToEnd(consumer);
        } else if (timestamp == -2) {
            KafkaUtil.consumerSeekToBeginning(consumer);
        } else if (timestamp >= 0) {
            KafkaUtil.consumerSeekToTimestamp(consumer, timestamp);
        }
    }

    private static void completeStartupExceptionally(CompletableFuture<Void> startupResult, Exception ex) {
        startupResult.completeExceptionally(ex);
        logger.error("kafka consumer start error", ex);
    }

    private record ConsumerTask(String threadName,
                                Consumer<KafkaConsumer<String, byte[]>> initializer) {
    }

    @Override
    public void close() {
        Thread current = Thread.currentThread();
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            if (thread == current) {
                continue;
            }
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
