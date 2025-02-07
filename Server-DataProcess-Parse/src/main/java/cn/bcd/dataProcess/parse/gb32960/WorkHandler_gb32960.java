package cn.bcd.dataProcess.parse.gb32960;

import cn.bcd.base.kafka.ext.datadriven.WorkHandler;
import cn.bcd.parser.protocol.gb32960.data.Packet;
import cn.bcd.parser.protocol.gb32960.data.VehicleLoginData;
import cn.bcd.parser.protocol.gb32960.data.VehicleLogoutData;
import cn.bcd.parser.protocol.gb32960.data.VehicleRunData;
import cn.bcd.storage.mongo.gb32960.SaveRawData;
import io.netty.buffer.Unpooled;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Date;

public class WorkHandler_gb32960 extends WorkHandler {

    public WorkHandler_gb32960(String id) {
        super(id);
    }

    @Override
    public void onMessage(ConsumerRecord<String, byte[]> msg) throws Exception {
        Packet packet = Packet.read(Unpooled.wrappedBuffer(msg.value()));
        Date collectTime = switch (packet.flag) {
            case vehicle_login_data -> ((VehicleLoginData) packet.data).collectTime;
            case vehicle_run_data, vehicle_supplement_data -> ((VehicleRunData) packet.data).collectTime;
            case vehicle_logout_data -> ((VehicleLogoutData) packet.data).collectTime;
            default -> null;
        };
        if (collectTime != null) {
            SaveHandler_gb32960.put(new SaveRawData(msg.key(), collectTime, packet.flag.type, msg.value()));
        }
    }
}
