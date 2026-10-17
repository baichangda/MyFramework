package cn.bcd.lib.spring.vehicle.command;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

@Data
public class Request<T, R> {
    private static final int PACKET_HEADER_LENGTH = 24;
    private static final int COMMAND_TIME_LENGTH = 6;

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

    public static <T, R> Request<T, R> create(String vin, Command<T, R> command, int timeout,
                                             CommandCallback<T, R> callback, boolean waitVehicleResponse) {
        Request<T, R> request = new Request<>();
        request.content = command.toRequestBytes();
        request.id = toId(vin, command.flag, request.content);
        request.vin = vin;
        request.flag = command.flag;
        request.version = command.version;
        request.timeout = timeout;
        request.waitVehicleResponse = waitVehicleResponse;
        request.command = command;
        request.callback = callback;
        return request;
    }

    /** 使用数据单元开头的六字节时间生成秒级请求 ID。 */
    public static String toId(String vin, int flag, byte[] content) {
        return toId(vin, flag, content, 0);
    }

    /** 完整报文中的数据单元从 24 字节报文头之后开始，两个协议版本相同。 */
    public static String toIdFromPacket(String vin, int flag, byte[] packetBytes) {
        return toId(vin, flag, packetBytes, PACKET_HEADER_LENGTH);
    }

    private static String toId(String vin, int flag, byte[] bytes, int timeOffset) {
        if (bytes.length - timeOffset < COMMAND_TIME_LENGTH) {
            throw new IllegalArgumentException("Command data must contain a six-byte time");
        }
        long timestamp = LocalDateTime.of(
                2000 + (bytes[timeOffset] & 0xff),
                bytes[timeOffset + 1] & 0xff,
                bytes[timeOffset + 2] & 0xff,
                bytes[timeOffset + 3] & 0xff,
                bytes[timeOffset + 4] & 0xff,
                bytes[timeOffset + 5] & 0xff).toEpochSecond(DateUtil.ZONE_OFFSET);
        return timestamp + "," + vin + "," + flag;
    }

    public byte[] toPacketBytes() {
        if (version == ProtocolVersion.v_2016) {
            return cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.build_bytes_packetData(vin, cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag.fromInteger(flag), 0xFE, content);
        } else {
            return cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.build_bytes_packetData(vin, cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag.fromInteger(flag), 0xFE, content);
        }
    }
}
