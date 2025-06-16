package cn.bcd.lib.parser.protocol.gb32960.v2025.data;


import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumType;
import io.netty.buffer.ByteBuf;

import java.util.Date;

/**
 * 车载终端控制
 */
public class TerminalControlCommand implements PacketData {
    //时间
    public Date time;
    //命令id
    public byte id;
    //命令参数
    public byte[] data;

    public static TerminalControlCommand read(int contentLength, ByteBuf byteBuf) {
        int dataLen = contentLength - 7;
        TerminalControlCommand terminalControlCommand = new TerminalControlCommand();
        long ts = FieldBuilder__F_date_bytes_6.read(byteBuf, DateZoneUtil.ZONE_ID, 2000);
        terminalControlCommand.time = new Date(ts);
        terminalControlCommand.id = byteBuf.readByte();
        if (dataLen > 0) {
            byte[] temp = new byte[dataLen];
            byteBuf.readBytes(temp);
            terminalControlCommand.data = temp;
        }
        return terminalControlCommand;
    }

    public void write(ByteBuf byteBuf) {
        FieldBuilder__F_date_bytes_6.write(byteBuf, time.getTime(), DateZoneUtil.ZONE_ID, 2000);
        byteBuf.writeByte(id);
        if (data != null) {
            byteBuf.writeBytes(data);
        }
    }

    public static class Alarm {
        public byte level;
        public byte level__type;

        public static Alarm from(byte[] data) {
            Alarm alarm = new Alarm();
            byte b = data[0];
            byte type = DefaultNumValGetter.instance.getType(NumType.uint8, b);
            alarm.level__type = type;
            if (type == 0) {
                alarm.level = b;
            }
            return alarm;
        }

        public byte[] to() {
            byte[] data = new byte[1];
            if (level__type == 0) {
                data[0] = level;
            } else {
                data[0] = (byte) DefaultNumValGetter.instance.getVal_int(NumType.uint8, level__type);
            }
            return data;
        }
    }
}
