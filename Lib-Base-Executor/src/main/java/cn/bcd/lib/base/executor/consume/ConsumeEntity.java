package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.util.DateUtil;

/**
 * 按业务 ID 管理的有状态消息消费者。
 * <p>
 * 一个实体固定绑定到一个 {@link ConsumeExecutor}，其初始化、消息处理和销毁回调
 * 均在绑定执行器的单线程内串行执行。
 * </p>
 *
 * @param <T> 消息类型
 */
public abstract class ConsumeEntity<T> {

    /**
     * 实体业务 ID。
     */
    public final String id;

    /**
     * 实体创建时间，单位毫秒。
     */
    public final long createTime;

    /**
     * 实体绑定的执行器，由 {@link ConsumeExecutorGroup} 在初始化前设置。
     */
    public ConsumeExecutor<T> executor;

    /**
     * 最后一条消息的处理时间，单位秒，用于过期扫描。
     */
    public long lastMessageTime;

    /**
     * @param id 实体业务 ID
     */
    public ConsumeEntity(String id) {
        this.id = id;
        this.createTime = DateUtil.CacheMillisecond.current();
    }

    /**
     * 更新活跃时间后调用业务消息处理方法。
     */
    void onMessageInternal(T t) throws Exception {
        lastMessageTime = DateUtil.CacheSecond.current();
        onMessage(t);
    }

    /**
     * 串行处理一条消息。
     *
     * @param t 消息
     */
    public abstract void onMessage(T t) throws Exception;

    /**
     * 实体首次创建时调用，先于第一条消息的 {@link #onMessage(Object)} 调用。
     * 初始化失败时框架会调用 {@link #destroy()} 清理已创建的部分资源。
     *
     * @param first 触发实体创建的第一条消息
     */
    public void init(T first) throws Exception {
    }

    /**
     * 实体被删除、过期或执行器组关闭时调用。
     */
    public void destroy() throws Exception {
    }
}
