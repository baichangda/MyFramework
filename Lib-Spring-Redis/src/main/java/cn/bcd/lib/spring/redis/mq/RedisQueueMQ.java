package cn.bcd.lib.spring.redis.mq;

import cn.bcd.lib.spring.redis.RedisUtil;
import cn.bcd.lib.base.util.ExecutorUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class RedisQueueMQ<V> implements AutoCloseable {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String name;

    private final int consumerThreadNum;

    private final int workThreadNum;

    private final RedisSerializer<V> valueSerializer;

    private final BoundListOperations<String, byte[]> boundListOperations;

    private ThreadPoolExecutor consumeExecutor;

    private ThreadPoolExecutor workExecutor;

    private final RedisTemplate<String, byte[]> redisTemplate;

    private volatile boolean stop;

    private volatile boolean consumerAvailable;


    public RedisQueueMQ(String name, RedisConnectionFactory connectionFactory, Class<V> valueClass,
                        int consumerThreadNum, int workThreadNum) {
        this(name, connectionFactory, RedisUtil.getValueSerializer(valueClass),
                consumerThreadNum, workThreadNum);
    }

    private RedisQueueMQ(String name, RedisConnectionFactory connectionFactory, RedisSerializer<V> valueSerializer,
                         int consumerThreadNum, int workThreadNum) {
        this.name = name;
        this.consumerThreadNum = consumerThreadNum;
        this.workThreadNum = workThreadNum;

        this.redisTemplate = RedisUtil.newRedisTemplate_string_bytes(connectionFactory);
        this.boundListOperations = redisTemplate.boundListOps(name);
        this.valueSerializer = Objects.requireNonNull(valueSerializer, "valueSerializer");
    }

    public String getName() {
        return name;
    }

    protected byte[] compress(byte[] data) {
        return data;
    }

    protected byte[] unCompress(byte[] data) {
        return data;
    }

    public void send(V data) {
        boundListOperations.leftPush(compress(valueSerializer.serialize(data)));
    }

    public void sendBatch(List<V> dataList) {
        byte[][] bytesArr = dataList.stream().map(e -> compress(valueSerializer.serialize(e))).toArray(byte[][]::new);
        boundListOperations.leftPushAll(bytesArr);
    }

    public void init() {
        if (!consumerAvailable) {
            synchronized (this) {
                if (!consumerAvailable) {
                    this.stop = false;
                    this.consumeExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(consumerThreadNum);
                    this.workExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(workThreadNum);
                    start();
                    consumerAvailable = true;
                }
            }
        }
    }

    public void close() {
        if (consumerAvailable) {
            synchronized (this) {
                if (consumerAvailable) {
                    this.stop = true;
                    ExecutorUtil.shutdownThenAwait(true, consumeExecutor, workExecutor);
                    consumerAvailable = false;
                }
            }
        }
    }


    public void onMessage(V data) {
        logger.info(data.toString());
    }

    private void onMessageFromRedis(byte[] data) {
        onMessage(valueSerializer.deserialize(unCompress(data)));
    }

    protected void start() {
        while (consumeExecutor.getPoolSize() < consumeExecutor.getMaximumPoolSize()) {
            consumeExecutor.execute(() -> {
                long popTimeout = 1000L;
                while (!stop) {
                    try {
                        byte[] data = boundListOperations.rightPop(Duration.ofMillis(popTimeout));
                        if (data != null) {
                            workExecutor.execute(() -> {
                                try {
                                    onMessageFromRedis(data);
                                } catch (Exception e) {
                                    logger.error("onMessageFromRedis error", e);
                                }
                            });
                        }
                    } catch (Exception ex) {
                        if (ex instanceof QueryTimeoutException) {
                            logger.error("redisQueueMQ queue[{}] QueryTimeoutException", name, ex);
                        } else {
                            logger.error("redisQueueMQ queue[{}] error,try after 10s", name, ex);
                            try {
                                Thread.sleep(10000L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                logger.info("redisQueueMQ queue[{}] stop", name);
            });
        }
    }
}
