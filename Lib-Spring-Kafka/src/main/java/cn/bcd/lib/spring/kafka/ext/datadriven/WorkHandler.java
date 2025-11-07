package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.util.DateUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * 本类中所有的方法为了保证数据并发安全，都是在executor中执行的、包括
 * {@link #WorkHandler(String)}
 * {@link #init(ConsumerRecord)}
 * {@link #destroy()}
 * {@link #onMessage(ConsumerRecord)}
 * 同时、为了保证线程安全性、建议所有异步操作交给executor执行、但是不要阻塞、因为{@link #executor}只有一个线程
 * <p>
 * 需要注意的是、此对象在内存中的数量和id数量有关、例如有10000个id的数据、则最多存在10000个对象
 * 所以不建议此对象子类的类变量定义过多过大、因为这样可能会导致内存消耗太大
 */
public abstract class WorkHandler {
    public final String id;
    /**
     * 所属执行器
     */
    public WorkExecutor executor;

    /**
     * 所属的消费者
     */
    public DataDrivenKafkaConsumer consumer;

    /**
     * 创建时间(秒)
     */
    public final long createTime;
    /**
     * 最后一条信息时间(秒)
     */
    public long lastMessageTime;

    /**
     * @param id id
     */
    public WorkHandler(String id) {
        this.id = id;
        this.createTime = DateUtil.CacheSecond.current();
    }

    /**
     * @param executor 关联的执行器
     * @param consumer 关联的消费者
     */
    void afterConstruct(WorkExecutor executor, DataDrivenKafkaConsumer consumer){
        this.executor = executor;
        this.consumer = consumer;
    }

    public abstract void onMessage(ConsumerRecord<String, byte[]> msg) throws Exception;

    public void init(ConsumerRecord<String, byte[]> first) throws Exception {

    }

    public void destroy() throws Exception {

    }
}
