package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.concurrent.ScheduledFuture;

@Data
public class Request<T, R> {
    public String id;
    public String vin;
    public int flag;
    public byte[] content;
    public boolean waitVehicleResponse;
    public int timeout;
    public ProtocolVersion version;

    @JsonIgnore
    public Command<T, R> command;
    @JsonIgnore
    public CommandCallback<T, R> callback;
    @JsonIgnore
    public ScheduledFuture<?> timeoutFuture;

    public static String toId(String vin, int flag) {
        return vin + "," + flag;
    }

    public byte[] toPacketBytes() {
        if (version == ProtocolVersion.v_2016) {
            return cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.build_bytes_packetData(vin, cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag.fromInteger(flag), 0xFE, content);
        } else {
            return cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.build_bytes_packetData(vin, cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag.fromInteger(flag), 0xFE, content);
        }
    }
}
