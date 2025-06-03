package cn.bcd.lib.parser.protocol.gb32960.v2025.data;


import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.anno.data.DefaultNumValChecker;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.base.anno.data.NumVal_byte;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
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
        public NumVal_byte level;

        public static Alarm from(byte[] data) {
            Alarm alarm = new Alarm();
            alarm.level = DefaultNumValChecker.instance.getNumVal_byte(NumType.uint8, data[0]);
            return alarm;
        }

        public byte[] to() {
            byte[] data = new byte[1];
            data[0] = DefaultNumValChecker.instance.getVal(NumType.uint8, level);
            return data;
        }
    }
}
