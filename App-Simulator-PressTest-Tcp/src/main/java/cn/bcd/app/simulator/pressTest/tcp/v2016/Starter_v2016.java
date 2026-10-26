package cn.bcd.app.simulator.pressTest.tcp.v2016;

import cn.bcd.app.simulator.pressTest.tcp.PressTest;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2016.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.VehicleRunData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import picocli.CommandLine;

import java.util.Date;

@CommandLine.Command(name = "v2016", mixinStandardHelpOptions = true)
public class Starter_v2016 extends PressTest<Packet> {
    final static String sampleHex = Const.sample_vehicleRunData;
    final static Processor<Packet> processor = Parser.getProcessor(Packet.class);

    @Override
    protected Packet initSample(String vin) {
        ByteBuf buffer = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(sampleHex));
        Packet packet = processor.process(buffer);
        packet.vin = vin;
        return packet;
    }

    @Override
    protected ByteBuf toByteBuf(Packet packet, long ts) {
        ((VehicleRunData) packet.data).collectTime = new Date(ts);
        ByteBuf buffer = Unpooled.buffer();
        processor.deProcess(buffer, packet);
        PacketUtil.fix_code(buffer);
        return buffer;
    }
}
