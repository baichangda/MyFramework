package cn.bcd.lib.base.redis.mq.queue;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.redis.RedisUtil;
import cn.bcd.lib.base.redis.mq.ValueSerializerType;
import cn.bcd.lib.base.util.ClassUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import com.fasterxml.jackson.databind.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class RedisQueueMQ<V> {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String name;

    private final int consumerThreadNum;

    private final int workThreadNum;

    private final RedisSerializer<V> valueSerializer;

    private final BoundListOperations<String, byte[]> boundListOperations;

    private ThreadPoolExecutor consumeExecutor;

    private ThreadPoolExecutor workExecutor;

    private final RedisTemplate<String, byte[]> redisTemplate;

    private boolean stop;

    private volatile boolean consumerAvailable;


    public RedisQueueMQ(String name, RedisConnectionFactory connectionFactory, ValueSerializerType valueSerializerType, int consumerThreadNum, int workThreadNum) {
        this.name = name;
        this.consumerThreadNum = consumerThreadNum;
        this.workThreadNum = workThreadNum;

        this.redisTemplate = RedisUtil.newRedisTemplate_string_bytes(connectionFactory);
        this.boundListOperations = redisTemplate.boundListOps(name);
        this.valueSerializer = (RedisSerializer<V>) getDefaultRedisSerializer(valueSerializerType);
    }

    public String getName() {
        return name;
    }

    private RedisSerializer<?> getDefaultRedisSerializer(ValueSerializerType valueSerializerType) {
        switch (valueSerializerType) {
            case BYTE_ARRAY -> {
                return RedisUtil.SERIALIZER_VALUE_BYTEARRAY;
            }
            case STRING -> {
                return RedisUtil.SERIALIZER_VALUE_STRING;
            }
            case SERIALIZABLE -> {
                return RedisUtil.SERIALIZER_VALUE_JDK;
            }
            case JACKSON -> {
                return RedisUtil.newJackson2JsonRedisSerializer(parseValueJavaType());
            }
            default -> {
                throw BaseException.get("valueSerializerType [{}] not support", valueSerializerType);
            }
        }
    }

    private JavaType parseValueJavaType() {
        Type parentType = ClassUtil.getParentUntil(getClass(), RedisQueueMQ.class);
        return JsonUtil.getJavaType(((ParameterizedType) parentType).getActualTypeArguments()[0]);
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

    public void initConsumer() {
        if (!consumerAvailable) {
            synchronized (this) {
                if (!consumerAvailable) {
                    this.stop = false;
                    this.consumeExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(consumerThreadNum);
                    this.workExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(workThreadNum);
                    start();
                }
            }
        }
    }

    public void destroy() {
        if (consumerAvailable) {
            synchronized (this) {
                if (consumerAvailable) {
                    this.stop = true;
                    ExecutorUtil.shutdownThenAwait(consumeExecutor, workExecutor);
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
                long popTimeout = ((LettuceConnectionFactory) redisTemplate.getConnectionFactory()).getTimeout() / 2;
                while (!stop) {
                    try {
                        byte[] data = boundListOperations.rightPop(popTimeout, TimeUnit.MILLISECONDS);
                        if (data != null) {
                            workExecutor.execute(() -> {
                                try {
                                    onMessageFromRedis(data);
                                } catch (Exception e) {
                                    logger.error("onMessageFromRedis error",e);
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
                                throw BaseException.get(e);
                            }
                        }
                    }
                }
                logger.info("redisQueueMQ queue[{}] stop", name);
            });
        }
    }
}
