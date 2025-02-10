package cn.bcd.businessProcess.backend.base.support_notify;

import cn.bcd.base.kafka.ext.ProducerFactory;
import cn.bcd.base.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import cn.bcd.base.redis.RedisUtil;
import cn.bcd.base.util.ExecutorUtil;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractNotifyClient extends ThreadDrivenKafkaConsumer {
    static Logger logger = LoggerFactory.getLogger(AbstractNotifyClient.class);
    private final Producer<String, byte[]> producer;
    public ScheduledExecutorService workPool;
    public final BoundHashOperations<String, String, String> boundHashOperations;
    public final Map<String, ListenerInfo> id_listenerInfo = new HashMap<>();
    private final String subscribeTopic;
    private final String notifyTopic;
    private final KafkaProperties.Consumer consumerProp;

    /**
     * @param kafkaBootstrapServers  监听kafka地址
     * @param redisConnectionFactory redis
     * @param type                   所监听的业务的类型名称
     * @param serverId               当前服务的id
     *                               必须是全局唯一、即时同一集群的也不能一样
     *                               例如两个业务后端的客户端id、设置为bus1、bus2
     */
    public AbstractNotifyClient(String kafkaBootstrapServers,
                                RedisConnectionFactory redisConnectionFactory,
                                String type,
                                String serverId) {
        super("notifyClient(" + type + ")",
                false,
                1,
                100,
                1000,
                true,
                0,
                0,
                "notify_" + type);
        this.consumerProp = new KafkaProperties.Consumer();
        this.consumerProp.setBootstrapServers(Arrays.stream(kafkaBootstrapServers.split(",")).toList());
        this.consumerProp.setGroupId(type + "_" + serverId);
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(this.consumerProp.getBootstrapServers());
        this.subscribeTopic = "subscribe_" + type;
        this.notifyTopic = "notify_" + type;
        this.producer = ProducerFactory.newProducer(producerProp);
        this.boundHashOperations = RedisUtil.newString_StringRedisTemplate(redisConnectionFactory).boundHashOps(this.notifyTopic);
    }


    public void init() {
        //开始消费
        super.init(consumerProp);
        workPool = Executors.newSingleThreadScheduledExecutor();
        workPool.scheduleWithFixedDelay(() -> {
            final long ts = System.currentTimeMillis();
            Map<String, String> save = new HashMap<>();
            for (Map.Entry<String, ListenerInfo> entry2 : id_listenerInfo.entrySet()) {
                ListenerInfo value2 = entry2.getValue();
                value2.ts = ts;
                save.put(entry2.getKey(), value2.toString());
            }
            boundHashOperations.putAll(save);
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void destroy() {
        //停止消费
        super.destroy();
        //停止工作线程池
        ExecutorUtil.shutdownThenAwait(workPool);
        workPool = null;
    }

    /**
     * 订阅
     *
     * @param id
     */
    public CompletableFuture<String> subscribe(String id) {
        return CompletableFuture.supplyAsync(() -> {
            final ListenerInfo listenerInfo = new ListenerInfo(id, System.currentTimeMillis());
            id_listenerInfo.put(id, listenerInfo);
            //添加到redis
            boundHashOperations.put(id, listenerInfo.toString());
            //发送kafka通知
            producer.send(new ProducerRecord<>(subscribeTopic, ("1" + listenerInfo).getBytes()));
            logger.info("client subscribe id[{}]", id);
            return id;
        }, workPool);
    }

    /**
     * 取消订阅
     *
     * @param id {@link #subscribe(String)}返回的id
     */
    public CompletableFuture<Void> unSubscribe(String id) {
        return CompletableFuture.runAsync(() -> {
            //删除缓存
            id_listenerInfo.remove(id);
            //从redis删除
            boundHashOperations.delete(id);
            //发送kafka通知
            producer.send(new ProducerRecord<>(subscribeTopic, ("2" + id).getBytes()));
            logger.info("client unSubscribe id[{}]", id);
        });
    }
}
