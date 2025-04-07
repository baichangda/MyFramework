package cn.bcd.server.data.process.transfer.handler;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.base.util.HexUtil;
import cn.bcd.lib.parser.protocol.gb32960.util.PacketUtil;
import cn.bcd.lib.storage.mongo.transfer.MongoUtil_transferData;
import cn.bcd.lib.storage.mongo.transfer.TransferResponseData;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class TcpReceiveDataHandler implements TcpDataHandler {
    static final Logger logger = org.slf4j.LoggerFactory.getLogger(TcpReceiveDataHandler.class);

    @Override
    public void handle(String vin, byte[] bytes, Context context) throws Exception {
        //保存转发记录结果
        if (bytes != null) {
            int type = PacketUtil.getPacketFlag(bytes).type;
            Date time = PacketUtil.getTime(bytes);
            int replyFlag = bytes[3];
            String hex = ByteBufUtil.hexDump(bytes);
            TransferResponseData responseData = new TransferResponseData();
            responseData.setVin(vin);
            responseData.setCollectTime(time);
            responseData.setType(type);
            responseData.setReplyFlag(replyFlag);
            responseData.setHex(hex);
            MongoUtil_transferData.save_transferResponseData(List.of(responseData));
            logger.info("transfer response data vin[{}] collectTime[{}] type[{}] replyFlag[{}] hex[{}]",
                    vin,
                    DateZoneUtil.dateToString_second(time),
                    HexUtil.hexDump((byte) type),
                    replyFlag,
                    hex);
        }
    }
}
