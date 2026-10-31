package cn.bcd.lib.spring.kafka;

import cn.bcd.lib.spring.kafka.ext.datadriven.DataDrivenKafkaConsumer;
import cn.bcd.lib.spring.kafka.ext.threaddriven.ThreadDrivenKafkaConsumer;
import io.netty.util.concurrent.SingleThreadEventExecutor;

import java.util.concurrent.TimeUnit;

public final class ConsumerTestUtil {
    private ConsumerTestUtil() {
    }

    public static void awaitClosed(DataDrivenKafkaConsumer consumer) {
        awaitTerminated(consumer.workExecutors);
    }

    public static void awaitClosed(ThreadDrivenKafkaConsumer consumer) {
        awaitTerminated(consumer.workPool);
    }

    private static void awaitTerminated(SingleThreadEventExecutor[] executors) {
        try {
            for (var executor : executors) {
                executor.terminationFuture().get(5, TimeUnit.SECONDS);
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError("Timed out or failed waiting for consumer shutdown", exception);
        }
    }
}
