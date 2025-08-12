package cn.bcd.app.dataProcess.parse;

import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.storage.mongo.raw.MongoUtil_gb32960;
import cn.bcd.lib.storage.mongo.raw.RawData;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class SaveUtil {
    public static ArrayBlockingQueue<RawData> queue;
    static ExecutorService pool = Executors.newSingleThreadExecutor();
    public final static LongAdder saveCount = new LongAdder();
    static {
        SaveUtil.init();
    }

    public static void init() {
        queue = new ArrayBlockingQueue<>(10000);
        pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            ExecutorUtil.loop(queue, 500, list -> {
                MongoUtil_gb32960.save_rawData(list);
                saveCount.add(list.size());
            }, null);
        });
    }

    public static void put(RawData data) throws InterruptedException {
        queue.put(data);
    }
}
