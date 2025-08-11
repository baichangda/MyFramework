package cn.bcd.app.dataProcess.gateway.tcp.v2016;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1000)
@Component
public class ResponseHandler implements DataHandler_v2016 {

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, VehicleCacheData_v2016 vehicleCacheData) throws Exception {
        byte[] response = PacketUtil.build_bytes_common_response(data, (byte) 1);
        vehicleCacheData.ctx.write(response);
    }
}
