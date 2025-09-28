package cn.bcd.app.dataProcess.transfer.v2016;

import cn.bcd.lib.base.util.ExecutorUtil;
import cn.bcd.lib.storage.mongo.transfer.MongoUtil_transferData;
import cn.bcd.lib.storage.mongo.transfer.TransferData;
import cn.bcd.lib.storage.mongo.transfer.TransferResponseData;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class SaveUtil {
    public final static int queueSize = 100000;
    public static ArrayBlockingQueue<TransferData> queue_transfer;
    public static ArrayBlockingQueue<TransferResponseData> queue_transferResponse;
    static ExecutorService pool_transfer = Executors.newSingleThreadExecutor();
    static ExecutorService pool_transferResponse = Executors.newSingleThreadExecutor();
    public final static LongAdder saveCount_transfer = new LongAdder();
    public final static LongAdder saveCount_transferResponse = new LongAdder();

    static {
        SaveUtil.init();
    }

    public static void init() {
        queue_transfer = new ArrayBlockingQueue<>(queueSize);
        pool_transfer = Executors.newSingleThreadExecutor();
        pool_transfer.execute(() -> {
            ExecutorUtil.loop(queue_transfer, 1000, list -> {
                MongoUtil_transferData.save_transferData(list);
                saveCount_transfer.add(list.size());
            }, null);
        });

        queue_transferResponse = new ArrayBlockingQueue<>(queueSize);
        pool_transferResponse = Executors.newSingleThreadExecutor();
        pool_transferResponse.execute(() -> {
            ExecutorUtil.loop(queue_transferResponse, 1000, list -> {
                MongoUtil_transferData.save_transferResponseData(list);
                saveCount_transferResponse.add(list.size());
            }, null);
        });
    }

    public static void put(TransferData data) {
        try {
            queue_transfer.put(data);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public static void put(TransferResponseData data) {
        try {
            queue_transferResponse.put(data);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
