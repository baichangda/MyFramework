package cn.bcd.lib.base.executor.consume;

import cn.bcd.lib.base.util.DateUtil;

public abstract class ConsumeEntity<T> {
    public final String id;
    /**
     * 创建时间(秒)
     */
    public final long createTime;

    public ConsumeExecutor<T> executor;

    /**
     * 最后一条信息时间(秒)
     */
    public long lastMessageTime;


    public ConsumeEntity(String id) {
        this.id = id;
        this.createTime = DateUtil.CacheSecond.current();
    }


    void onMessageInternal(T t) throws Exception {
        lastMessageTime = DateUtil.CacheSecond.current();
        onMessage(t);
    }

    public abstract void onMessage(T t) throws Exception;

    public void init(T t) throws Exception {

    }

    public void destroy() throws Exception {

    }
}
