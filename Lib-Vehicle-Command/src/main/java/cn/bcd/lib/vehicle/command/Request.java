package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.concurrent.ScheduledFuture;

@Data
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
        return PacketUtil.build_bytes_packetData(vin, flag, 0xFE, content);
    }
}
