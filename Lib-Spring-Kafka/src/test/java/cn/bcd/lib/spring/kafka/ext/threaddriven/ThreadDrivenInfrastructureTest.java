package cn.bcd.lib.spring.kafka.ext.threaddriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static cn.bcd.lib.spring.kafka.ConsumerTestUtil.awaitClosed;

class ThreadDrivenInfrastructureTest {
    @Test
    void rejectedSubmissionDoesNotLeaveAnOutstandingMessage() {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer()) {
            consumer.close();
            awaitClosed(consumer);
            assertThrows(RejectedExecutionException.class, () -> consumer.submitRecord(
                    new ConsumerRecord<>("topic", 0, 0, "id", new byte[0])));
            assertEquals(0, consumer.blockingNum.sum());
            assertEquals(0, consumer.monitor_consumeCount.sum());
        }
    }

    @Test
    void schedulerCanCloseConsumerWithoutWaitingForItself() throws Exception {
        try (ThreadDrivenKafkaConsumer consumer = newConsumer()) {
            consumer.scheduledPool.submit(consumer::close).get(5, TimeUnit.SECONDS);
            consumer.close();
            awaitClosed(consumer);
            assertTrue(consumer.scheduledPool.isTerminated());
            for (var executor : consumer.workPool) {
                assertTrue(executor.isTerminated());
            }
        }
    }

    private static ThreadDrivenKafkaConsumer newConsumer() {
        return new ThreadDrivenKafkaConsumer("thread-infrastructure-test", 3, 1000, true, 0, 60,
                ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public void onMessage(ConsumerRecord<String, byte[]> record, SingleThreadEventExecutor executor) {
            }
        };
    }
}
