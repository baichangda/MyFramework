package cn.bcd.app.dataProcess.parse.v2025;

import cn.bcd.app.dataProcess.parse.SaveUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import cn.bcd.lib.storage.mongo.raw.RawData;
import io.netty.buffer.ByteBufUtil;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.EnumSet;

@Order(1)
@Component
public class SaveRawHandler implements DataHandler_v2025 {
    static EnumSet<PacketFlag> saveRawDataTypeSet = EnumSet.of(
            PacketFlag.vehicle_login_data,
            PacketFlag.vehicle_run_data,
            PacketFlag.vehicle_logout_data
    );

    @Override
    public void handle(String vin, Packet packet, Context_v2025 context) throws Exception {
        if (saveRawDataTypeSet.contains(packet.flag)) {
            String hexDump = ByteBufUtil.hexDump(context.rawData);
            Date collectTime = PacketUtil.getTime(context.rawData);
            RawData rawData = new RawData();
            rawData.setVin(vin);
            rawData.setCollectTime(collectTime);
            rawData.setType(packet.flag.type);
            rawData.setGwReceiveTime(context.gwInTime);
            rawData.setGwSendTime(context.gwOutTime);
            rawData.setParseReceiveTime(context.parseInTime);
            rawData.setHex(hexDump);
            SaveUtil.put(rawData);
        }
    }
}
