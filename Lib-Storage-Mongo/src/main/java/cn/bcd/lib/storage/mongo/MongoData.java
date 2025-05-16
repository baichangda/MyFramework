package cn.bcd.lib.storage.mongo;

public interface MongoData {
    /**
     * 由于有多个单机mongo组成集群、所以需要一个分区id用于分到指定的mongo中
     * 获取用于分区的id、用于定位到指定mongo
     *
     * @return
     */
    String getPartitionId();

    /**
     *
     * 数据会以key、value方式存储、所有只能通过主键 精确查询/范围查询
     * 为了主键可以范围查询、需要将查询条件设计在主键中
     * 获取记录id
     *
     * @return
     */
    String getId();
}
