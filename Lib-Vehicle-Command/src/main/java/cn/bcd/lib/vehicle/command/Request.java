package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
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
}
