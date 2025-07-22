package cn.bcd.app.data.process.parse.gb32960;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import cn.bcd.lib.storage.mongo.gb32960.RawData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

public class WorkHandler_gb32960 extends WorkHandler {

    static final Logger logger = LoggerFactory.getLogger(WorkHandler_gb32960.class);

    public final List<DataHandler_gb32960> handlers;

    public final Context_gb32960 context = new Context_gb32960();


    public WorkHandler_gb32960(String id, List<DataHandler_gb32960> handlers) {
        super(id);
        this.handlers = handlers;
    }

    @Override
    public void init(ConsumerRecord<String,byte[]> first) throws Exception {
        for (DataHandler_gb32960 handler : handlers) {
            try {
                handler.init(id, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        for (DataHandler_gb32960 handler : handlers) {
            try {
                handler.destroy(id, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    static EnumSet<PacketFlag> saveRawDataTypeSet = EnumSet.of(
            PacketFlag.vehicle_login_data,
            PacketFlag.vehicle_run_data,
            PacketFlag.vehicle_logout_data
    );

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> msg) throws Exception {
        byte[] value = msg.value();
        Date[] dates = DateUtil.getPrependDatesFromBytes(value, 2);
        byte[] message = new byte[value.length - 16];
        System.arraycopy(value, 16, message, 0, message.length);
        PacketFlag flag = PacketUtil.getPacketFlag(message);
        if (Const.logEnable) {
            logger.info("on kafka message vin[{}] type[{}] gwInTime[{}] gwOutTime[{}]:\n{}",
                    PacketUtil.getVin(message),
                    flag,
                    DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[0]),
                    DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[1]),
                    ByteBufUtil.hexDump(message));
        }
        context.gwInTime = dates[0];
        context.gwOutTime = dates[1];
        context.parseInTime = new Date();

        Packet packet;
        try {
            packet = Packet.read(Unpooled.wrappedBuffer(message));
            if (saveRawDataTypeSet.contains(flag)) {
                String hexDump = ByteBufUtil.hexDump(message);
                Date collectTime = PacketUtil.getTime(message);
                RawData rawData = new RawData();
                rawData.setVin(id);
                rawData.setCollectTime(collectTime);
                rawData.setType(flag.type);
                rawData.setGwReceiveTime(context.gwInTime);
                rawData.setGwSendTime(context.gwOutTime);
                rawData.setParseReceiveTime(context.parseInTime);
                rawData.setHex(hexDump);
                SaveUtil_gb32960.put(rawData);
            }
        } catch (Exception e) {
            logger.error("error", e);
            return;
        }

        for (DataHandler_gb32960 handler : handlers) {
            try {
                handler.handle(id, packet, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }
}
