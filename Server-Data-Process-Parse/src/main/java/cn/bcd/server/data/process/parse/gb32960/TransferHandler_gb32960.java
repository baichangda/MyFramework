package cn.bcd.server.data.process.parse.gb32960;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.data.init.transferAccess.TransferAccessDataInit;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class TransferHandler_gb32960 implements DataHandler_gb32960 {

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Override
    public void handle(String vin, Packet packet, Context_gb32960 context) throws Exception {
        switch (packet.flag) {
            case vehicle_run_data, vehicle_supplement_data, vehicle_login_data, vehicle_logout_data -> {
                ByteBuf byteBuf = packet.toByteBuf();
                byte[] bytes = ByteBufUtil.getBytes(byteBuf);
                byte[] res = DateUtil.prependDatesToBytes(bytes,
                        context.gwInTime,
                        context.gwOutTime,
                        context.parseInTime,
                        new Date()
                );
                List<String> platformCodes = TransferAccessDataInit.vin_platformCodes.get(vin);
                if (platformCodes == null) {
                    return;
                }
                for (String platformCode : platformCodes) {
                    kafkaTemplate.send("ts-" + platformCode, res);
                }
            }
            default -> {
            }
        }
    }
}
