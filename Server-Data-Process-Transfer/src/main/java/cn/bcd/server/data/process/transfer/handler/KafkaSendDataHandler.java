package cn.bcd.server.data.process.transfer.handler;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.util.PacketUtil;
import cn.bcd.lib.storage.mongo.transfer.MongoUtil_transferData;
import cn.bcd.lib.storage.mongo.transfer.TransferData;
import cn.bcd.server.data.process.transfer.tcp.Client;
import cn.bcd.server.data.process.transfer.tcp.SendData;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Order(99)
@Component
public class KafkaSendDataHandler implements KafkaDataHandler {

    static final Logger logger = org.slf4j.LoggerFactory.getLogger(KafkaSendDataHandler.class);

    @Override
    public void handle(String vin, byte[] bytes, Context context) throws Exception {
        PacketFlag flag = PacketFlag.fromInteger(bytes[2]);
        if (flag == PacketFlag.vehicle_run_data) {
            long ts = PacketUtil.getTime(bytes).getTime();
            /**
             * 实时数据满足如下两种情况转换为补发数据类型
             * 1、报文时间超过补发阈值
             * 2、报文时间小于上一包报文时间
             */
            if (DateUtil.CacheMillisecond.current() - ts > Client.transferConfigData.reissueTimeThreshold * 1000 || ts < context.lastRunReportTime) {
                bytes[2] = (byte) PacketFlag.vehicle_supplement_data.type;
            }
            //更新最后上报时间
            if (ts > context.lastRunReportTime) {
                context.lastRunReportTime = ts;
            }
        }
        //发送到队列中
        Client.sendQueue.put(new SendData(bytes, () -> {
            // 保存转发记录
            TransferData transferData = new TransferData();
            transferData.setVin(vin);
            transferData.setType(PacketUtil.getPacketFlag(bytes).type);
            transferData.setVehicleModelCode(context.vehicleData.getVehicleModelCode());
            transferData.setPlatformCode(Client.transferConfigData.platCode);
            transferData.setHex(ByteBufUtil.hexDump(bytes));
            transferData.setCollectTime(PacketUtil.getTime(bytes));
            transferData.setGwInTime(context.gwInTime);
            transferData.setGwOutTime(context.gwOutTime);
            transferData.setParseInTime(context.parseInTime);
            transferData.setParseOutTime(context.parseOutTime);
            transferData.setTransferInTime(context.transferInTime);
            transferData.setTransferOutTime(new Date());
            MongoUtil_transferData.save_transferData(List.of(transferData));
        }, context.executor));
    }
}
