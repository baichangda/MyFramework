package cn.bcd.server.data.process.transfer.handler;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.data.init.vehicle.VehicleDataInit;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class TransferDataHandler extends WorkHandler {

    static final Logger logger = LoggerFactory.getLogger(TransferDataHandler.class);

    public final List<KafkaDataHandler> kafkaDataHandlers;
    public final List<TcpDataHandler> tcpDataHandlers;

    public final Context context = new Context();

    public TransferDataHandler(String id, List<KafkaDataHandler> kafkaDataHandlers, List<TcpDataHandler> tcpDataHandlers) {
        super(id);
        this.kafkaDataHandlers = kafkaDataHandlers;
        this.tcpDataHandlers = tcpDataHandlers;
    }

    @Override
    public void init(ConsumerRecord<String,byte[]> first) {
        context.executor = executor;
        context.setVehicleData(VehicleDataInit.vin_vehicleData.get(id));
        for (KafkaDataHandler handler : kafkaDataHandlers) {
            try {
                handler.init(id, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        for (KafkaDataHandler handler : kafkaDataHandlers) {
            try {
                handler.destroy(id, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    public void onTcpMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        for (TcpDataHandler handler : tcpDataHandlers) {
            try {
                handler.handle(id, bytes, context);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        }
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> msg) {
        if (msg.value() != null) {
            byte[] value = msg.value();
            Date[] dates = DateUtil.getPrependDatesFromBytes(value, 4);
            byte[] message = new byte[value.length - 32];
            System.arraycopy(value, 32, message, 0, message.length);
            PacketFlag flag = PacketUtil.getPacketFlag(message);
            if (Const.logEnable) {
                logger.info("on kafka message vin[{}] type[{}] gwInTime[{}] gwOutTime[{}] parseInTime[{}] parseOutTime[{}]:\n{}",
                        PacketUtil.getVin(message),
                        flag,
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[0]),
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[1]),
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[2]),
                        DateZoneUtil.dateToStr_yyyyMMddHHmmss(dates[3]),
                        ByteBufUtil.hexDump(message));
            }
            context.gwInTime = dates[0];
            context.gwOutTime = dates[1];
            context.parseInTime = dates[2];
            context.parseOutTime = dates[3];
            context.transferInTime = new Date();
            for (KafkaDataHandler handler : kafkaDataHandlers) {
                try {
                    handler.handle(id, message, context);
                } catch (Exception ex) {
                    logger.error("error", ex);
                }
            }
        }
    }
}
