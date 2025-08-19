package cn.bcd.lib.data.notify.subscribeNotify;

import cn.bcd.lib.base.kafka.KafkaUtil;
import cn.bcd.lib.base.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import cn.bcd.lib.base.redis.RedisUtil;
import cn.bcd.lib.base.util.ExecutorUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 由消息提供方实现、并注册为spring bean
 * 逻辑流程
 * 1、启动时候会从redis中加载所有订阅信息
 * 2、kafka监听订阅和取消订阅请求
 * 3、每隔1min检查订阅信息是否有变化
 * 主要是为了解决客户端掉线、未主动取消订阅导致服务端无法更新缓存
 * <p>
 * 通过调用{@link #notify(String, Supplier)}发送通知
 */
public abstract class AbstractNotifyServer extends ThreadDrivenKafkaConsumer {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final String type;
    public final BoundHashOperations<String, String, String> boundHashOperations;
    public ExecutorService workPool;
    public ScheduledExecutorService scheduledPool;
    private final Producer<String, byte[]> producer;
    private final String subscribeTopic;
    private final String notifyTopic;

    private Map<String, ListenerInfo> cache = new HashMap<>();
    private final KafkaProperties.Consumer consumerProp;

    /**
     * @param kafkaBootstrapServers  监听kafka地址
     * @param redisConnectionFactory redis
     * @param type                   所监听的业务的类型名称
     * @param groupId                消费组id
     *                               必须是全局唯一、即时同一集群的也不能一样
     *                               例如两个业务后端的客户端id、设置为bus1、bus2
     */
    public AbstractNotifyServer(List<String> kafkaBootstrapServers,
                                RedisConnectionFactory redisConnectionFactory,
                                String type,
                                String groupId) {
        super("notifyServer(" + type + ")",
                false,
                1,
                100,
                1000,
                true,
                0,
                0,
                "_subscribe_" + type, null);
        this.consumerProp = new KafkaProperties.Consumer();
        this.consumerProp.setBootstrapServers(kafkaBootstrapServers);
        this.consumerProp.setGroupId(groupId);
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(this.consumerProp.getBootstrapServers());
        producerProp.setKeySerializer(StringSerializer.class);
        producerProp.setValueSerializer(ByteArraySerializer.class);
        this.subscribeTopic = "_subscribe_" + type;
        this.notifyTopic = "_notify_" + type;
        this.type = type;
        this.boundHashOperations = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory).boundHashOps(this.notifyTopic);
        this.producer = KafkaUtil.newKafkaProducer_string_bytes(producerProp.buildProperties(new DefaultSslBundleRegistry()));
    }


    public void init() {
        workPool = Executors.newSingleThreadExecutor();
        scheduledPool = Executors.newSingleThreadScheduledExecutor();
        //初始化redis订阅数据到缓存
        checkAndUpdateCache().join();
        //开始消费
        super.init(consumerProp.buildProperties(new DefaultSslBundleRegistry()));
        //启动线程定时更新缓存
        startUpdateCacheFromRedis();
    }

    public void destroy() {
        //停止消费
        super.destroy();
        //停止工作线程池
        ExecutorUtil.shutdownAllThenAwait(true, workPool, scheduledPool);
        workPool = null;
        scheduledPool = null;
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
        final byte[] value = consumerRecord.value();
        final char flag = (char) value[0];
        final String content = new String(value, 1, value.length - 1);
        try {
            if (flag == '1') {
                final ListenerInfo listenerInfo = ListenerInfo.fromString(content);
                workPool.execute(() -> {
                    cache.put(listenerInfo.id, listenerInfo);
                });
                logger.info("notify server subscribe type[{}] id[{}]", type, listenerInfo.id);
            } else {
                workPool.execute(() -> {
                    cache.remove(content);
                });
                logger.info("notify server unsubscribe type[{}] id[{}]", type, content);
            }
        } catch (IOException e) {
            logger.error("notify server ListenerInfo.fromString error type[{}] value:\n{}", type, new String(value), e);
        }
    }

    /**
     * 每隔1min
     * 从redis中获取所有的监听信息、更新本地缓存
     */
    private void startUpdateCacheFromRedis() {
        scheduledPool.scheduleWithFixedDelay(this::checkAndUpdateCache, 1, 1, TimeUnit.MINUTES);
    }


    private CompletableFuture<Void> checkAndUpdateCache() {
        Map<String, ListenerInfo> aliveMap = new HashMap<>();
        Map<String, String> entries = boundHashOperations.entries();
        if (entries != null) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String value = entry.getValue();
                try {
                    final ListenerInfo listenerInfo = ListenerInfo.fromString(value);
                    if ((System.currentTimeMillis() - listenerInfo.ts) <= 60000) {
                        aliveMap.put(entry.getKey(), listenerInfo);
                    }
                } catch (IOException e) {
                    logger.error("notify server ListenerInfo.fromString error type[{}] value:\n{}", type, value);
                }
            }
        }
        return CompletableFuture.runAsync(() -> {
            boolean update;
            if (aliveMap.size() == cache.size()) {
                update = aliveMap.entrySet().stream().anyMatch(e -> !cache.containsKey(e.getKey()));
            } else {
                update = true;
            }
            if (update) {
                cache = aliveMap;
            }
        }, workPool);
    }


    /**
     * 发送通知(异步)
     * 会先判断是否有订阅者，有则发送，无则不发送
     * 如果不发送、则{@link CompletableFuture#get()}结果为null
     *
     * @param id
     * @param supplier
     * @return
     */
    public CompletableFuture<Future<RecordMetadata>> notify(String id, Supplier<byte[]> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            if (cache.containsKey(id)) {
                try {
                    return producer.send(new ProducerRecord<>(notifyTopic, id, supplier.get()));
                } catch (Exception ex) {
                    logger.info("notify server notify error type[{}] id[{}]", type, id, ex);
                    return null;
                }
            } else {
                return null;
            }
        }, workPool);

    }


}
