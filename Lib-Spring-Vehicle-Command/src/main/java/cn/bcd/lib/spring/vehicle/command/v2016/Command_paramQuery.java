package cn.bcd.lib.spring.vehicle.command.v2016;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.ParamQueryRequest;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.ParamQueryResponse;
import cn.bcd.lib.spring.vehicle.command.Command;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public class Command_paramQuery extends Command<ParamQueryRequest, ParamQueryResponse> {
    final Processor<ParamQueryRequest> processor_ParamQueryRequest = Parser.getProcessor(ParamQueryRequest.class);
    final Processor<ParamQueryResponse> processor_ParamQueryResponse = Parser.getProcessor(ParamQueryResponse.class);

    public Command_paramQuery(ParamQueryRequest request) {
        super(request, PacketFlag.param_query.type, ProtocolVersion.v_2016);
    }

    @Override
    public byte[] toRequestBytes() {
        ByteBuf buffer = Unpooled.buffer();
        processor_ParamQueryRequest.deProcess(buffer, request);
        return ByteBufUtil.getBytes(buffer);
    }

    @Override
    public ParamQueryResponse toResponse(byte[] content) {
        return processor_ParamQueryResponse.process(Unpooled.wrappedBuffer(content));
    }
}
