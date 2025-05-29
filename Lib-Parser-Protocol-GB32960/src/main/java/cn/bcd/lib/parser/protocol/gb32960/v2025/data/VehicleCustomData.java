package cn.bcd.lib.parser.protocol.gb32960.v2025.data;


import io.netty.buffer.ByteBuf;

/**
 * 自定义数据
 */
public class VehicleCustomData {
    public short id;
    public int len;
    public byte[] data;

    public static VehicleCustomData read(short id, ByteBuf byteBuf) {
        VehicleCustomData vehicleCustomData = new VehicleCustomData();
        vehicleCustomData.id = id;
        vehicleCustomData.len = byteBuf.readUnsignedShort();
        byte[] bytes = new byte[vehicleCustomData.len];
        byteBuf.readBytes(bytes);
        vehicleCustomData.data = bytes;
        return vehicleCustomData;
    }

    public void write(ByteBuf byteBuf, VehicleCustomData vehicleCustomData) {
        byteBuf.writeByte(vehicleCustomData.id);
        byteBuf.writeShort(vehicleCustomData.len);
        byteBuf.writeBytes(vehicleCustomData.data);
    }
}
