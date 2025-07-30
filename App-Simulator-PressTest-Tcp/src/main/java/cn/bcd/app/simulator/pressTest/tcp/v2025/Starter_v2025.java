package cn.bcd.app.simulator.pressTest.tcp.v2025;

import cn.bcd.app.simulator.pressTest.tcp.PressTest;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.VehicleRunData;
import cn.bcd.lib.parser.protocol.gb32960.v2025.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import picocli.CommandLine;

import java.util.Date;

@CommandLine.Command(name = "v2025", mixinStandardHelpOptions = true)
public class Starter_v2025 extends PressTest<Packet> {
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
