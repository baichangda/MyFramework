package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.util.PacketUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class Request<T, R> {
    public String id;
    public String vin;
    public PacketFlag flag;
    public byte[] content;
    public boolean waitVehicleResponse;
    public int timeout;

    @JsonIgnore
    public Command<T, R> command;
    @JsonIgnore
    public CommandCallback<T, R> callback;
    @JsonIgnore
    public ScheduledFuture<?> timeoutFuture;

    public static String toId(String vin, PacketFlag flag) {
        return vin + "," + flag.type;
    }

    public byte[] toPacketBytes() {
        int length = content.length;
        byte[] bytes = new byte[length + 25];
        bytes[0] = 0x23;
        bytes[1] = 0x23;
        bytes[2] = (byte) flag.toInteger();
        bytes[3] = (byte) 0xfe;
        System.arraycopy(vin.getBytes(), 0, bytes, 4, 17);
        bytes[21] = 1;
        bytes[22] = (byte) (length >> 8);
        bytes[23] = (byte) length;
        System.arraycopy(content, 0, bytes, 24, length);
        PacketUtil.fix_code(bytes);
        return bytes;
    }
}
