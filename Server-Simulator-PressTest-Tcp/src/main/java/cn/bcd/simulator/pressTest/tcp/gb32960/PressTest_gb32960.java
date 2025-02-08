package cn.bcd.simulator.pressTest.tcp.gb32960;

import cn.bcd.parser.base.Parser;
import cn.bcd.parser.base.processor.Processor;
import cn.bcd.parser.protocol.gb32960.Const;
import cn.bcd.parser.protocol.gb32960.data.Packet;
import cn.bcd.parser.protocol.gb32960.data.VehicleRunData;
import cn.bcd.simulator.pressTest.tcp.PressTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import picocli.CommandLine;

import java.util.Date;

@CommandLine.Command(name = "gb32960", mixinStandardHelpOptions = true)
public class PressTest_gb32960 extends PressTest<Packet> {
    final static String sampleHex = Const.sample_vehicleRunData;
    final static Processor<Packet> processor = Parser.getProcessor(Packet.class);

    @Override
    protected Packet initSample(String vin) {
        byte[] bytes = ByteBufUtil.decodeHexDump(sampleHex);
        Packet packet = processor.process(Unpooled.wrappedBuffer(bytes));
        packet.vin = vin;
        return packet;
    }

    @Override
    protected ByteBuf toByteBuf(Packet packet, long ts) {
        ByteBuf buffer = Unpooled.buffer();
        ((VehicleRunData) packet.data).collectTime = new Date(ts);
        processor.deProcess(buffer, packet);
        return buffer;
    }
}
