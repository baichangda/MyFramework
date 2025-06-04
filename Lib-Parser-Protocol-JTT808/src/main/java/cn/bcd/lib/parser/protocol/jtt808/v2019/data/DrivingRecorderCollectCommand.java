package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import io.netty.buffer.ByteBuf;

public class DrivingRecorderCollectCommand implements PacketBody {
    //命令字
    public byte flag;
    //数据块
    public byte[] content;

    public static DrivingRecorderCollectCommand read(ByteBuf data, int len) {
        DrivingRecorderCollectCommand drivingRecorderCollectCommand = new DrivingRecorderCollectCommand();
        drivingRecorderCollectCommand.flag = data.readByte();
        byte[] content = new byte[len - 1];
        data.readBytes(content);
        drivingRecorderCollectCommand.content = content;
        return drivingRecorderCollectCommand;
    }

    public void write(ByteBuf data){
        data.writeByte(flag);
        data.writeBytes(content);
    }
}
