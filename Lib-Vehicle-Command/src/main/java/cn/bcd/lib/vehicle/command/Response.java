package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response<T, R> {
    public String vin;
    public PacketFlag flag;
    public ResponseStatus status;

    //以下属性可能为null、取决于车辆是否在线
    public String requestId;
    public byte[] content;

    @JsonIgnore
    public Command<T, R> command;

    public Response() {
    }

    public Response(String vin, PacketFlag flag, ResponseStatus status) {
        this.vin = vin;
        this.flag = flag;
        this.status = status;
    }

    public Response(Request<T, R> request, ResponseStatus status, byte[] content) {
        this.vin = request.vin;
        this.flag = request.flag;
        this.requestId = request.id;
        this.status = status;
        this.content = content;
    }

    @JsonIgnore
    public R getContentObj() {
        if (content == null) {
            return null;
        }
        return command.toResponse(content);
    }
}
