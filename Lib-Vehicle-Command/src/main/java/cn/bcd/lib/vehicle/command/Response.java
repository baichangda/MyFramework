package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class Response<T, R> {
    public String vin;
    public int flag;
    public ResponseStatus status;
    public ProtocolVersion version;

    /**
     * 以下属性可能为null或默认值、取决于是否接收到车辆的响应报文
     * 通过{@link #content}==null判断是否接收到车辆的响应报文
     */
    public int replyFlag;
    public byte[] content;

    @JsonIgnore
    public Command<T, R> command;

    public Response() {
    }

    /**
     * @param vin
     * @param flag
     * @param status
     * @param version
     * @param packetBytes 响应报文、可能为null
     */
    public Response(String vin, int flag, ResponseStatus status, ProtocolVersion version, byte[] packetBytes) {
        this.vin = vin;
        this.flag = flag;
        this.status = status;
        if (packetBytes != null) {
            if (version == ProtocolVersion.v_2016) {
                this.replyFlag = cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.getReplyFlag(packetBytes);
                this.content = cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.getPacketData_bytes(packetBytes);
            } else {
                this.replyFlag = cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.getReplyFlag(packetBytes);
                this.content = cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.getPacketData_bytes(packetBytes);
            }
        }
    }

    @JsonIgnore
    public R getContentObj() {
        if (content == null) {
            return null;
        }
        return command.toResponse(content);
    }
}
