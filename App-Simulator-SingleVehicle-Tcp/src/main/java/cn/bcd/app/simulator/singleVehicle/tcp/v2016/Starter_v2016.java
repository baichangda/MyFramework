package cn.bcd.app.simulator.singleVehicle.tcp.v2016;

import cn.bcd.app.simulator.singleVehicle.tcp.HttpServer;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import picocli.CommandLine;

@CommandLine.Command(name = "v2016", mixinStandardHelpOptions = true)
public class Starter_v2016 extends HttpServer {
    public Starter_v2016() {
        super(VehicleData_v2016::new);
    }

    @Override
    public String hexToJson(byte[] hex) {
        Packet packet = Packet.read(Unpooled.wrappedBuffer(hex));
        return JsonUtil.toJson(packet);
    }

    @Override
    public String jsonToHex(String json) throws Exception {
        Packet packet = JsonUtil.OBJECT_MAPPER.readValue(json, Packet.class);
        ByteBuf buffer = Unpooled.buffer();
        packet.write(buffer);
        return ByteBufUtil.hexDump(buffer);
    }
}
