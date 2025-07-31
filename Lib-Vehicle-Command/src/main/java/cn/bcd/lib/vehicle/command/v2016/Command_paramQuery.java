package cn.bcd.lib.vehicle.command.v2016;

import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.ParamQueryRequest;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.ParamQueryResponse;
import cn.bcd.lib.vehicle.command.Command;

public class Command_paramQuery extends Command<ParamQueryRequest, ParamQueryResponse> {
    public Command_paramQuery(ParamQueryRequest request) {
        super(request, PacketFlag.param_query.type, ProtocolVersion.v_2016);
    }

    @Override
    public byte[] toRequestBytes() {
        return new byte[0];
    }

    @Override
    public ParamQueryResponse toResponse(byte[] content) {
        return null;
    }
}
