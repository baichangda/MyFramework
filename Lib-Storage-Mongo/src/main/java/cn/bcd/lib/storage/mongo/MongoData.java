package cn.bcd.lib.storage.mongo;

public interface MongoData {
    /**
     * 获取记录id、用于定位到指定mongo
     *
     * @return
     */
    String getPartitionId();

    /**
     * 获取记录rowKey
     *
     * @return
     */
    String getId();
}
