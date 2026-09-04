package cn.bcd.app.dataProcess.gateway.mqtt.v2016;

import cn.bcd.lib.base.executor.consume.ConsumeEntity;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class VehicleEntity_v2016 extends ConsumeEntity<byte[]> {

    static Logger logger = LoggerFactory.getLogger(VehicleEntity_v2016.class);

    private Context_v2016 context;

    private final List<DataHandler_v2016> dataHandlers;

    public VehicleEntity_v2016(String id, List<DataHandler_v2016> dataHandlers) {
        super(id);
        this.dataHandlers = dataHandlers;
    }

    @Override
    public void init(byte[] first) throws Exception {
        context = new Context_v2016();
        for (DataHandler_v2016 dataHandler : dataHandlers) {
            dataHandler.init(id, context);
        }
    }

    @Override
    public void destroy() throws Exception {
        for (DataHandler_v2016 dataHandler : dataHandlers) {
            dataHandler.destroy(id, context);
        }
    }

    @Override
    public void onMessage(byte[] bytes) throws Exception {
        context.receiveTime = new Date();
        PacketFlag flag = PacketFlag.fromInteger(bytes[2] & 0xFF);
        logger.info("receive msg vin[{}],receiverTime:{}, type[{}]:\n{}", id, context.receiveTime.getTime(), flag, ByteBufUtil.hexDump(bytes));
        for (DataHandler_v2016 dataHandler : dataHandlers) {
            dataHandler.handle(id, flag, bytes, context);
        }
    }
}
