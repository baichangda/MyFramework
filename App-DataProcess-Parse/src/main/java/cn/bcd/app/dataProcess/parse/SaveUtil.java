package cn.bcd.app.dataProcess.parse;

import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.spring.storage.mongo.raw.MongoUtil_gb32960;
import cn.bcd.lib.spring.storage.mongo.raw.RawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class SaveUtil {
    public final static int queueSize = 100000;
    public static ArrayBlockingQueue<RawData> queue;
    static ExecutorService pool = Executors.newSingleThreadExecutor();
    public final static LongAdder saveCount = new LongAdder();

    static Logger logger = LoggerFactory.getLogger(SaveUtil.class);

    static {
        SaveUtil.init();
    }

    public static void init() {
        queue = new ArrayBlockingQueue<>(queueSize);
        pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            ExecutorUtil.loop(queue, 1000, list -> {
                try {
                    MongoUtil_gb32960.save_rawData(list);
                    saveCount.add(list.size());
                } catch (Exception ex) {
                    logger.error("error", ex);
                }
            }, null);
        });
    }

    public static void put(RawData data) {
        try {
            queue.put(data);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
