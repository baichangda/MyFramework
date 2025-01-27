package cn.bcd.dataProcess.parse.gb32960;

import cn.bcd.base.util.ExecutorUtil;
import cn.bcd.storage.mongo.gb32960.MongoUtil_gb32960;
import cn.bcd.storage.mongo.gb32960.SaveRawData;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaveHandler_gb32960 {
    public static ArrayBlockingQueue<SaveRawData> queue;
    static ExecutorService pool = Executors.newSingleThreadExecutor();

    static {
        SaveHandler_gb32960.init();
    }

    public static void init() {
        queue = new ArrayBlockingQueue<>(10000);
        pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            ExecutorUtil.loop(queue, 500, MongoUtil_gb32960::saveBatch_rawData, null);
        });
    }

    public static void put(SaveRawData data) throws InterruptedException {
        queue.put(data);
    }
}
