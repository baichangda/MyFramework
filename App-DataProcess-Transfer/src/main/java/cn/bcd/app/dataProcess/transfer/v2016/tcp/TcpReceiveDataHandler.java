package cn.bcd.app.dataProcess.transfer.v2016.tcp;

import cn.bcd.app.dataProcess.transfer.v2016.SaveUtil;
import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.base.util.HexUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import cn.bcd.lib.spring.storage.mongo.transfer.TransferResponseData;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TcpReceiveDataHandler implements TcpDataHandler {
    static final Logger logger = org.slf4j.LoggerFactory.getLogger(TcpReceiveDataHandler.class);

    @Override
    public void handle(String vin, byte[] bytes) throws Exception {
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
            SaveUtil.put(responseData);
            if (Const.logEnable) {
                logger.info("transfer response data vin[{}] collectTime[{}] type[{}] replyFlag[{}] hex[{}]",
                        vin,
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(time),
                        HexUtil.hexDump((byte) type),
                        replyFlag,
                        hex);
            }
        }
    }
}
