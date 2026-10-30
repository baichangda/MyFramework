package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.util.DateUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * 由 DataDrivenKafkaConsumer 管理时，构造及生命周期回调都在所属 executor 中执行，包括
 * {@link #WorkHandler(String)}
 * {@link #init(ConsumerRecord)}
 * {@link #destroy()}
 * {@link #onMessage(ConsumerRecord)}
 * 异步操作的结果应交回 executor 后再修改 Handler 状态，不要在 executor 内等待自己的排队任务。
 * 同一 executor 上可能还有其他 ID 的 Handler，长期阻塞会同时拖慢这些 ID。
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
     * 最后一次开始处理消息的本地时间（秒），由工作线程更新；用于空闲过期检查。
     * 不代表 Kafka 消息时间戳，也不代表外部异步操作已经完成。
     */
    public long lastMessageTime;

    /**
     * 构造时尚未绑定 executor/consumer，子类需要它们时应在 init 中访问。
     *
     * @param id 业务 ID，可能为 null
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

    /**
     * 在所属工作执行器内处理消息。异常会被框架记录，当前消息不会自动重试。
     * 自动计数模式在本方法返回或抛异常后释放计数；手动模式应由业务在异步成功或失败后释放一次。
     * 本方法返回不代表外部异步操作已经完成。
     */
    public abstract void onMessage(ConsumerRecord<String, byte[]> msg) throws Exception;

    /**
     * 绑定 executor/consumer 后调用，成功后才将 Handler 注册到 Map。
     * first 随后仍会交给 onMessage；初始化失败时不缓存实例，下一条消息会重新创建。
     * 失败实例不会自动调用 destroy，初始化途中取得的资源应由子类在失败路径自行释放。
     */
    public void init(ConsumerRecord<String, byte[]> first) throws Exception {

    }

    /**
     * 手动移除、空闲过期或消费者关闭时，在所属执行器内释放资源。
     * 框架不等待外部异步操作完成，业务应自行取消或协调，避免销毁后继续访问资源。
     * 抛出的 Exception 会被记录，不阻止其他 Handler 的销毁。
     */
    public void destroy() throws Exception {

    }
}
