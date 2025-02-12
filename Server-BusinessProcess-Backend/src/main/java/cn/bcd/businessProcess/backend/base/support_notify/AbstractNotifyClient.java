package cn.bcd.businessProcess.backend.base.support_notify;

import cn.bcd.base.kafka.KafkaUtil;
import cn.bcd.base.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import cn.bcd.base.redis.RedisUtil;
import cn.bcd.base.util.ExecutorUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class AbstractNotifyClient extends ThreadDrivenKafkaConsumer {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Producer<String, byte[]> producer;
    public ScheduledExecutorService workPool;
    public final BoundHashOperations<String, String, String> boundHashOperations;
    public final Map<String, ListenerInfo> id_listenerInfo = new HashMap<>();
    private final String subscribeTopic;
    private final String notifyTopic;
    private final KafkaProperties.Consumer consumerProp;
    private final String type;

    /**
     * @param kafkaBootstrapServers  监听kafka地址
     * @param redisConnectionFactory redis
     * @param type                   所监听的业务的类型名称
     * @param serverId               当前服务的id
     *                               必须是全局唯一、即时同一集群的也不能一样
     *                               例如两个业务后端的客户端id、设置为bus1、bus2
     */
    public AbstractNotifyClient(List<String> kafkaBootstrapServers,
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
        this.type = type;
        this.consumerProp = new KafkaProperties.Consumer();
        this.consumerProp.setBootstrapServers(kafkaBootstrapServers);
        this.consumerProp.setGroupId(type + "_" + serverId);
        this.consumerProp.setKeyDeserializer(StringDeserializer.class);
        this.consumerProp.setValueDeserializer(ByteArraySerializer.class);
        KafkaProperties.Producer producerProp = new KafkaProperties.Producer();
        producerProp.setBootstrapServers(this.consumerProp.getBootstrapServers());
        producerProp.setKeySerializer(StringSerializer.class);
        producerProp.setValueSerializer(ByteArraySerializer.class);
        this.subscribeTopic = "subscribe_" + type;
        this.notifyTopic = "notify_" + type;
        this.producer = KafkaUtil.newKafkaProducer_string_bytes(producerProp);
        this.boundHashOperations = RedisUtil.newRedisTemplate_string_string(redisConnectionFactory).boundHashOps(this.notifyTopic);
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
            try {
                boundHashOperations.putAll(save);
            }catch (Exception e){
                logger.error("notify client schedule error type[{}]", type, e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void destroy() {
        //停止消费
        super.destroy();
        //停止工作线程池
        ExecutorUtil.shutdownThenAwait(workPool);
        workPool = null;
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> consumerRecord) throws Exception {
        String id = consumerRecord.key();
        CompletableFuture.runAsync(() -> {
            ListenerInfo listenerInfo = id_listenerInfo.get(id);
            if (listenerInfo != null) {
                try {
                    listenerInfo.consumer.accept(consumerRecord.value());
                } catch (Exception e) {
                    logger.error("notify client consumer error type[{}] id[{}]", type, id, e);
                }
            }
        }, workPool);
    }

    /**
     * 订阅
     *
     * @param id
     * @param consumer 当有消息时候回调、注意不要阻塞、因为只有一个线程处理任务、阻塞会导致所有的任务阻塞
     */
    public CompletableFuture<Void> subscribe(String id, Consumer<byte[]> consumer) {
        return CompletableFuture.runAsync(() -> {
            final ListenerInfo listenerInfo = new ListenerInfo(id, System.currentTimeMillis(), consumer);
            id_listenerInfo.put(id, listenerInfo);
            try {
                //添加到redis
                boundHashOperations.put(id, listenerInfo.toString());
                //发送kafka通知
                producer.send(new ProducerRecord<>(subscribeTopic, id, ("1" + listenerInfo).getBytes()));
                logger.info("notify client subscribe type[{}] id[{}]", type, id);
            } catch (Exception ex) {
                logger.error("notify client subscribe error type[{}] id[{}] topic[{}]", type, id, subscribeTopic, ex);
            }
        }, workPool);
    }

    /**
     * 取消订阅
     *
     * @param id {@link #subscribe(String, Consumer)}返回的id
     */
    public CompletableFuture<Void> unsubscribe(String id) {
        return CompletableFuture.runAsync(() -> {
            //删除缓存
            id_listenerInfo.remove(id);
            try {
                //从redis删除
                boundHashOperations.delete(id);
                //发送kafka通知
                producer.send(new ProducerRecord<>(subscribeTopic, id, ("2" + id).getBytes()));
                logger.info("notify client unsubscribe type[{}] id[{}]", type, id);
            } catch (Exception ex) {
                logger.error("notify client unsubscribe error type[{}] id[{}] topic[{}]", type, id, subscribeTopic, ex);
            }
        });
    }
}
