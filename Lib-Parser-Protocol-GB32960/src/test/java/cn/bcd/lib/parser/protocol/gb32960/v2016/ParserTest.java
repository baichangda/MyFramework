package cn.bcd.lib.parser.protocol.gb32960.v2016;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.anno.data.NumVal_double;
import cn.bcd.lib.parser.base.util.PerformanceUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.VehicleRunData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ParserTest {
    static Logger logger = LoggerFactory.getLogger(ParserTest.class);


    @Test
    public void test() {
        Parser.withDefaultLogCollector_parse();
        Parser.withDefaultLogCollector_deParse();
        Parser.enableGenerateClassFile();
        Parser.enablePrintBuildLog();
        String data = Const.sample_vehicleRunData;
        data = data.replaceAll(" ", "");
        byte[] bytes = ByteBufUtil.decodeHexDump(data);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        Packet packet = Packet.read(byteBuf);
        ((VehicleRunData)packet.data).vehicleBaseData.totalMileage=new NumVal_double(2,0);
        ByteBuf dest = Unpooled.buffer();
        packet.write(dest);
        logger.info(data.toUpperCase());
        logger.info(ByteBufUtil.hexDump(dest).toUpperCase());
        assert data.equalsIgnoreCase(ByteBufUtil.hexDump(dest));
    }

    @Test
    public void test_performance() {
        Parser.disableByteBufCheck();
        Parser.enablePrintBuildLog();
        Parser.enableGenerateClassFile();
        String data = Const.sample_vehicleRunData;
        int threadNum = 1;
        logger.info("param threadNum[{}]", threadNum);
        int num = Integer.MAX_VALUE;
        PerformanceUtil.testPerformance(ByteBufUtil.decodeHexDump(data), threadNum, num, Packet::read, (buf, instance) -> instance.write(buf), true);
    }

    @Test
    public void test_performance_json() throws IOException {
        ByteBuf buffer = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(Const.sample_vehicleRunData));
        Packet read = Packet.read(buffer);
        byte[] data = JsonUtil.toJsonAsBytes(read);
        LongAdder count = new LongAdder();
        try (ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor()) {
            pool.scheduleAtFixedRate(() -> logger.info("perThreadSpeed/s:{}", count.sumThenReset()), 3, 3, TimeUnit.SECONDS);
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                Packet packet = JsonUtil.OBJECT_MAPPER.readValue(data, Packet.class);
                count.increment();
            }
        }
    }
}
