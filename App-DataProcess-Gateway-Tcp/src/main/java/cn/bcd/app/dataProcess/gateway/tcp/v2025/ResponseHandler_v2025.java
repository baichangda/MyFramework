package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1000)
@Component
public class ResponseHandler_v2025 implements DataHandler_v2025 {

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) throws Exception {
        byte[] response = PacketUtil.build_bytes_common_response(data, (byte) 1);
        context.ctx.write(response);
    }
}
