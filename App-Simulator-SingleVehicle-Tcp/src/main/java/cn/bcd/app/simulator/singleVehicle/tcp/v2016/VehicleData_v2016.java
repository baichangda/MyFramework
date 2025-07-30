package cn.bcd.app.simulator.singleVehicle.tcp.v2016;

import cn.bcd.app.simulator.singleVehicle.tcp.VehicleData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.VehicleRunData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.Date;


public class VehicleData_v2016 extends VehicleData {
    //车辆运行数据
    public VehicleRunData vehicleRunData;

    public VehicleData_v2016(String vin) {
        super(vin);
    }

    public void init_vehicleRunData() {
        Packet packet = Packet.read(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(Const.sample_vehicleRunData)));
        vehicleRunData = (VehicleRunData) packet.data;
    }

    @Override
    public byte[] onSend_vehicleRunDataToBytes() {
        vehicleRunData.collectTime = new Date();
        return PacketUtil.build_bytes_packetData(vin, PacketFlag.vehicle_run_data, (short) 0xfe, vehicleRunData);
    }
}
