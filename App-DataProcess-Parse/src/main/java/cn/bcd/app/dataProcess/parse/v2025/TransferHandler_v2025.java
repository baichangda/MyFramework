package cn.bcd.app.dataProcess.parse.v2025;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.data.init.transferAccess.TransferAccessDataInit;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class TransferHandler_v2025 implements DataHandler_v2025 {

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Override
    public void handle(String vin, Packet packet, Context_v2025 context) throws Exception {
        switch (packet.flag) {
            case vehicle_run_data, vehicle_supplement_data, vehicle_login_data, vehicle_logout_data -> {
                byte[] bytes = context.rawData;
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
