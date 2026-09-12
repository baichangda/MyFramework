package cn.bcd.app.dp.parse.v2025;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2025.Const;
import io.netty.buffer.ByteBufUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkHandlerV2025Test {

    @Test
    void exposesRawMessageToDataHandlers() throws Exception {
        byte[] message = ByteBufUtil.decodeHexDump(Const.sample_vehicleRunData);
        byte[] value = DateUtil.prependDatesToBytes(message, new Date(1), new Date(2));
        AtomicReference<byte[]> handledRawData = new AtomicReference<>();
        DataHandler_v2025 handler = (vin, packet, context) -> {
            assertNotNull(packet);
            handledRawData.set(context.rawData);
        };
        WorkHandler_v2025 workHandler = new WorkHandler_v2025(
                "TEST0000000000001", List.of(handler));

        workHandler.onMessage(new ConsumerRecord<>(
                "gw-parse", 0, 0L, "TEST0000000000001", value));

        assertArrayEquals(message, handledRawData.get());
        assertArrayEquals(message, workHandler.context.rawData);
    }
}
