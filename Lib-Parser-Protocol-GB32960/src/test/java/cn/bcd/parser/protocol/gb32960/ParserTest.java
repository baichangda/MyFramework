package cn.bcd.parser.protocol.gb32960;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.util.PerformanceUtil;
import cn.bcd.lib.parser.protocol.gb32960.Const;
import cn.bcd.lib.parser.protocol.gb32960.data.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
//        ((VehicleRunData)packet.data).vehicleBaseData.vehicleSpeed=new NumVal_float(0,(short)33.3);
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
        int num = 2000000000;
        PerformanceUtil.testPerformance(ByteBufUtil.decodeHexDump(data), threadNum, num, Packet::read, (buf, instance) -> instance.write(buf), true);
    }
}
