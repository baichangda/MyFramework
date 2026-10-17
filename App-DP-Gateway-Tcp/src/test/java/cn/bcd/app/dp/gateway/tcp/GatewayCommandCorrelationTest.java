package cn.bcd.app.dp.gateway.tcp;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.spring.vehicle.command.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCommandCorrelationTest {
    @Test
    void oldVehicleResponseCannotRemoveTheNextSecondsRequest() {
        String vin = "L1234567890123456";
        int flag = PacketFlag.param_query.type;
        byte[] oldTime = {26, 10, 17, 12, 34, 56};
        byte[] newTime = {26, 10, 17, 12, 34, 57};
        Request<?, ?> current = new Request<>();
        current.id = Request.toId(vin, flag, newTime);
        current.timeout = 30;
        GatewayCommandReceiver.cache.put(current.id, current);
        try {
            byte[] oldPacket = cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil.build_bytes_packetData(
                    vin, PacketFlag.param_query, 1, oldTime);
            new GatewayCommandReceiver().onResponse(vin, flag, oldPacket);
            assertSame(current, GatewayCommandReceiver.cache.getIfPresent(current.id));

            byte[] oldPacket2025 = cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil.build_bytes_packetData(
                    vin, cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag.param_query, 1, oldTime);
            new GatewayCommandReceiver().onResponse(vin, flag, oldPacket2025);
            assertSame(current, GatewayCommandReceiver.cache.getIfPresent(current.id));
        } finally {
            GatewayCommandReceiver.cache.invalidate(current.id);
        }
    }
}
