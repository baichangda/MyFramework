package cn.bcd.app.simulator.singleVehicle.tcp.v2025;

import cn.bcd.app.simulator.singleVehicle.tcp.VehicleData;
import cn.bcd.lib.parser.protocol.gb32960.v2025.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.VehicleRunData;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.Date;


public class VehicleData_v2025 extends VehicleData {
    //车辆运行数据
    public VehicleRunData vehicleRunData;

    public VehicleData_v2025(String vin) {
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
