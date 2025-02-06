package cn.bcd.simulator.pressTest.tcp.gb32960;

import cn.bcd.parser.base.Parser;
import cn.bcd.parser.base.processor.Processor;
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
    final static String sampleHex = "232302FE4C534A4533363039364D53313430343935010141170608100A10010103010040000003520F2827811C012E2000000002010101594FDB4E2F4A0F3227100500073944E501DD620A0601090E1B01370E14010145010444070300021387000000000801010F282781006C00016C0E180E190E1A0E190E190E180E180E1A0E1B0E180E190E1A0E180E180E190E1A0E1A0E190E180E1A0E180E1A0E1A0E180E170E190E170E190E170E190E1B0E190E190E190E180E180E170E170E180E170E170E170E190E170E180E170E190E170E170E170E180E180E190E190E140E180E180E170E170E150E160E160E180E190E170E180E170E180E170E180E170E160E190E150E180E160E180E170E160E160E170E150E170E170E140E170E160E160E170E170E170E170E160E170E160E170E140E170E170E160E160E170E170E170E160E160E160E16090101000C454545444544444445444544F5";
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
