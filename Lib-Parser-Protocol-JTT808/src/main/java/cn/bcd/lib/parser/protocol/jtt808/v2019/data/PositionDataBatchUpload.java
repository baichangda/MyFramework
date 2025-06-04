package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import io.netty.buffer.ByteBuf;

public class PositionDataBatchUpload implements PacketBody {
    //数据项个数
    public int num;
    //位置数据类型
    public byte type;
    //位置汇报数据项
    public PositionReportItem[] items;

    public static PositionDataBatchUpload read(ByteBuf data) {
        PositionDataBatchUpload positionDataBatchUpload = new PositionDataBatchUpload();
        int num = data.readUnsignedShort();
        positionDataBatchUpload.num = num;
        positionDataBatchUpload.type = data.readByte();
        PositionReportItem[] items = new PositionReportItem[num];
        positionDataBatchUpload.items = items;
        for (int i = 0; i < num; i++) {
            items[i] = PositionReportItem.read(data);
        }
        return positionDataBatchUpload;
    }

    public void write(ByteBuf data) {
        data.writeShort(num);
        data.writeByte(type);
        for (PositionReportItem item : items) {
            item.write(data);
        }
    }
}
