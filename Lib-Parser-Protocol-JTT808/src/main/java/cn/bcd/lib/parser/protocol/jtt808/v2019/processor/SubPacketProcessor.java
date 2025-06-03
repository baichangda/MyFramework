package cn.bcd.lib.parser.protocol.jtt808.v2019.processor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.jtt808.v2019.data.PacketHeader;
import cn.bcd.lib.parser.protocol.jtt808.v2019.data.SubPacket;
import io.netty.buffer.ByteBuf;

public class SubPacketProcessor implements Processor<SubPacket> {
    @Override
    public SubPacket process(ByteBuf data, ProcessContext<?> processContext) {
        PacketHeader packetHeader = (PacketHeader) processContext.instance;
        if (packetHeader.subPacketFlag == 0) {
            return null;
        } else {
            SubPacket subPacket = new SubPacket();
            subPacket.total = data.readUnsignedShort();
            subPacket.no = data.readUnsignedShort();
            return subPacket;
        }
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext<?> processContext, SubPacket instance) {
        PacketHeader packetHeader = (PacketHeader) processContext.instance;
        if (packetHeader.subPacketFlag == 1) {
            if (instance == null) {
                throw BaseException.get("subPacketFlag[1] but subPacket is null");
            }
            data.writeShort(instance.total);
            data.writeShort(instance.no);
        }
    }
}
