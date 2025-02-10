package cn.bcd.businessProcess.backend.base.support_notify;

import cn.bcd.base.kafka.ext.ProducerFactory;
import cn.bcd.base.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import cn.bcd.base.redis.RedisUtil;
import cn.bcd.base.util.ExecutorUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 由消息提供方实现、并注册为spring bean
 * 主要实现
 * {@link #onListenerInfoUpdate (ListenerInfo)} 用于更新订阅
 * 逻辑流程
 * 1、启动时候会从redis中加载所有订阅信息
 * 2、kafka监听订阅和取消订阅请求
 * 3、每隔1min检查订阅信息是否有变化
 * 主要是为了解决客户端掉线、未主动取消订阅导致服务端无法更新缓存
 * <p>
 * 通过调用{@link #notify(byte[])}发送通知
 */
public abstract class AbstractNotifyServer extends ThreadDrivenKafkaConsumer {
    static Logger logger = LoggerFactory.getLogger(AbstractNotifyServer.class);
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
     * @param serverId               当前服务的id
     *                               必须是全局唯一、即时同一集群的也不能一样
     *                               例如两个业务后端的客户端id、设置为bus1、bus2
     */
    public AbstractNotifyServer(String kafkaBootstrapServers,
                                RedisConnectionFactory redisConnectionFactory,
                                String type,
                                String serverId) {
        super("notifyServer(" + type + ")",
                false,
                1,
                100,
                1000,
                true,
                0,
                0,
                "subscribe_" + type);
        this.consumerProp = new KafkaProperties.Consumer();
        this.consumerProp.setBootstrapServers(Arrays.stream(kafkaBootstrapServers.split(",")).toList());
        this.consumerProp.setGroupId(type + "_" + serverId);
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(this.consumerProp.getBootstrapServers());
        this.subscribeTopic = "subscribe_" + type;
        this.notifyTopic = "notify_" + type;
        this.type = type;
        this.boundHashOperations = RedisUtil.newString_StringRedisTemplate(redisConnectionFactory).boundHashOps(this.notifyTopic);
        this.producer = ProducerFactory.newProducer(producerProp);
    }


    public void init() {
        workPool = Executors.newSingleThreadExecutor();
        scheduledPool = Executors.newSingleThreadScheduledExecutor();
        //初始化redis订阅数据到缓存
        checkAndUpdateCache().join();
        //开始消费
        super.init(consumerProp);
        //启动线程定时更新缓存
        startUpdateCacheFromRedis();
    }

    public void destroy() {
        //停止消费
        super.destroy();
        //停止工作线程池
        ExecutorUtil.shutdownAllThenAwait(workPool, scheduledPool);
        workPool = null;
        scheduledPool = null;
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) {
        final byte[] value = consumerRecord.value();
        final char flag = (char) value[0];
        final String content = new String(value, 1, value.length - 1);
        try {
            final ListenerInfo listenerInfo = ListenerInfo.fromString(content);
            if (flag == '1') {
                workPool.execute(() -> {
                    cache.put(listenerInfo.id, listenerInfo);
                    onListenerInfoUpdate(cache.values().toArray(new ListenerInfo[0]));
                });
                logger.info("server subscribe type[{}] id[{}] flag[{}]", type, listenerInfo.id, flag);
            } else {
                workPool.execute(() -> {
                    cache.remove(content);
                    onListenerInfoUpdate(cache.values().toArray(new ListenerInfo[0]));
                });
                logger.info("server unsubscribe type[{}] id[{}] flag[{}]", type, listenerInfo.id, flag);
            }
        } catch (IOException e) {
            logger.error("ListenerInfo.fromString error type[{}] flag[{}] value:\n{}", type, flag, value, e);
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
                    logger.error("ListenerInfo.fromString error type[{}] value:\n{}", type, value);
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
                onListenerInfoUpdate(cache.values().toArray(new ListenerInfo[0]));
            }
        }, workPool);
    }

    /**
     * 当监控信息发生变更后的回调方法
     * 注意不要阻塞此方法
     * 因为针对该类所有操作都是由单线程完成
     *
     * @param listenerInfos 全量有效的订阅信息
     */
    public void onListenerInfoUpdate(ListenerInfo[] listenerInfos) {

    }

    /**
     * 发送通知(异步)
     * 会先判断是否有订阅者，有则发送，无则不发送
     * 如果不发送、则{@link CompletableFuture#get()}结果为null
     *
     * @param bytes
     * @return
     */
    public CompletableFuture<Future<RecordMetadata>> notify(final byte[] bytes) {
        return CompletableFuture.supplyAsync(() -> {
            if (cache.isEmpty()) {
                return null;
            } else {
                return producer.send(new ProducerRecord<>(notifyTopic, bytes));
            }
        }, workPool);

    }

}
