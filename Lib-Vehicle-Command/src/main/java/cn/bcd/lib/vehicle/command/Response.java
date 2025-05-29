package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class Response<T, R> {
    public String vin;
    public PacketFlag flag;
    public ResponseStatus status;

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
     * @param packetBytes 响应报文、可能为null
     */
    public Response(String vin, PacketFlag flag, ResponseStatus status, byte[] packetBytes) {
        this.vin = vin;
        this.flag = flag;
        this.status = status;
        if (packetBytes != null) {
            this.replyFlag = PacketUtil.getReplyFlag(packetBytes);
            this.content = PacketUtil.getPacketData_bytes(packetBytes);
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
