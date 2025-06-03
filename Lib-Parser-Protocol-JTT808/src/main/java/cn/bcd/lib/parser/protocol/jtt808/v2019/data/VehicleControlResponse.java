package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import io.netty.buffer.ByteBuf;

public class VehicleControlResponse implements PacketBody {
    public int sn;
    public Position position;

    public static VehicleControlResponse read(ByteBuf data, int len) {
        VehicleControlResponse vehicleControlResponse = new VehicleControlResponse();
        vehicleControlResponse.sn = data.readUnsignedShort();
        vehicleControlResponse.position = Position.read(data, len - 2);
        return vehicleControlResponse;
    }

    public void write(ByteBuf data){
        data.writeShort(sn);
        position.write(data);
    }
}
