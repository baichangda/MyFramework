package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.parser.protocol.gb32960.v2016.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.VehicleRunData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;


public class VehicleData {
    //车辆运行数据
    public VehicleRunData vehicleRunData;

    public void init() {
        init_vehicleRunData();
    }

    public void init_vehicleRunData() {
        Packet packet = Packet.read(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(Const.sample_vehicleRunData)));
        vehicleRunData = (VehicleRunData) packet.data;
    }

}
