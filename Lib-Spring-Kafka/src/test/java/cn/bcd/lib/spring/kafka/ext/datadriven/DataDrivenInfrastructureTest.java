package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.spring.kafka.ext.ConsumerParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DataDrivenInfrastructureTest {
    @Test
    void rejectedSubmissionDoesNotLeaveAnOutstandingMessage() {
        try (DataDrivenKafkaConsumer consumer = newConsumer()) {
            consumer.close();
            assertThrows(RejectedExecutionException.class, () -> consumer.submitRecord(
                    new ConsumerRecord<>("topic", 0, 0, "id", new byte[0])));
            assertEquals(0, consumer.blockingNum.sum());
            assertEquals(0, consumer.monitor_consumeCount.sum());
        }
    }

    @Test
    void schedulerCanCloseConsumerWithoutWaitingForItself() throws Exception {
        try (DataDrivenKafkaConsumer consumer = newConsumer()) {
            consumer.scheduledPool.submit(consumer::close).get(5, TimeUnit.SECONDS);
            consumer.close();
            assertTrue(consumer.scheduledPool.isTerminated());
            for (var executor : consumer.workExecutors) {
                assertTrue(executor.isTerminated());
            }
        }
    }

    private static DataDrivenKafkaConsumer newConsumer() {
        return new DataDrivenKafkaConsumer("data-infrastructure-test", 3, 1000, true, 0, null, 60,
                ConsumerParam.get_singleConsumer("topic")) {
            @Override
            public WorkHandler newHandler(String id, ConsumerRecord<String, byte[]> first) {
                return new WorkHandler(id) {
                    @Override
                    public void onMessage(ConsumerRecord<String, byte[]> record) {
                    }
                };
            }
        };
    }
}
